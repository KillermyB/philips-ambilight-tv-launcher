package ch.atelier.tv;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.text.*;
import android.view.*;
import android.widget.TextView;
import java.util.*;

/** Shadow-free ink chosen once per semantic section, never independently per glyph. */
final class ArtworkTextTone {
    interface Symbol { int artworkColor(); void setArtworkColor(int color); }
    private static final String TAG="artwork-tone";
    // Paintings contain many mid-tones: reserve dark ink for genuinely pale sections.
    // Separate thresholds keep a section from flickering between inks near the boundary.
    private static final double DARK_INK_ENTER=.50, DARK_INK_LEAVE=.42;
    private static final Map<TextView,NavigationInk> navigation=new WeakHashMap<>();
    private static final class NavigationInk { int color=Ui.WHITE; boolean active; }
    private static final class Section {
        final View[] members; int color=Ui.WHITE;
        Section(View... members){this.members=members;}
    }
    private final ArtView painting;
    private final List<Section> defaults=new ArrayList<>(),groups=new ArrayList<>();
    private final Map<TextView,TextWatcher> watched=new WeakHashMap<>();
    private boolean queued;
    private final Runnable update=()->{queued=false;refresh();};
    private final Runnable settled=this::refresh;
    ArtworkTextTone(ArtView painting,View... targets){this.painting=painting;for(View target:targets)defaults.add(new Section(target));}
    ArtworkTextTone unify(View... members){groups.add(new Section(members));return this;}
    static boolean controls(TextView text){return TAG.equals(text.getTag())||"material-tone".equals(text.getTag());}
    static void material(TextView... texts){for(TextView text:texts){text.setTag("material-tone");text.setShadowLayer(0,0,0,0);}}
    static void navigation(TextView text,boolean active){
        NavigationInk ink=navigation.get(text);if(ink==null){ink=new NavigationInk();navigation.put(text,ink);}ink.active=active;applyNavigation(text,ink);
    }
    private static void applyNavigation(TextView text,NavigationInk ink){
        text.setTextColor(ink.color);text.setShadowLayer(0,0,0,0);
        boolean dark=ink.color==Ui.INK;
        android.graphics.drawable.GradientDrawable surface=new android.graphics.drawable.GradientDrawable();
        surface.setColor(ink.active?(dark?0x38FFFFFF:0x38080E12):0);float unit=text.getResources().getDisplayMetrics().widthPixels/960f;
        surface.setCornerRadius(16*unit);surface.setStroke(ink.active?Math.max(1,Math.round(.5f*unit)):0,dark?0x65121818:0x70F7F5EF);text.setBackground(surface);
    }
    private void collect(View v,List<View> leaves){
        if(v==null)return;
        if(v instanceof TextView||v instanceof Symbol)leaves.add(v);
        else if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)collect(group.getChildAt(i),leaves);}
    }
    private void request(){if(!queued){queued=true;painting.post(update);}}
    void artworkChanged(){refresh();painting.removeCallbacks(settled);painting.removeCallbacks(update);queued=false;painting.postDelayed(settled,800);painting.postDelayed(update,400);}
    private RectF area(View v){
        if(!v.isShown()||v.getWidth()==0)return null;
        if(v instanceof TextView){
            TextView text=(TextView)v;
            Layout layout=text.getLayout();if(layout==null||layout.getLineCount()==0||text.length()==0)return null;
            float x=text.getTotalPaddingLeft()-text.getScrollX(),y=text.getTotalPaddingTop()-text.getScrollY();
            float left=Float.MAX_VALUE,right=-Float.MAX_VALUE;for(int i=0;i<layout.getLineCount();i++){left=Math.min(left,layout.getLineLeft(i));right=Math.max(right,layout.getLineRight(i));}
            return new RectF(x+left,y+layout.getLineTop(0),x+right,y+layout.getLineBottom(layout.getLineCount()-1));
        }
        return new RectF(0,0,v.getWidth(),v.getHeight());
    }
    private void watch(View v){
        if(!(v instanceof TextView))return;TextView text=(TextView)v;
        text.setTag(TAG);if(text.getShadowRadius()!=0)text.setShadowLayer(0,0,0,0);
        if(!watched.containsKey(text)){TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){request();}public void afterTextChanged(Editable e){}};watched.put(text,watcher);text.addTextChangedListener(watcher);}
    }
    void refresh(){
        Matrix source=new Matrix(),inverse=new Matrix();painting.transformMatrixToGlobal(source);if(!source.invert(inverse))return;
        Set<View> grouped=Collections.newSetFromMap(new IdentityHashMap<View,Boolean>());
        for(Section group:groups){List<View> leaves=new ArrayList<>();for(View member:group.members)collect(member,leaves);grouped.addAll(leaves);refresh(group,leaves,inverse);}
        for(Section section:defaults){List<View> leaves=new ArrayList<>();for(View member:section.members)collect(member,leaves);leaves.removeAll(grouped);refresh(section,leaves,inverse);}
    }
    private void refresh(Section section,List<View> leaves,Matrix inverse){
        double sum=0;int samples=0;
        for(View v:leaves){
            watch(v);RectF area=area(v);if(area==null||area.isEmpty())continue;
            Matrix position=new Matrix();v.transformMatrixToGlobal(position);position.postConcat(inverse);position.mapRect(area);
            double light=painting.luminanceBehind(area);if(light>=0){sum+=light;samples++;}
        }
        if(samples==0)return;double light=sum/samples;
        section.color=section.color==Ui.INK?(light<DARK_INK_LEAVE?Ui.WHITE:Ui.INK):(light>DARK_INK_ENTER?Ui.INK:Ui.WHITE);
        for(View v:leaves){
            if(v instanceof TextView){TextView text=(TextView)v;NavigationInk ink=navigation.get(text);
                if(ink!=null){if(ink.color!=section.color){ink.color=section.color;applyNavigation(text,ink);}}
                else text.setTextColor(text.hasFocus()&&text.isClickable()&&text.getBackground()!=null?Ui.INK:section.color);
            }else ((Symbol)v).setArtworkColor(section.color);
        }
    }
    void close(){painting.removeCallbacks(update);painting.removeCallbacks(settled);for(Map.Entry<TextView,TextWatcher> entry:watched.entrySet())entry.getKey().removeTextChangedListener(entry.getValue());watched.clear();queued=false;}
}
