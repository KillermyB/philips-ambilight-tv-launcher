package ch.atelier.tv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.widget.FrameLayout;

/** Light frosted glass, sampled once from the painting without the Home controls. */
final class GlassPanel extends FrameLayout {
    private final Ui ui;
    private final ArtView wallpaper;
    private final boolean substantial;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds = new RectF();
    private Bitmap sample;
    private Shader tint;

    GlassPanel(Ui ui, ArtView wallpaper) {
        this(ui, wallpaper, false);
    }
    GlassPanel(Ui ui, ArtView wallpaper, boolean substantial) {
        super(ui.context); this.ui = ui; this.wallpaper = wallpaper; this.substantial = substantial;
        setBackground(ui.round(0xC0151C22, 22, 0, 0)); setClipToOutline(true); setWillNotDraw(false);
    }
    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight); bounds.set(0, 0, width, height);
        tint = new LinearGradient(0, 0, width * .3f, height,
            substantial ? new int[]{0xB8151C22, 0xD0111920} : new int[]{0x8C151C22, 0xAA111920}, null, Shader.TileMode.CLAMP);
        post(this::samplePainting);
    }
    private void samplePainting() {
        if (!isAttachedToWindow() || wallpaper.getWidth() == 0 || getWidth() == 0) return;
        int[] location = new int[2], origin = new int[2]; getLocationOnScreen(location); wallpaper.getLocationOnScreen(origin);
        // Sample the final position, independently of the sheet's entrance animation.
        float left = location[0] - getTranslationX() - getPivotX() * (1 - getScaleX());
        float top = location[1] - getTranslationY() - getPivotY() * (1 - getScaleY());
        sample = Bitmap.createBitmap(Math.max(1, getWidth() / 12), Math.max(1, getHeight() / 12), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sample); canvas.scale(sample.getWidth() / (float)getWidth(), sample.getHeight() / (float)getHeight());
        canvas.translate(origin[0] - left, origin[1] - top); wallpaper.draw(canvas);
        int width = sample.getWidth(), height = sample.getHeight(); int[] pixels = new int[width * height], next = new int[pixels.length];
        sample.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int pass = 0; pass < 2; pass++) {
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int red = 0, green = 0, blue = 0;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int color = pixels[Math.max(0, Math.min(height - 1, y + dy)) * width + Math.max(0, Math.min(width - 1, x + dx))];
                    red += color >> 16 & 255; green += color >> 8 & 255; blue += color & 255;
                }
                next[y * width + x] = 0xFF000000 | (red / 9) << 16 | (green / 9) << 8 | blue / 9;
            }
            int[] swap = pixels; pixels = next; next = swap;
        }
        sample.setPixels(pixels, 0, width, 0, 0, width, height); invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); paint.setColor(0xFFFFFFFF); paint.setStyle(Paint.Style.FILL); paint.setShader(null);
        if (sample != null) canvas.drawBitmap(sample, null, bounds, paint);
        paint.setShader(tint); canvas.drawRoundRect(bounds, ui.px(22), ui.px(22), paint); paint.setShader(null);
        paint.setColor(0x45FFFFFF); paint.setStrokeWidth(ui.px(.6f)); paint.setStyle(Paint.Style.STROKE);
        float inset = ui.px(.6f); canvas.drawRoundRect(inset, inset, getWidth() - inset, getHeight() - inset, ui.px(22), ui.px(22), paint);
    }
    @Override protected void onDetachedFromWindow() { animate().cancel(); sample = null; super.onDetachedFromWindow(); }
}
