package ch.atelier.tv;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.widget.FrameLayout;

/** A small cached material sample. The painting itself stays sharp; no per-frame blur. */
final class CleanFrameGlass extends FrameLayout {
    private final Ui ui;
    private final ArtView painting;
    private final Paint paint = new Paint(3);
    private Bitmap sample;
    private final android.graphics.RectF surface=new android.graphics.RectF();
    private Shader sheen;
    private boolean morphing;
    private int veil = 0x28101416;
    private final Runnable refresh = this::sample;
    CleanFrameGlass(Ui ui, ArtView painting) {
        super(ui.context); this.ui = ui; this.painting = painting;
        setBackground(ui.round(0x1810181C, 24, 0, 0)); setClipToOutline(true); setWillNotDraw(false);
    }
    void setMorphing(boolean value) { morphing = value; removeCallbacks(refresh); if (!value) post(refresh); }
    void artworkChanged() { removeCallbacks(refresh); postDelayed(refresh, 820); }
    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        surface.set(0,0,w,h);sheen=new LinearGradient(0,0,0,Math.max(1,h),0x18FFFFFF,0x04FFFFFF,Shader.TileMode.CLAMP);
        removeCallbacks(refresh); if (!morphing) post(refresh);
    }
    private void sample() {
        if (morphing || !isAttachedToWindow() || getWidth() == 0 || painting.currentImage() == null) return;
        int w = Math.max(1, getWidth() / 10), h = Math.max(1, getHeight() / 10);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); canvas.scale(w / (float)getWidth(), h / (float)getHeight());
        Matrix source = new Matrix(), target = new Matrix(), inverse = new Matrix();
        painting.transformMatrixToGlobal(source); transformMatrixToGlobal(target); target.invert(inverse); source.postConcat(inverse);
        canvas.concat(source); painting.draw(canvas);
        int[] pixels = new int[w * h], next = new int[w * h]; bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 2; pass++) {
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int red = 0, green = 0, blue = 0;
                for (int yy = -2; yy <= 2; yy++) for (int xx = -2; xx <= 2; xx++) {
                    int c = pixels[Math.max(0, Math.min(h - 1, y + yy)) * w + Math.max(0, Math.min(w - 1, x + xx))];
                    red += c >> 16 & 255; green += c >> 8 & 255; blue += c & 255;
                }
                next[y * w + x] = 0xFF000000 | red / 25 << 16 | green / 25 << 8 | blue / 25;
            }
            int[] swap = pixels; pixels = next; next = swap;
        }
        double brightness = 0; for (int color : pixels) brightness += ColorTone.luminance(color);
        brightness /= pixels.length; veil = brightness > .45 ? 0x68101416 : brightness > .2 ? 0x48101416 : 0x28101416;
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h); sample = bitmap; invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); paint.setShader(null); paint.setStyle(Paint.Style.FILL); paint.setColor(0xBDFFFFFF);
        if (sample != null) canvas.drawBitmap(sample, null, surface, paint);
        float reading=Math.max(0,Math.min(1,(getHeight()-ui.px(52))/(float)ui.px(304)));
        int tinted=Color.argb(Math.round(Color.alpha(veil)+(0x9A-Color.alpha(veil))*reading),0x10,0x14,0x16);
        canvas.drawColor(tinted);paint.setColor(Color.WHITE);
        paint.setShader(sheen);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint); paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE); paint.setColor(0x35FFFFFF); paint.setStrokeWidth(ui.px(.5f));
        canvas.drawRoundRect(.5f, .5f, getWidth() - .5f, getHeight() - .5f, ui.px(24), ui.px(24), paint);
    }
    @Override protected void onDetachedFromWindow() { removeCallbacks(refresh); sample = null; super.onDetachedFromWindow(); }
}
