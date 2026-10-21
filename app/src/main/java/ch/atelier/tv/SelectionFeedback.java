package ch.atelier.tv;

import android.animation.ValueAnimator;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

/** Logical focus and clicks are immediate; only the visual response interpolates. */
final class SelectionFeedback {
    interface Appearance { void update(boolean active, boolean focused); }
    private final View view;
    private final float scale, lift, depth;
    private final Appearance appearance;
    private final boolean spring;
    private boolean focused, hovered, pressed;

    SelectionFeedback(View view, float scale, float lift, float depth, Appearance appearance) { this(view, scale, lift, depth, appearance, false); }
    SelectionFeedback(View view, float scale, float lift, float depth, Appearance appearance, boolean spring) {
        this.view = view; this.scale = scale; this.lift = lift; this.depth = depth; this.appearance = appearance; this.spring = spring;
        view.setOnFocusChangeListener((item, value) -> {
            focused = value; if (!value) { pressed = false; hovered = false; } update(false);
        });
        view.setOnHoverListener((item, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER) {
                hovered = true; view.requestFocus(); update(false);
            } else if (event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT) {
                hovered = false; update(false);
            }
            return false;
        });
        view.setOnKeyListener((item, code, event) -> {
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER)
                press(event.getAction() == KeyEvent.ACTION_DOWN);
            return false;
        });
        view.setOnTouchListener((item, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) press(true);
            else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) press(false);
            return false;
        });
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View item) { }
            @Override public void onViewDetachedFromWindow(View item) { item.animate().cancel(); }
        });
    }

    private void press(boolean value) { if (pressed != value) { pressed = value; update(true); } }

    private void update(boolean pressFeedback) {
        boolean active = focused || hovered;
        appearance.update(active, focused);
        float targetScale = (active ? scale : 1f) * (pressed ? .975f : 1f);
        float targetLift = active && !pressed ? -lift : 0f;
        float targetDepth = active && !pressed ? depth : 0f;
        // Cancel from the current visual values: rapid changes never queue up.
        view.animate().cancel();
        if (!view.isAttachedToWindow() || !ValueAnimator.areAnimatorsEnabled()) {
            view.setScaleX(targetScale); view.setScaleY(targetScale);
            view.setTranslationY(targetLift); view.setTranslationZ(targetDepth);
        } else {
            view.animate().scaleX(targetScale).scaleY(targetScale).translationY(targetLift).translationZ(targetDepth)
                .setDuration(pressFeedback ? 80 : spring ? active ? 190 : 130 : active ? 140 : 110)
                .setInterpolator(spring && active && !pressFeedback ? new PathInterpolator(.18f, 1.4f, .3f, 1f) : new PathInterpolator(.16f, 1f, .3f, 1f)).start();
        }
    }
}
