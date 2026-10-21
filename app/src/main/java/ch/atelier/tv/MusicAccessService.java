package ch.atelier.tv;

import android.content.ComponentName;
import android.content.Context;
import android.app.NotificationManager;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Notification access is the Android entitlement; only Spotify media sessions are read. */
public final class MusicAccessService extends NotificationListenerService {
    static final String SPOTIFY = "com.spotify.tv.android";
    interface Listener { void update(Track track); }
    static final class Track {
        final boolean access, session, playing;
        final String title, artist, album;
        final Bitmap cover, banner;
        final long duration, position, updated, actions;
        final float speed;
        Track(boolean access, MediaController controller, Bitmap cover) { this(access, controller, cover, null); }
        Track(boolean access, MediaController controller, Bitmap cover, Bitmap banner) {
            this(access, controller != null, controller == null ? null : controller.getMetadata(), controller == null ? null : controller.getPlaybackState(), cover, banner);
        }
        Track(boolean access, boolean session, MediaMetadata metadata, PlaybackState state, Bitmap cover) { this(access, session, metadata, state, cover, null); }
        Track(boolean access, boolean session, MediaMetadata metadata, PlaybackState state, Bitmap cover, Bitmap banner) {
            this.access = access; this.session = session; this.cover = cover; this.banner = banner;
            String name = metadata == null ? "" : metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            if ((name == null || name.isEmpty()) && metadata != null) name = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            title = name == null ? "" : ArtStore.clean(name, "", 180);
            String performer = metadata == null ? "" : metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if ((performer == null || performer.isEmpty()) && metadata != null) performer = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            if ((performer == null || performer.isEmpty()) && metadata != null) performer = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
            artist = performer == null ? "" : ArtStore.clean(performer, "", 160);
            String record = metadata == null ? "" : metadata.getString(MediaMetadata.METADATA_KEY_ALBUM);
            album = record == null ? "" : ArtStore.clean(record, "", 180);
            duration = metadata == null ? 0 : Math.max(0, metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));
            position = state == null ? 0 : Math.max(0, state.getPosition()); updated = state == null ? 0 : state.getLastPositionUpdateTime();
            actions = state == null ? 0 : state.getActions(); speed = state == null ? 0 : state.getPlaybackSpeed();
            playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
        }
        float progress() {
            if (duration <= 0) return 0;
            return positionMs() / (float) duration;
        }
        long positionMs() {
            long elapsed = playing && updated > 0 ? Math.max(0, SystemClock.elapsedRealtime() - updated) : 0;
            long value = Math.max(0, position + (long) (elapsed * speed));
            return duration > 0 ? Math.min(duration, value) : value;
        }
        boolean canCommand(int command) {
            if (!access || !session || title.isEmpty()) return false;
            long accepted = command < 0 ? PlaybackState.ACTION_SKIP_TO_PREVIOUS : command > 0 ? PlaybackState.ACTION_SKIP_TO_NEXT :
                (playing ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY) | PlaybackState.ACTION_PLAY_PAUSE;
            return (actions & accepted) != 0;
        }
    }
    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private static Track latest = new Track(false, null, null);
    private static MusicAccessService connected;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaSessionManager manager;
    private MediaController spotify;
    private Bitmap cover, banner;
    private String artworkKey = "";
    private final ExecutorService artworkWorker = Executors.newSingleThreadExecutor();
    private Future<?> artworkRequest;
    private int artworkGeneration;
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::choose;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { readCover(metadata); publish(); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { publish(); }
        @Override public void onSessionDestroyed() { clearController(); refresh(); }
    };

    static boolean allowed(Context context) {
        ComponentName component = new ComponentName(context, MusicAccessService.class);
        if (Build.VERSION.SDK_INT >= 27) return context.getSystemService(NotificationManager.class).isNotificationListenerAccessGranted(component);
        String enabled = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        return enabled != null && java.util.Arrays.asList(enabled.split(":")).contains(component.flattenToString());
    }
    static void observe(Context context, Listener listener) {
        listeners.addIfAbsent(listener);
        if (connected != null) connected.refresh();
        else {
            latest = new Track(allowed(context), null, null);
            if (latest.access) requestRebind(new ComponentName(context, MusicAccessService.class));
        }
        listener.update(latest);
    }
    static void forget(Listener listener) { listeners.remove(listener); }
    static void command(int command) {
        MusicAccessService service = connected; if (service == null || service.spotify == null || !latest.canCommand(command)) return;
        try {
            MediaController.TransportControls controls = service.spotify.getTransportControls();
            if (command < 0) controls.skipToPrevious();
            else if (command > 0) controls.skipToNext();
            else if (latest.playing) controls.pause(); else controls.play();
        } catch (RuntimeException ignored) { service.refresh(); }
    }
    @Override public void onListenerConnected() {
        connected = this; manager = getSystemService(MediaSessionManager.class);
        try { manager.addOnActiveSessionsChangedListener(sessions, new ComponentName(this, MusicAccessService.class), handler); refresh(); }
        catch (SecurityException error) { clearController(); publish(); }
    }
    private void refresh() {
        if (manager == null) return;
        try { choose(manager.getActiveSessions(new ComponentName(this, MusicAccessService.class))); }
        catch (SecurityException error) { clearController(); publish(); }
    }
    private void choose(List<MediaController> controllers) {
        MediaController selected = null;
        if (controllers != null) for (MediaController controller : controllers) {
            if (!SPOTIFY.equals(controller.getPackageName())) continue;
            selected = controller;
            PlaybackState state = controller.getPlaybackState();
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) break;
        }
        if (spotify != null && selected != null && spotify.getSessionToken().equals(selected.getSessionToken())) { publish(); return; }
        clearController(); spotify = selected;
        if (spotify != null) {
            spotify.registerCallback(callback, handler); readCover(spotify.getMetadata());
        }
        publish();
    }
    private static boolean wide(Bitmap bitmap) { return bitmap != null && !bitmap.isRecycled() && bitmap.getWidth() > bitmap.getHeight() * 1.35f; }
    private static String imageUri(MediaMetadata metadata, String field) { String uri = metadata.getString(field); return uri == null ? "" : uri; }
    private void readCover(MediaMetadata metadata) {
        if (metadata == null) { artworkKey = ""; artworkGeneration++; if (artworkRequest != null) artworkRequest.cancel(true); cover = null; banner = null; return; }
        String albumUri = imageUri(metadata, MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
        String artUri = imageUri(metadata, MediaMetadata.METADATA_KEY_ART_URI);
        String iconUri = imageUri(metadata, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
        Bitmap album = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        Bitmap art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        Bitmap icon = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        String key = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) + "\n" + metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) + "\n" +
            metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) + "\n" + albumUri + "\n" + artUri + "\n" + iconUri + "\n" +
            (album != null) + (art != null) + (icon != null);
        if (key.equals(artworkKey)) return; artworkKey = key;
        final int ticket = ++artworkGeneration; if (artworkRequest != null) artworkRequest.cancel(true);
        cover = album != null && !album.isRecycled() ? album : art != null && !art.isRecycled() ? art : icon != null && !icon.isRecycled() ? icon : null;
        banner = wide(art) ? art : wide(icon) ? icon : wide(album) ? album : null;
        // Original public metadata URLs only. A separate ART_URI can carry a genuine wide image.
        java.util.LinkedHashSet<String> addresses = new java.util.LinkedHashSet<>();
        if (!albumUri.isEmpty()) addresses.add(albumUri);
        if (!artUri.isEmpty()) addresses.add(artUri);
        if (!iconUri.isEmpty()) addresses.add(iconUri);
        if (addresses.isEmpty()) return;
        artworkRequest = artworkWorker.submit(() -> {
            for (String address : addresses) {
                if (Thread.currentThread().isInterrupted()) return;
                Bitmap value = null; try { value = AlbumArt.load(address, address.equals(artUri) ? 1600 : 1000); } catch (Exception ignored) { }
                final Bitmap received = value; final boolean isAlbum = address.equals(albumUri);
                handler.post(() -> {
                    if (ticket != artworkGeneration || spotify == null || connected != this) { if (received != null) received.recycle(); return; }
                    if (received == null) return;
                    boolean used = false;
                    if (cover == null || isAlbum && received.getWidth() >= cover.getWidth() && received.getHeight() >= cover.getHeight()) { cover = received; used = true; }
                    if (wide(received) && (banner == null || received.getWidth() > banner.getWidth())) { banner = received; used = true; }
                    if (used) publish(); else received.recycle();
                });
            }
        });
    }
    private void clearController() {
        artworkGeneration++; if (artworkRequest != null) { artworkRequest.cancel(true); artworkRequest = null; }
        if (spotify != null) spotify.unregisterCallback(callback); spotify = null; cover = null; banner = null; artworkKey = "";
    }
    private void publish() {
        latest = new Track(allowed(this), spotify, cover, banner);
        for (Listener listener : listeners) listener.update(latest);
    }
    private void disconnect() {
        if (manager != null) manager.removeOnActiveSessionsChangedListener(sessions); manager = null;
        clearController(); if (connected == this) connected = null;
        latest = new Track(allowed(this), null, null); for (Listener listener : listeners) listener.update(latest);
    }
    @Override public void onListenerDisconnected() { disconnect(); }
    @Override public void onDestroy() { disconnect(); artworkWorker.shutdownNow(); super.onDestroy(); }
}
