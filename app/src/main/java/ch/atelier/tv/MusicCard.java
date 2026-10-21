package ch.atelier.tv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

final class MusicCard extends FrameLayout {
    static final int WIDTH=182,HEIGHT=80;
    private final Ui ui;
    private final ImageView artwork;
    private final TextView title, artist;
    private final View previous, toggle, next;
    private final Glyph toggleGlyph;
    private final Progress progress;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MusicAccessService.Track track;
    private boolean active, compact;
    private int returnTarget=View.NO_ID;
    private MusicTile tile;
    private final Runnable tick = new Runnable() {
        @Override public void run() { progress.invalidate(); if(tile!=null)tile.invalidateSelf(); if (active && track != null && track.playing) handler.postDelayed(this, 100); }
    };
    MusicCard(Ui ui, Runnable openSpotify) {
        super(ui.context); this.ui = ui; setClipChildren(false);
        setBackground(ui.round(0x99172220, 11, 0x22FFFFFF, .5f));
        ui.interactive(this, "Spotify", openSpotify);
        FocusFrame focus = new FocusFrame(this, ui, 0x22FFFFFF, 11); setForeground(focus);
        new SelectionFeedback(this, 1.02f, ui.px(1), ui.px(4), (value, focused) -> focus.setActive(value));
        artwork = new ImageView(ui.context); artwork.setScaleType(ImageView.ScaleType.FIT_CENTER);
        artwork.setBackground(ui.round(0xFF16201C, 7, 0, 0)); artwork.setClipToOutline(true);
        artwork.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); ui.place(this, artwork, 9, 9, 42, 42);
        title = ui.text("Spotify", 10, Ui.WHITE, true); ui.place(this, title, 63, 6, 156, 17);
        artist = ui.text("", 8, Ui.MUTED, false); ui.place(this, artist, 63, 23, 156, 13);
        previous = control(-1, "Morceau précédent"); toggle = control(0, "Lecture"); next = control(1, "Morceau suivant");
        toggleGlyph = (Glyph) ((FrameLayout) toggle).getChildAt(0);
        ui.place(this, previous, 59, 36, 25, 22); ui.place(this, toggle, 92, 36, 25, 22); ui.place(this, next, 125, 36, 25, 22);
        setNextFocusDownId(toggle.getId());
        previous.setNextFocusLeftId(getId()); previous.setNextFocusRightId(toggle.getId()); toggle.setNextFocusLeftId(previous.getId());
        toggle.setNextFocusRightId(next.getId()); next.setNextFocusLeftId(toggle.getId()); next.setNextFocusRightId(getId());
        for (View control : new View[]{previous, toggle, next}) control.setNextFocusUpId(getId());
        progress = new Progress(ui); ui.place(this, progress, 63, 59, 156, 1);
        setCompact();
    }
    void setCompact() {
        if(compact)return; compact=true;
        tile=new MusicTile(ui);setBackground(tile);artwork.setVisibility(GONE);progress.setVisibility(GONE);
        layout(title,10,7,162,28);layout(artist,10,36,162,12);
        title.setSingleLine(false);title.setMaxLines(2);title.setLineSpacing(0,1.05f);title.setGravity(android.view.Gravity.TOP);
        title.setAutoSizeTextTypeUniformWithConfiguration(ui.px(8.5f),ui.px(10.5f),1,android.util.TypedValue.COMPLEX_UNIT_PX);
        artist.setAutoSizeTextTypeUniformWithConfiguration(ui.px(7),ui.px(8),1,android.util.TypedValue.COMPLEX_UNIT_PX);
        title.setTextColor(Ui.WHITE);artist.setTextColor(0xFFD9DFE2);ArtworkTextTone.material(title,artist);
        layout(previous,42,52,25,22);layout(toggle,78,52,25,22);layout(next,114,52,25,22);
        android.graphics.drawable.GradientDrawable frame=ui.round(0,12,0,0);
        setForeground(new android.graphics.drawable.InsetDrawable(frame,ui.px(3)));
        new SelectionFeedback(this,1.015f,0,0,(selected,focused)->{frame.setStroke(selected?ui.px(.7f):0,0xA0FFFFFF);});
        if(track!=null)tile.render(track);
    }
    private void layout(View v,float x,float y,float w,float h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(ui.px(w),ui.px(h));p.leftMargin=ui.px(x);p.topMargin=ui.px(y);v.setLayoutParams(p);}
    private View control(int action, String description) {
        FrameLayout button = new FrameLayout(ui.context); Glyph glyph = new Glyph(ui, action);
        ui.place(button, glyph, 6, 4, 13, 13); ui.interactive(button, description, () -> MusicAccessService.command(action));
        button.setBackground(ui.round(action==0?0xDAF7F5EF:0x30000000,11,0,0));glyph.color=action==0?Ui.INK:Ui.WHITE;
        new SelectionFeedback(button, 1.12f, 0, 0, (value, focused) -> {
            button.setBackground(ui.round(value ? Ui.WHITE : action==0?0xDAF7F5EF:0x30000000, 11, 0, 0)); glyph.color = value||action==0 ? Ui.INK : Ui.WHITE; glyph.invalidate();
        }); return button;
    }
    private void wireTransport() {
        java.util.ArrayList<View> enabled=new java.util.ArrayList<>();
        for(View control:new View[]{previous,toggle,next})if(control.isEnabled())enabled.add(control);
        setNextFocusDownId(enabled.isEmpty()?(returnTarget==View.NO_ID?getId():returnTarget):toggle.isEnabled()?toggle.getId():enabled.get(0).getId());
        for(int i=0;i<enabled.size();i++){View control=enabled.get(i);control.setNextFocusLeftId(i==0?getId():enabled.get(i-1).getId());control.setNextFocusRightId(i==enabled.size()-1?getId():enabled.get(i+1).getId());control.setNextFocusUpId(getId());}
    }
    void returnTo(int id) { returnTarget=id; for (View control : new View[]{previous, toggle, next}) control.setNextFocusDownId(id); wireTransport(); }
    void render(MusicAccessService.Track value) {
        track = value;
        if (value.cover != null) { artwork.setImageBitmap(value.cover); artwork.setContentDescription("Pochette du morceau en cours"); }
        else {
            try { artwork.setImageDrawable(ui.context.getPackageManager().getApplicationIcon(MusicAccessService.SPOTIFY)); }
            catch (Exception ignored) { artwork.setImageDrawable(null); }
        }
        title.setText(value.title.isEmpty() ? "Spotify" : value.title);
        artist.setText(!value.access ? "Activer la carte dans Réglages" : value.title.isEmpty() ? (value.playing ? "Lecture en cours" : "Lancez un morceau dans Spotify") : value.artist);
        toggleGlyph.paused = !value.playing; toggleGlyph.invalidate(); toggle.setContentDescription(value.playing ? "Pause" : "Lecture");
        boolean known = value.session && !value.title.isEmpty();
        enable(previous, value.canCommand(-1)); enable(next, value.canCommand(1)); enable(toggle, value.canCommand(0)); wireTransport();
        progress.setVisibility(compact ? GONE : known && value.duration > 0 ? VISIBLE : INVISIBLE);
        if(tile!=null)tile.render(value);
        setContentDescription(value.title.isEmpty() ? "Spotify. " + artist.getText() : value.title + ", " + value.artist + ". " + (value.playing ? "En lecture" : "En pause") + ". Mode écoute.");
        setActive(active);
    }
    private void enable(View view, boolean enabled) {
        if (!enabled && view.hasFocus()) requestFocus(); view.setEnabled(enabled); view.setFocusable(enabled); view.setAlpha(enabled ? 1f : .3f);
    }
    void setActive(boolean value) {
        active = value; handler.removeCallbacks(tick); progress.invalidate();if(tile!=null)tile.invalidateSelf();
        if (value && track != null && track.playing) handler.postDelayed(tick, 100);
    }
    @Override protected void onDetachedFromWindow() { setActive(false); super.onDetachedFromWindow(); }
    private final class Progress extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Progress(Ui ui) { super(ui.context); }
        @Override protected void onDraw(Canvas canvas) {
            paint.setColor(0x35FFFFFF); canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setColor(0xFFE2E9E0); canvas.drawRect(0, 0, getWidth() * (track == null ? 0 : track.progress()), getHeight(), paint);
        }
    }
    static final class Glyph extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); final Path path = new Path(); final int action;
        int color = Ui.WHITE; boolean paused = true;
        Glyph(Ui ui, int action) { super(ui.context); this.action = action; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            canvas.save(); canvas.scale(getWidth() / 24f, getHeight() / 24f); paint.setColor(color); paint.setStyle(Paint.Style.FILL);
            if (action == 0 && !paused) { canvas.drawRoundRect(6, 4, 10, 20, 1, 1, paint); canvas.drawRoundRect(14, 4, 18, 20, 1, 1, paint); }
            else {
                if (action < 0) { canvas.translate(24, 0); canvas.scale(-1, 1); }
                path.reset(); path.moveTo(6, 4); path.lineTo(19, 12); path.lineTo(6, 20); path.close(); canvas.drawPath(path, paint);
                if (action != 0) canvas.drawRoundRect(19, 4, 22, 20, 1, 1, paint);
            }
            canvas.restore();
        }
    }
}
