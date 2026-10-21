package ch.atelier.tv;

import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/** An immediate, contrast-aware inner rim. No moving reflection or idle animation. */
final class FocusFrame extends Drawable {
    private final float radius, stroke, keyline;
    private final int idleColor;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF card = new RectF(), rim = new RectF();
    private boolean active;
    private Integer innerColor;

    FocusFrame(View host, Ui ui, int idleColor) {
        this(host, ui, idleColor, 10);
    }
    FocusFrame(View host, Ui ui, int idleColor, float cornerRadius) {
        this.idleColor = idleColor; radius = ui.px(cornerRadius);
        stroke = ui.px(2); keyline = ui.px(4);

    }

    void setActive(boolean value) {
        if (active == value) return;
        active = value; invalidateSelf();
    }

    void innerOnly(Drawable artwork) {
        Bitmap sample = Bitmap.createBitmap(24, 14, Bitmap.Config.ARGB_8888);
        Rect previous = new Rect(artwork.getBounds()); long brightness = 0; int count = 0;
        try {
            artwork.setBounds(0, 0, 24, 14); artwork.draw(new Canvas(sample));
            for (int y = 0; y < 14; y++) for (int x = 0; x < 24; x++) {
                if (x > 2 && x < 21 && y > 2 && y < 11) continue;
                int color = sample.getPixel(x, y);
                brightness += ((color >> 16 & 255) * 299 + (color >> 8 & 255) * 587 + (color & 255) * 114) / 1000;
                count++;
            }
            innerColor = count > 0 && brightness / count > 160 ? 0xE612191F : Ui.WHITE;
        } finally { artwork.setBounds(previous); sample.recycle(); }
        invalidateSelf();
    }

    @Override protected void onBoundsChange(Rect bounds) {
        card.set(bounds); rim.set(card); rim.inset(keyline / 2, keyline / 2);
    }

    @Override public void draw(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL); paint.setShader(null);
        if (active) {
            paint.setColor(0x0DFFFFFF);
            canvas.drawRoundRect(card, radius, radius, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        if (innerColor != null) {
            if (active) {
                float inset = keyline * .8f; rim.set(card); rim.inset(inset, inset);
                paint.setStrokeWidth(stroke * .65f); paint.setColor(innerColor);
                canvas.drawRoundRect(rim, Math.max(0, radius - inset), Math.max(0, radius - inset), paint);
            }
            return;
        }
        if (active) {
            // A dark keyline keeps the white rim visible on white app banners too.
            paint.setStrokeWidth(keyline); paint.setColor(0xE612191F);
            canvas.drawRoundRect(rim, Math.max(0, radius - keyline / 2), Math.max(0, radius - keyline / 2), paint);
        }
        paint.setStrokeWidth(active ? stroke : stroke / 2);
        paint.setColor(active ? Ui.WHITE : idleColor);
        canvas.drawRoundRect(rim, Math.max(0, radius - keyline / 2), Math.max(0, radius - keyline / 2), paint);
    }

    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter filter) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
