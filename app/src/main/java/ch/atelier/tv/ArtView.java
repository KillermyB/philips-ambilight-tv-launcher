package ch.atelier.tv;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** The original image ratio is preserved in both contain and cover modes. */
final class ArtView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF destination = new RectF();
    private Bitmap image;
    private Bitmap previous;
    private ValueAnimator transition;
    private float blend = 1f;
    private boolean cover;

    ArtView(Context context) { super(context); }
    void setImage(Bitmap bitmap) { setImage(bitmap, false); }
    void setImage(Bitmap bitmap, boolean animate) {
        if (transition != null) { transition.cancel(); transition = null; }
        previous = animate && image != null && bitmap != null && ValueAnimator.areAnimatorsEnabled() ? image : null;
        image = bitmap; blend = previous == null ? 1f : 0f;
        if (previous != null) {
            transition = ValueAnimator.ofFloat(0f, 1f); transition.setDuration(700);
            transition.addUpdateListener(value -> {
                blend = (float) value.getAnimatedValue(); if (blend >= 1f) previous = null; invalidate();
            });
            transition.start();
        }
        invalidate();
    }
    void setCover(boolean value) { cover = value; invalidate(); }
    Bitmap currentImage() { return image; }

    @Override protected void onDraw(Canvas canvas) {
        float width = getWidth(), height = getHeight(); canvas.drawColor(0xFF101716);
        if (previous != null) drawImage(canvas, previous, width, height, 255);
        drawImage(canvas, image, width, height, Math.round(255 * blend));
        paint.setAlpha(255);
    }
    private void drawImage(Canvas canvas, Bitmap bitmap, float width, float height, int alpha) {
        if (bitmap == null || bitmap.isRecycled()) return;
        float sx = width / bitmap.getWidth(), sy = height / bitmap.getHeight();
        float scale = cover ? Math.max(sx, sy) : Math.min(sx, sy);
        float w = bitmap.getWidth() * scale, h = bitmap.getHeight() * scale;
        destination.set((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f);
        paint.setAlpha(alpha); canvas.drawBitmap(bitmap, null, destination, paint);
    }
    double luminanceBehind(RectF area) {
        if(image==null||image.isRecycled()||getWidth()==0||getHeight()==0||area.isEmpty())return -1;
        float scale=cover?Math.max(getWidth()/(float)image.getWidth(),getHeight()/(float)image.getHeight()):Math.min(getWidth()/(float)image.getWidth(),getHeight()/(float)image.getHeight());
        float left=(getWidth()-image.getWidth()*scale)/2,top=(getHeight()-image.getHeight()*scale)/2;
        double[] values=new double[40];int index=0;
        for(int y=0;y<4;y++)for(int x=0;x<10;x++){
            int ix=(int)((area.left+area.width()*(x+.5f)/10-left)/scale),iy=(int)((area.top+area.height()*(y+.5f)/4-top)/scale);
            int color=ix<0||iy<0||ix>=image.getWidth()||iy>=image.getHeight()?0xFF101716:image.getPixel(ix,iy);
            values[index++]=ColorTone.luminance(color);
        }
        java.util.Arrays.sort(values);return (values[19]+values[20])/2;
    }
    float shadeBehind(RectF area, int foreground) {
        return Math.max(shadeImage(image, area, foreground), shadeImage(previous, area, foreground));
    }
    private float shadeImage(Bitmap bitmap, RectF area, int foreground) {
        if (bitmap == null || bitmap.isRecycled() || getWidth() == 0 || getHeight() == 0) return 0;
        float sx = getWidth() / (float) bitmap.getWidth(), sy = getHeight() / (float) bitmap.getHeight();
        float scale = cover ? Math.max(sx, sy) : Math.min(sx, sy);
        float left = (getWidth() - bitmap.getWidth() * scale) / 2, top = (getHeight() - bitmap.getHeight() * scale) / 2;
        float[] values = new float[40]; int index = 0;
        for (int y = 0; y < 4; y++) for (int x = 0; x < 10; x++) {
            int ix = (int) ((area.left + area.width() * (x + .5f) / 10 - left) / scale);
            int iy = (int) ((area.top + area.height() * (y + .5f) / 4 - top) / scale);
            int color = ix < 0 || iy < 0 || ix >= bitmap.getWidth() || iy >= bitmap.getHeight() ? 0xFF101716 : bitmap.getPixel(ix, iy);
            values[index++] = ColorTone.shade(color, foreground);
        }
        java.util.Arrays.sort(values);
        return values[37];
    }
}
