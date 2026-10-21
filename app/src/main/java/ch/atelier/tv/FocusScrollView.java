package ch.atelier.tv;

import android.graphics.Rect;
import android.graphics.Canvas;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ScrollView;
import java.util.IdentityHashMap;
import java.util.Map;

/** Clip at the viewport, while keeping the complete focus animation inside it. */
final class FocusScrollView extends ScrollView {
    private final int gutter;
    private final Rect focusBounds = new Rect();
    private final Map<View, View> groups = new IdentityHashMap<>();
    private final Runnable reveal = this::revealFocus;

    FocusScrollView(Ui ui) {
        super(ui.context); gutter = ui.px(8);
        setClipChildren(true); setClipToPadding(true);
        setVerticalScrollBarEnabled(false); setOverScrollMode(OVER_SCROLL_NEVER);
        setSmoothScrollingEnabled(false); setFocusable(false); setFadingEdgeLength(0);
    }
    void keepTogether(View target, View captionedCard) { groups.put(target, captionedCard); }

    @Override protected void dispatchDraw(Canvas canvas) {
        // With zero outer padding Android may skip the viewport clip. Keep
        // scrolled rows out of the heading/footer, including their shadows.
        int saved = canvas.save();
        canvas.clipRect(getScrollX(), getScrollY(), getScrollX() + getWidth(), getScrollY() + getHeight());
        super.dispatchDraw(canvas); canvas.restoreToCount(saved);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        View focused = findFocus(); int id = View.NO_ID, direction = 0;
        if (focused != null && event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP: id = focused.getNextFocusUpId(); direction = View.FOCUS_UP; break;
                case KeyEvent.KEYCODE_DPAD_DOWN: id = focused.getNextFocusDownId(); direction = View.FOCUS_DOWN; break;
                case KeyEvent.KEYCODE_DPAD_LEFT: id = focused.getNextFocusLeftId(); direction = View.FOCUS_LEFT; break;
                case KeyEvent.KEYCODE_DPAD_RIGHT: id = focused.getNextFocusRightId(); direction = View.FOCUS_RIGHT; break;
            }
            View next = id == View.NO_ID ? null : getRootView().findViewById(id);
            // Follow the actual selection immediately. Native arrowScroll can
            // otherwise consume a press just to move the last few pixels.
            if (next == focused) return true;
            if (next != null && next.isShown() && next.requestFocus(direction)) return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        // Android first reveals the unscaled view. Reserve the zoom/lift too,
        // after its own direction-key scrolling has finished.
        removeCallbacks(reveal); post(reveal);
    }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom); revealFocus();
    }
    private void revealFocus() {
        View focused = findFocus(); if (focused == null || focused == this || getChildCount() == 0) return;
        View target = groups.containsKey(focused) ? groups.get(focused) : focused;
        target.getDrawingRect(focusBounds); offsetDescendantRectToMyCoords(target, focusBounds);
        focusBounds.inset(-gutter, -gutter);
        int delta = computeScrollDeltaToGetChildRectOnScreen(focusBounds);
        if (delta != 0) scrollBy(0, delta);
    }
    @Override protected void onDetachedFromWindow() {
        removeCallbacks(reveal); super.onDetachedFromWindow();
    }
}
