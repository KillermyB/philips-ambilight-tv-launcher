package ch.atelier.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** Static vector scenery. No timer, bitmap wallpaper or perpetual animation. */
final class Backdrop extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path ridge = new Path();
    private final boolean hero;
    private int accent = 0xFF4F527F;

    Backdrop(Context context, boolean hero) { super(context); this.hero = hero; }
    void setAccent(int value) { accent = value; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        paint.setShader(new LinearGradient(0, 0, w, h,
            hero ? new int[]{0xFF172034, 0xFF192039, 0xFF101522} : new int[]{0xFF111722, 0xFF0B0D14},
            null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(new RadialGradient(w * .77f, h * .13f, w * .64f,
            new int[]{withAlpha(accent, hero ? 210 : 60), Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        if (hero) {
            paint.setShader(new LinearGradient(0, h * .3f, w, h,
                new int[]{0x002E3C65, 0x663A4875, 0xFF11192A}, null, Shader.TileMode.CLAMP));
            ridge.reset(); ridge.moveTo(w * .34f, h);
            ridge.cubicTo(w * .49f, h * .33f, w * .61f, h * .98f, w * .76f, h * .41f);
            ridge.cubicTo(w * .86f, h * .05f, w * .98f, h * .39f, w, h * .23f);
            ridge.lineTo(w, h); ridge.close(); canvas.drawPath(ridge, paint);
            paint.setShader(new LinearGradient(0, 0, w * .64f, 0,
                new int[]{0xC50F1521, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
        }
        paint.setShader(null);
    }

    private int withAlpha(int color, int alpha) { return (color & 0x00FFFFFF) | (alpha << 24); }
}
