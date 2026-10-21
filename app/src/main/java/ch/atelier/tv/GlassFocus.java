package ch.atelier.tv;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** Stationary frosted selection with a fine lit edge; no travelling reflection. */
final class GlassFocus extends Drawable {
    private final Paint paint=new Paint(3);private final RectF rect=new RectF();private final float radius,stroke;private Shader fill;
    GlassFocus(Ui ui,float radius){this.radius=ui.px(radius);stroke=ui.px(.6f);}
    @Override protected void onBoundsChange(Rect b){rect.set(b);rect.inset(stroke,stroke);fill=new LinearGradient(0,rect.top,0,rect.bottom,new int[]{0x65FFFFFF,0x18FFFFFF,0x32FFFFFF},new float[]{0,.55f,1},Shader.TileMode.CLAMP);}
    @Override public void draw(Canvas c){paint.setStyle(Paint.Style.FILL);paint.setShader(fill);paint.setColor(Color.WHITE);c.drawRoundRect(rect,radius,radius,paint);paint.setStyle(Paint.Style.STROKE);paint.setShader(new LinearGradient(0,rect.top,0,rect.bottom,0xB8FFFFFF,0x30FFFFFF,Shader.TileMode.CLAMP));paint.setStrokeWidth(stroke);c.drawRoundRect(rect,radius,radius,paint);paint.setShader(null);}
    @Override public void setAlpha(int a){}@Override public void setColorFilter(ColorFilter f){}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
