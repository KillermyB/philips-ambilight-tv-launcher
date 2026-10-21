package ch.atelier.tv;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Read-only lyrics inside music focus. Hidden screens stop requests and position updates. */
final class InlineLyrics extends FrameLayout {
    interface Listener { void changed(); }
    private final Ui ui;
    final ScrollView scroll;
    private final LinearLayout body;
    private final List<TextView> labels = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Listener listener;
    private Future<?> request;
    private MusicAccessService.Track track;
    private Lyrics.Result result;
    private String key = "";
    private int generation, selected = -2;
    private boolean active, closed, following = true, loading;
    private final Runnable tick = new Runnable() {
        @Override public void run() { highlight(); if (active && hasLyrics() && result.synced && track != null && track.playing) handler.postDelayed(this, 400); }
    };
    InlineLyrics(Ui ui, Listener listener) {
        super(ui.context); this.ui = ui; this.listener = listener;
        scroll = new ScrollView(ui.context) {
            @Override protected void dispatchDraw(Canvas canvas) {
                int save = canvas.save(); canvas.clipRect(getScrollX(), getScrollY(), getScrollX() + getWidth(), getScrollY() + getHeight());
                super.dispatchDraw(canvas); canvas.restoreToCount(save);
            }
        };
        scroll.setId(View.generateViewId()); scroll.setFocusable(true); scroll.setDefaultFocusHighlightEnabled(false);
        scroll.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS); scroll.setSmoothScrollingEnabled(false);
        scroll.setContentDescription("Paroles. Haut et Bas pour lire ; Gauche ou Droite pour retrouver les commandes.");
        scroll.setVerticalScrollBarEnabled(false); scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.setVerticalFadingEdgeEnabled(true); scroll.setFadingEdgeLength(ui.px(18));
        scroll.setPadding(ui.px(10), ui.px(8), ui.px(10), ui.px(8));
        scroll.setBackground(ui.round(0x08000000, 13, 0, 0)); scroll.setClipToOutline(true);
        scroll.setOnFocusChangeListener((v, focus) -> scroll.setBackground(ui.round(0x08000000, 13, focus ? 0x70FFFFFF : 0, .5f)));
        body = new LinearLayout(ui.context); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, ui.px(76), 0, ui.px(100)); scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        addView(scroll, new LayoutParams(-1, -1)); setVisibility(GONE);
    }
    void render(MusicAccessService.Track value) {
        track = value; String next = value.session + "\n" + Lyrics.key(value);
        if (!next.equals(key)) {
            key = next; cancelRequest(); result = null; selected = -2; following = true;
            body.removeAllViews(); labels.clear(); scroll.scrollTo(0, 0); listener.changed();
            if (active) load(false);
        } else restartTick();
    }
    boolean hasLyrics() { return result != null && !result.lines.isEmpty(); }
    boolean synced() { return hasLyrics() && result.synced; }
    boolean following() { return following; }
    boolean loading() { return loading; }
    String status() { return result == null ? loading ? "Recherche des paroles…" : "" : result.status; }
    void retry() { load(true); }
    void follow() { following = true; selected = -2; highlight(); listener.changed(); scroll.requestFocus(); }
    private void load(boolean retry) {
        if (closed || !active || track == null || !track.session || track.title.isEmpty() || track.artist.isEmpty()) return;
        cancelRequest(); final int ticket = generation; loading = true; listener.changed();
        MusicAccessService.Track current = track;
        request = worker.submit(() -> {
            Lyrics.Result response;
            try { response = Lyrics.load(current, retry); }
            catch (InterruptedException ignored) { return; }
            catch (Exception error) { response = new Lyrics.Result(java.util.Collections.emptyList(), false, "Paroles indisponibles · réessayer"); }
            final Lyrics.Result received = response;
            handler.post(() -> { if (!closed && active && ticket == generation) display(received); });
        });
    }
    private void display(Lyrics.Result value) {
        request = null; loading = false; result = value; selected = -2; body.removeAllViews(); labels.clear();
        for (Lyrics.Line line : value.lines) {
            TextView text = ui.text(line.text.isEmpty() ? "♪" : line.text, 23, value.synced ? 0x8AFFFFFF : Ui.WHITE, false);
            text.setSingleLine(false); text.setEllipsize(null); text.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            text.setIncludeFontPadding(true); text.setLineSpacing(0, 1.12f); text.setPadding(ui.px(5), ui.px(7), ui.px(5), ui.px(7));
            body.addView(text, new LinearLayout.LayoutParams(-1, -2)); labels.add(text);
        }
        scroll.scrollTo(0, 0); listener.changed();
        body.post(() -> { if (!closed && active && result == value) { highlight(); restartTick(); } });
    }
    private void highlight() {
        if (closed || !active || !synced() || track == null) return;
        int index = Lyrics.indexAt(result.lines, track.positionMs()); if (index == selected) return; selected = index;
        for (int i = 0; i < labels.size(); i++) labels.get(i).setTextColor(i == index ? Ui.WHITE : 0x8AFFFFFF);
        if (following && index >= 0 && index < labels.size()) {
            View line = labels.get(index); int y = Math.max(0, line.getTop() - scroll.getHeight() / 2 + line.getHeight() / 2);
            if (ValueAnimator.areAnimatorsEnabled()) scroll.smoothScrollTo(0, y); else scroll.scrollTo(0, y);
        }
    }
    private void restartTick() {
        handler.removeCallbacks(tick);
        if (active && synced()) { highlight(); if (track != null && track.playing) handler.postDelayed(tick, 400); }
    }
    void setActive(boolean value) {
        if (closed) return;
        boolean changed = active != value; active = value;
        if (!value) cancelRequest();
        else if (changed && result == null && !loading) load(false);
        restartTick();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (!scroll.hasFocus()) return super.dispatchKeyEvent(event);
        int code = event.getKeyCode();
        boolean horizontal = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean vertical = code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
        if (!horizontal && !vertical) return super.dispatchKeyEvent(event);
        // ScrollView consumes directional keys internally. Honour escape routes before
        // delegating, and never swallow an arrow when its target cannot receive focus.
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (horizontal) return moveFocus(code == KeyEvent.KEYCODE_DPAD_LEFT ? scroll.getNextFocusLeftId() : scroll.getNextFocusRightId())
            || super.dispatchKeyEvent(event);
        int direction = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
        if (!scroll.canScrollVertically(direction))
            return moveFocus(direction < 0 ? scroll.getNextFocusUpId() : scroll.getNextFocusDownId()) || super.dispatchKeyEvent(event);
        following = false; listener.changed();
        int distance = Math.max(ui.px(48), scroll.getHeight() / 3);
        scroll.scrollBy(0, direction * distance); return true;
    }
    private boolean moveFocus(int id) {
        if (id == View.NO_ID || id == scroll.getId()) return false;
        View target = getRootView().findViewById(id);
        return target != null && target.isShown() && target.isEnabled() && target.isFocusable() && target.requestFocus();
    }
    private void cancelRequest() { generation++; if (request != null) request.cancel(true); request = null; loading = false; }
    void close() { if (closed) return; closed = true; active = false; cancelRequest(); handler.removeCallbacksAndMessages(null); worker.shutdownNow(); }
    @Override protected void onDetachedFromWindow() { close(); super.onDetachedFromWindow(); }
}
