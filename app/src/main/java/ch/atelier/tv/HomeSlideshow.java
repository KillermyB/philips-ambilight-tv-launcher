package ch.atelier.tv;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A single pending timer and decode, active only while the home is visible. */
final class HomeSlideshow {
    interface Listener { void onFrame(ArtStore.Art work, Bitmap bitmap, boolean advanceQuote, boolean animate); }
    private final ArtStore store;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<String> remaining = new ArrayList<>();
    private String currentId = "", signature = "";
    private boolean visible, closed, loading;
    private int generation;
    private final Runnable advance = () -> next(true);

    HomeSlideshow(ArtStore store, Listener listener) { this.store = store; this.listener = listener; }

    void setVisible(boolean value) {
        if (closed || visible == value) return;
        visible = value; handler.removeCallbacks(advance);
        if (!visible) {
            // A menu may open before the first decode completes. Keep that initial
            // frame; pause later changes without leaving the first screen blank.
            if (!currentId.isEmpty()) { generation++; loading = false; }
            return;
        }
        ArtStore.Art selected = selectedWork();
        if (selected != null && (!currentId.equals(selected.id) ||
            !currentId.equals(store.prefs.getString("art", "sierra")))) showSelected();
        else schedule();
    }

    void preferencesChanged() { signature = ""; schedule(); }

    void showSelected() {
        if (!visible || closed) return;
        ArtStore.Art selected = selectedWork();
        if (selected != null) display(selected, false);
    }

    private ArtStore.Art selectedWork() {
        ArtStore.Art selected = store.selected();
        if (store.prefs.getBoolean("home_personal", false) && (selected == null || !selected.personal()))
            for (ArtStore.Art work : store.list()) if (work.personal()) return work;
        return selected;
    }

    void next(boolean automatic) {
        if (!visible || closed || loading || (automatic && !store.prefs.getBoolean("home_slideshow", true))) return;
        List<ArtStore.Art> pool = new ArrayList<>();
        for (ArtStore.Art work : store.list())
            if (!store.prefs.getBoolean("home_personal", false) || work.personal()) pool.add(work);
        if (pool.size() < 2) { schedule(); return; }
        ArtStore.Art next = null;
        if (store.prefs.getBoolean("home_shuffle", true)) {
            StringBuilder key = new StringBuilder(); for (ArtStore.Art work : pool) key.append(work.id).append(';');
            if (!signature.equals(key.toString())) { remaining.clear(); signature = key.toString(); }
            remaining.remove(currentId);
            if (remaining.isEmpty()) {
                for (ArtStore.Art work : pool) if (!work.id.equals(currentId)) remaining.add(work.id);
                Collections.shuffle(remaining);
            }
            String id = remaining.remove(0);
            for (ArtStore.Art work : pool) if (work.id.equals(id)) { next = work; break; }
        } else {
            int index = -1;
            for (int i = 0; i < pool.size(); i++) if (pool.get(i).id.equals(currentId)) index = i;
            next = pool.get((index + 1) % pool.size());
        }
        if (next != null) display(next, automatic); else schedule();
    }

    private void display(ArtStore.Art work, boolean animate) {
        final int request = ++generation; loading = true; handler.removeCallbacks(advance);
        worker.execute(() -> {
            try {
                Bitmap bitmap = store.decodeCover(work, 2560);
                handler.post(() -> {
                    if (closed || request != generation || !visible && !currentId.isEmpty()) { bitmap.recycle(); return; }
                    boolean changed = !currentId.isEmpty() && !currentId.equals(work.id);
                    currentId = work.id; loading = false;
                    // Commit picture, caption and quotation in one main-thread frame.
                    listener.onFrame(work, bitmap, changed, animate && changed);
                    android.util.Log.i("AtelierSlideshow", "Frame " + work.id + " quoteAdvance=" + changed);
                    schedule();
                });
            } catch (Exception error) {
                android.util.Log.w("AtelierSlideshow", "Artwork skipped: " + work.id, error);
                handler.post(() -> { if (!closed && request == generation) { loading = false; schedule(); } });
            }
        });
    }

    private void schedule() {
        handler.removeCallbacks(advance);
        if (!closed && visible && !loading && store.prefs.getBoolean("home_slideshow", true)) {
            int seconds = Math.max(30, Math.min(600, store.prefs.getInt("home_seconds", 60)));
            handler.postDelayed(advance, seconds * 1000L);
        }
    }

    void close() { closed = true; generation++; handler.removeCallbacksAndMessages(null); worker.shutdownNow(); }
}
