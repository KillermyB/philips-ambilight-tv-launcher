package ch.atelier.tv;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.widget.TextClock;

/** Native time/time-zone updates, with a brief transition for changed digits only. */
final class MinuteClock extends TextClock {
    private final Ui ui;
    private final Rect ink = new Rect();
    private String shown, previous;
    private ValueAnimator transition;
    private float progress = 1;

    MinuteClock(Ui ui) {
        super(ui.context); this.ui = ui;
        setFontFeatureSettings("'tnum' 1");
        setPadding(0, 0, ui.px(1), 0);
        shown = getText().toString();
    }

    @Override protected void onTextChanged(CharSequence text, int start, int before, int count) {
        super.onTextChanged(text, start, before, count);
        String value = text == null ? "" : text.toString(), old = shown;
        shown = value; finishTransition();
        if (old == null || old.equals(value) || !time(old) || !time(value) || !isAttachedToWindow() || !isShown() || !ValueAnimator.areAnimatorsEnabled()) return;
        previous = old; progress = 0;
        transition = ValueAnimator.ofFloat(0, 1); transition.setDuration(260);
        transition.setInterpolator(new android.view.animation.PathInterpolator(.16f, 1f, .3f, 1f));
        transition.addUpdateListener(frame -> {
            progress = (float) frame.getAnimatedValue();
            if (progress >= 1) previous = null;
            invalidate();
        });
        transition.start();
    }

    private static boolean time(String value) {
        return value.length() == 5 && value.charAt(2) == ':' && Character.isDigit(value.charAt(0)) && Character.isDigit(value.charAt(1)) && Character.isDigit(value.charAt(3)) && Character.isDigit(value.charAt(4));
    }

    @Override protected void onDraw(Canvas canvas) {
        String value = getText().toString();
        if (!time(value) || ui == null) { super.onDraw(canvas); return; }
        Paint paint = getPaint(); float spacing = paint.getLetterSpacing(); Paint.Align align = paint.getTextAlign();
        paint.setTextAlign(Paint.Align.LEFT); paint.setLetterSpacing(0); paint.setColor(getCurrentTextColor());
        int alpha = paint.getAlpha(); float digit = paint.measureText("0"), colon = paint.measureText(":"), tracking = spacing * getTextSize();
        float width = digit * 4 + colon + tracking * 4;
        float x = getWidth() - getPaddingRight() - width, baseline = getBaseline();
        for (int i = 0; i < value.length(); i++) {
            float advance = i == 2 ? colon : digit;
            if (previous != null && previous.charAt(i) != value.charAt(i)) {
                paint.setAlpha(Math.round(alpha * (1 - progress)));
                glyph(canvas, paint, previous, i, x, baseline - ui.px(4) * progress, advance);
                paint.setAlpha(Math.round(alpha * progress));
                glyph(canvas, paint, value, i, x, baseline + ui.px(4) * (1 - progress), advance);
            } else {
                paint.setAlpha(alpha); glyph(canvas, paint, value, i, x, baseline, advance);
            }
            x += advance + tracking;
        }
        paint.setAlpha(alpha); paint.setLetterSpacing(spacing); paint.setTextAlign(align);
    }

    private void glyph(Canvas canvas, Paint paint, String text, int index, float x, float baseline, float advance) {
        // The last digit's visible edge meets the date's visible edge, regardless of its side bearing.
        if (index == 4) { paint.getTextBounds(text, index, index + 1, ink); x += advance - ink.right; }
        canvas.drawText(text, index, index + 1, x, baseline, paint);
    }

    private void finishTransition() {
        if (transition != null) { transition.cancel(); transition = null; }
        previous = null; progress = 1; invalidate();
    }
    void stop() { finishTransition(); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); finishTransition(); }
    @Override protected void onVisibilityChanged(View changed, int visibility) { super.onVisibilityChanged(changed, visibility); if (!isShown()) finishTransition(); }
    @Override protected void onDetachedFromWindow() { finishTransition(); super.onDetachedFromWindow(); }
}
