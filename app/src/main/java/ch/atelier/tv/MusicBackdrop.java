package ch.atelier.tv;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.LruCache;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Public TheAudioDB images, resolved only while music focus is visible. Never controls playback. */
final class MusicBackdrop {
    static final class Result {
        final Bitmap image; final String credit, page;
        Result(Bitmap image, String credit, String page) { this.image = image; this.credit = credit; this.page = page; }
    }
    static final class Choice {
        final String id, credit, page; final List<String> images;
        Choice(String id, String credit, String page, List<String> images) { this.id = id; this.credit = credit; this.page = page; this.images = images; }
    }
    private static final class Cached {
        final Choice choice; final long expires;
        Cached(Choice choice, long lifetime) { this.choice = choice; expires = SystemClock.elapsedRealtime() + lifetime; }
    }
    private static final LinkedHashMap<String, Cached> records = new LinkedHashMap<String, Cached>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Cached> entry) { return size() > 16; }
    };
    private static final LruCache<String, Bitmap> images = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String address, Bitmap image) { return image.getAllocationByteCount(); }
        // Views can still own an evicted image; let GC release it after the transition.
    };
    private static long lastRequest, nextAllowed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Runnable changed;
    private Future<?> request;
    private MusicAccessService.Track track;
    private Result result;
    private String key = "";
    private int generation;
    private boolean active, closed, resolved;
    MusicBackdrop(Runnable changed) { this.changed = changed; }
    Result result() { return result; }
    static boolean wide(Bitmap image) { return image != null && !image.isRecycled() && image.getWidth() >= image.getHeight() * 1.35f; }
    void render(MusicAccessService.Track value) {
        track = value;
        String next = value.session + "\n" + Lyrics.key(value) + "\n" + wide(value.banner) + ":" + wide(value.cover);
        if (!next.equals(key)) {
            key = next; cancel(); resolved = false; result = null; changed.run();
        }
        load();
    }
    private boolean eligible() { return track != null && track.session && !track.title.isEmpty() && !track.artist.isEmpty() && !wide(track.banner) && !wide(track.cover); }
    private void load() {
        if (!active || closed || resolved || request != null || !eligible()) return;
        final int ticket = ++generation; final MusicAccessService.Track current = track;
        request = worker.submit(() -> {
            Result response = null;
            try { response = lookup(current); } catch (InterruptedException ignored) { return; } catch (Exception ignored) { }
            final Result received = response;
            handler.post(() -> {
                if (!closed && active && ticket == generation) { request = null; resolved = true; result = received; changed.run(); }
            });
        });
    }
    void setActive(boolean value) { if (closed) return; active = value; if (value) load(); else cancel(); }
    private void cancel() { generation++; if (request != null) request.cancel(true); request = null; }
    void close() { if (closed) return; closed = true; active = false; cancel(); handler.removeCallbacksAndMessages(null); worker.shutdownNow(); result = null; }

    private static String normalized(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
    private static String numeric(String value) { return value != null && value.matches("[0-9]{1,12}") ? value : ""; }
    private static List<String> urls(JSONObject item, String... fields) {
        List<String> out = new ArrayList<>();
        for (String field : fields) { String address = item.optString(field, ""); if (AlbumArt.audioDbUrl(address) && !out.contains(address)) out.add(address); }
        return out;
    }
    static Choice trackChoice(JSONArray entries, MusicAccessService.Track track) {
        if (entries == null || track.duration <= 0) return null;
        Choice selected = null;
        for (int i = 0; i < Math.min(50, entries.length()); i++) {
            JSONObject item = entries.optJSONObject(i); if (item == null || !normalized(track.artist).equals(normalized(item.optString("strArtist"))) ||
                !normalized(track.title).equals(normalized(item.optString("strTrack")))) continue;
            double duration = item.optDouble("intDuration", Double.NaN);
            String id = numeric(item.optString("idArtist"));
            if (id.isEmpty() || !Double.isFinite(duration) || Math.abs(duration - track.duration) > 5000) continue;
            if (selected != null && !selected.id.equals(id)) return null;
            String trackId = numeric(item.optString("idTrack"));
            Choice choice = new Choice(id, "Visuel du morceau · TheAudioDB", "https://www.theaudiodb.com/track/" + trackId,
                urls(item, "strMusicVidScreen1", "strMusicVidScreen2", "strMusicVidScreen3"));
            if (selected == null || selected.images.isEmpty()) selected = choice;
        }
        return selected;
    }
    static String albumArtist(JSONArray entries, MusicAccessService.Track track) {
        if (entries == null || track.album.isEmpty()) return ""; String selected = "";
        for (int i = 0; i < Math.min(100, entries.length()); i++) {
            JSONObject item = entries.optJSONObject(i); if (item == null || !normalized(track.artist).equals(normalized(item.optString("strArtist"))) ||
                !normalized(track.album).equals(normalized(item.optString("strAlbum")))) continue;
            String id = numeric(item.optString("idArtist")); if (id.isEmpty()) continue;
            if (!selected.isEmpty() && !selected.equals(id)) return ""; selected = id;
        }
        return selected;
    }
    static Choice artistChoice(JSONArray entries, MusicAccessService.Track track, String id) {
        if (entries == null || numeric(id).isEmpty()) return null;
        for (int i = 0; i < Math.min(10, entries.length()); i++) {
            JSONObject item = entries.optJSONObject(i);
            if (item != null && id.equals(item.optString("idArtist")) && normalized(track.artist).equals(normalized(item.optString("strArtist"))))
                return new Choice(id, "Fond d’artiste · TheAudioDB", "https://www.theaudiodb.com/artist/" + id,
                    urls(item, "strArtistFanart", "strArtistFanart2", "strArtistFanart3", "strArtistFanart4", "strArtistWideThumb"));
        }
        return null;
    }
    static boolean suitable(Bitmap image) { return image != null && !image.isRecycled() && image.getWidth() >= 960 && image.getHeight() >= 540 &&
        image.getWidth() / (float)image.getHeight() >= 1.5f && image.getWidth() / (float)image.getHeight() <= 2.2f; }
    private static Result image(Choice choice, long deadline) throws Exception {
        if (choice == null) return null;
        for (String address : choice.images) {
            Bitmap cached = images.get(address);
            if (cached != null && !cached.isRecycled()) return new Result(cached, choice.credit, choice.page);
        }
        int attempts = 0;
        for (String address : choice.images) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            if (SystemClock.elapsedRealtime() >= deadline || attempts++ >= 2) break;
            Bitmap bitmap = images.get(address);
            if (bitmap == null || bitmap.isRecycled()) {
                try { bitmap = AlbumArt.loadAudioDb(address); } catch (IOException ignored) { continue; }
                if (!suitable(bitmap)) { if (bitmap != null) bitmap.recycle(); continue; }
                images.put(address, bitmap);
            }
            return new Result(bitmap, choice.credit, choice.page);
        }
        return null;
    }
    static synchronized Result lookup(MusicAccessService.Track track) throws Exception {
        if (!track.session || track.artist.isEmpty() || track.title.isEmpty()) return null;
        String key = Lyrics.key(track); long now = SystemClock.elapsedRealtime(), deadline = now + 22000;
        Cached cached = records.get(key);
        if (cached != null && now < cached.expires) return image(cached.choice, deadline);
        Choice selected = null;
        try {
            JSONObject song = request("searchtrack.php", "s", track.artist, "t", track.title);
            Choice exact = trackChoice(song == null ? null : song.optJSONArray("track"), track);
            Result found = image(exact, deadline);
            if (found != null) { records.put(key, new Cached(exact, 24 * 3600000L)); return found; }
            String id = exact == null ? "" : exact.id;
            if (id.isEmpty() && !track.album.isEmpty() && SystemClock.elapsedRealtime() < deadline) {
                JSONObject album = request("searchalbum.php", "s", track.artist, "a", track.album);
                id = albumArtist(album == null ? null : album.optJSONArray("album"), track);
            }
            if (!id.isEmpty() && SystemClock.elapsedRealtime() < deadline) {
                JSONObject artist = request("artist.php", "i", id);
                selected = artistChoice(artist == null ? null : artist.optJSONArray("artists"), track, id);
                found = image(selected, deadline);
                if (found != null) { records.put(key, new Cached(selected, 24 * 3600000L)); return found; }
            }
            records.put(key, new Cached(null, 3600000)); return null;
        } catch (InterruptedException interrupted) { throw interrupted; }
        catch (Exception error) { records.put(key, new Cached(null, 60000)); return null; }
    }
    private static JSONObject request(String endpoint, String... values) throws Exception {
        long now = SystemClock.elapsedRealtime(); if (now < nextAllowed) throw new IOException("Provider temporarily unavailable");
        long delay = 2100 - (now - lastRequest); if (delay > 0) Thread.sleep(delay);
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        Uri.Builder uri = Uri.parse("https://www.theaudiodb.com/api/v1/json/123/" + endpoint).buildUpon();
        for (int i = 0; i < values.length; i += 2) uri.appendQueryParameter(values[i], values[i + 1]);
        HttpURLConnection connection = (HttpURLConnection)new URL(uri.build().toString()).openConnection();
        connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(6000); connection.setReadTimeout(8000);
        connection.setRequestProperty("User-Agent", "AtelierTV/0.13.0 (private Android TV; https://killermy.ch)");
        connection.setRequestProperty("Accept", "application/json"); lastRequest = SystemClock.elapsedRealtime();
        try {
            int code = connection.getResponseCode();
            if (code == 429 || code == 503) {
                long seconds = 60; try { seconds = Math.max(1, Math.min(3600, Long.parseLong(connection.getHeaderField("Retry-After")))); } catch (Exception ignored) { }
                nextAllowed = SystemClock.elapsedRealtime() + seconds * 1000; throw new IOException("Provider temporarily unavailable");
            }
            if (code == 404) return null;
            String type = connection.getContentType();
            if (code != 200 || type == null || !type.toLowerCase(Locale.ROOT).contains("application/json") || connection.getContentLengthLong() > 768 * 1024) throw new IOException("Invalid artwork response");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] bytes = new byte[8192]; int count;
                while ((count = input.read(bytes)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    if (out.size() + count > 768 * 1024) throw new IOException("Artwork response too large"); out.write(bytes, 0, count);
                }
                return new JSONObject(new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
            }
        } finally { connection.disconnect(); }
    }
}
