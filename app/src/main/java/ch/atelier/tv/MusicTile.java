package ch.atelier.tv;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Bounded, aspect-correct cover artwork with a separate colour gradient and media outline. */
final class MusicTile extends Drawable {
    private final Paint paint=new Paint(3);
    private final RectF bounds=new RectF(),imageBounds=new RectF(),artBounds=new RectF();
    private final Path clip=new Path(),outline=new Path(),elapsed=new Path();
    private final PathMeasure measure=new PathMeasure();
    private final PorterDuffXfermode fadeMode=new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
    private final float stroke,radius;
    private MusicAccessService.Track track;
    private Bitmap cover;
    private Shader shade,fade,background;
    private int tone=0xFF121B20;
    MusicTile(Ui ui){stroke=ui.px(.85f);radius=ui.px(13);}
    void render(MusicAccessService.Track value){
        track=value;
        if(cover!=value.cover){cover=value.cover;tone=coverTone();positionCover();}
        invalidateSelf();
    }
    private int coverTone(){
        if(cover==null||cover.isRecycled()||cover.getConfig()==Bitmap.Config.HARDWARE)return 0xFF121B20;
        long r=0,g=0,b=0;
        for(int y=0;y<8;y++)for(int x=0;x<8;x++){
            int c=cover.getPixel(Math.min(cover.getWidth()-1,(2*x+1)*cover.getWidth()/16),Math.min(cover.getHeight()-1,(2*y+1)*cover.getHeight()/16));
            r+=Color.red(c);g+=Color.green(c);b+=Color.blue(c);
        }
        return ColorTone.mix(Color.rgb((int)(r/64),(int)(g/64),(int)(b/64)),0xFF080D12,.65f);
    }
    private void positionCover(){
        if(cover!=null&&!cover.isRecycled()){
            artBounds.set(bounds.left+bounds.width()*.14f,bounds.top,bounds.right,bounds.bottom);
            // Fill most of the tile with a uniform scale and crop, never a stretched cover.
            float scale=Math.max(artBounds.width()/cover.getWidth(),artBounds.height()/cover.getHeight());
            float w=cover.getWidth()*scale,h=cover.getHeight()*scale;
            imageBounds.set(artBounds.centerX()-w/2,artBounds.centerY()-h/2,artBounds.centerX()+w/2,artBounds.centerY()+h/2);
            fade=new LinearGradient(artBounds.left,0,artBounds.left+Math.max(1,artBounds.width()*.78f),0,
                new int[]{0x00FFFFFF,0x18FFFFFF,0x70FFFFFF,0xCFFFFFFF,0xFFFFFFFF},new float[]{0,.18f,.44f,.72f,1},Shader.TileMode.CLAMP);
        }
        background=new LinearGradient(bounds.left,0,Math.max(bounds.left+1,bounds.right),0,tone,0xFF10171C,Shader.TileMode.CLAMP);
    }
    @Override protected void onBoundsChange(Rect r){
        bounds.set(r);bounds.inset(stroke,stroke);clip.reset();clip.addRoundRect(bounds,radius,radius,Path.Direction.CW);
        float l=bounds.left,t=bounds.top,b=bounds.bottom,right=bounds.right,c=bounds.centerX(),r0=radius;
        outline.reset();outline.moveTo(c,t);outline.lineTo(right-r0,t);outline.quadTo(right,t,right,t+r0);outline.lineTo(right,b-r0);outline.quadTo(right,b,right-r0,b);outline.lineTo(l+r0,b);outline.quadTo(l,b,l,b-r0);outline.lineTo(l,t+r0);outline.quadTo(l,t,l+r0,t);outline.lineTo(c,t);outline.close();measure.setPath(outline,false);
        shade=new LinearGradient(l,0,Math.max(l+1,right),0,new int[]{0xD0080D12,0xAC080D12,0x60080D12,0x10080D12},new float[]{0,.28f,.58f,1},Shader.TileMode.CLAMP);positionCover();
    }
    @Override public void draw(Canvas canvas){
        paint.setXfermode(null);paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);paint.setShader(background);canvas.drawRoundRect(bounds,radius,radius,paint);
        int save=canvas.save();canvas.clipPath(clip);
        if(cover!=null&&!cover.isRecycled()){
            // The crop and its mask share a bounded layer; no edge pixels are repeated.
            int layer=canvas.saveLayer(artBounds,null);canvas.clipRect(artBounds);
            paint.setShader(null);paint.setColor(Color.WHITE);canvas.drawBitmap(cover,null,imageBounds,paint);
            paint.setShader(fade);paint.setXfermode(fadeMode);canvas.drawRect(artBounds,paint);
            paint.setXfermode(null);canvas.restoreToCount(layer);
        }
        paint.setShader(shade);paint.setColor(Color.WHITE);canvas.drawRect(bounds,paint);canvas.restoreToCount(save);
        paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(0x32FFFFFF);canvas.drawPath(outline,paint);
        if(track!=null&&track.duration>0){elapsed.reset();measure.getSegment(0,measure.getLength()*Math.max(0,Math.min(1,track.progress())),elapsed,true);paint.setColor(0xF7FFFFFF);canvas.drawPath(elapsed,paint);}
    }
    @Override public void setAlpha(int a){}
    @Override public void setColorFilter(ColorFilter f){}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
