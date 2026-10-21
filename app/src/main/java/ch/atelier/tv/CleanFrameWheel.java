package ch.atelier.tv;

import android.animation.ValueAnimator;
import android.graphics.*;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import java.util.ArrayList;

/** A circular focus list. Only the presentation moves; focus and activation stay immediate. */
final class CleanFrameWheel extends FrameLayout {
    private final Ui ui;
    private final ArrayList<View> entries = new ArrayList<>();
    private final Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
    CleanFrameWheel(Ui ui) {
        super(ui.context); this.ui=ui; setClipChildren(true); setClipToPadding(true);
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }
    void register(View wrapper) { entries.add(wrapper); wrapper.setCameraDistance(ui.px(1200)); wrapper.setPivotX(ui.px(80)); }
    void select(int selected, boolean animate) {
        int count=entries.size(); if(count==0)return;
        for(int i=0;i<count;i++) {
            View v=entries.get(i); int distance=i-selected;
            while(distance>count/2)distance-=count;
            while(distance<-(count/2))distance+=count;
            float y=ui.px(distance*64), depth=Math.abs(distance);
            float alpha=depth==0?1:depth==1?.67f:depth==2?.28f:.04f;
            float scale=Math.max(.8f,1-depth*.065f), angle=-distance*9f;
            v.animate().cancel();
            // Recycle an almost invisible end item across the back of the wheel.
            if(Math.abs(v.getTranslationY()-y)>ui.px(200)) { v.setTranslationY(y); v.setAlpha(0); }
            if(animate&&ValueAnimator.areAnimatorsEnabled()) v.animate().translationY(y).rotationX(angle).scaleX(scale).scaleY(scale).alpha(alpha).setDuration(190).setInterpolator(new PathInterpolator(.16f,1,.3f,1)).start();
            else { v.setTranslationY(y);v.setRotationX(angle);v.setScaleX(scale);v.setScaleY(scale);v.setAlpha(alpha); }
        }
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        mask.setShader(new LinearGradient(0,0,0,h,new int[]{0x00FFFFFF,0xFFFFFFFF,0xFFFFFFFF,0x00FFFFFF},new float[]{0,.18f,.82f,1},Shader.TileMode.CLAMP));
    }
    @Override protected void dispatchDraw(Canvas canvas) {
        int save=canvas.saveLayer(0,0,getWidth(),getHeight(),null);super.dispatchDraw(canvas);
        canvas.drawRect(0,0,getWidth(),getHeight(),mask);canvas.restoreToCount(save);
    }
    @Override protected void onDetachedFromWindow(){for(View v:entries)v.animate().cancel();super.onDetachedFromWindow();}
}
