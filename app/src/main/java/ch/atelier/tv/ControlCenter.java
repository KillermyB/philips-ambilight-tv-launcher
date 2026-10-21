package ch.atelier.tv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

final class ControlCenter {
    static final class Source {
        final String label, status; final Runnable select;
        Source(String label, String status, Runnable select) { this.label = label; this.status = status; this.select = select; }
    }
    final GlassPanel view;
    private final Ui ui;
    private final TextView musicDetail;
    private final View music;

    ControlCenter(Ui ui, ArtView painting, List<Source> inputs, Runnable sources, Runnable listen, Runnable sleep, Runnable settings, Runnable debugging, Runnable close) {
        this(ui, painting, inputs, sources, listen, sleep, settings, debugging, close, null);
    }
    ControlCenter(Ui ui, ArtView painting, List<Source> inputs, Runnable sources, Runnable listen, Runnable sleep, Runnable settings, Runnable debugging, Runnable close, Runnable airplay) {
        this.ui = ui; view = new GlassPanel(ui, painting, true); view.setClipChildren(false); view.setClipToPadding(false);
        TextView eyebrow = ui.text("À PORTÉE DE MAIN", 8, 0xFFC2CED2, true); eyebrow.setLetterSpacing(.13f);
        ui.place(view, eyebrow, 24, 22, 296, 16);
        TextView title = ui.text("Centre de contrôle", 22, Ui.WHITE, true); title.setLetterSpacing(-.025f);
        ui.place(view, title, 22, 43, 302, 35);
        int musicWidth=airplay==null?308:148;
        music = tile("Mode écoute", "Spotify", 0, listen, musicWidth, 66);
        musicDetail = (TextView) ((FrameLayout) music).getChildAt(2); ui.place(view, music, 18, 94, musicWidth, 66);
        View share=airplay==null?null:tile("AirPlay", "Recopie d’écran", 5, airplay, 148, 66);
        if(share!=null){ui.place(view,share,178,94,148,66);music.setNextFocusRightId(share.getId());share.setNextFocusLeftId(music.getId());share.setNextFocusUpId(share.getId());}
        TextView label = ui.text("SOURCES HDMI", 8, 0xFFBBC8CD, true); label.setLetterSpacing(.12f);
        ui.place(view, label, 24, 179, 290, 18);
        FocusScrollView scroll = new FocusScrollView(ui);
        LinearLayout rows = new LinearLayout(ui.context); rows.setOrientation(LinearLayout.VERTICAL); rows.setClipChildren(false);
        rows.setPadding(ui.px(8), ui.px(8), ui.px(8), ui.px(8)); rows.setClipToPadding(false);
        scroll.addView(rows); ui.place(view, scroll, 10, 198, 324, 144);
        if (inputs.isEmpty()) inputs.add(new Source("Toutes les sources", "Choisir une entrée TV", sources));
        for (Source source : inputs) {
            View row = tile(source.label, source.status, 1, source.select, 308, 36);
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, ui.px(36)); layout.bottomMargin = ui.px(4); rows.addView(row, layout);
        }
        View standby = tile("Veille", "Options et activation", 2, sleep, 148, 62);
        View preferences = tile("Réglages", "Votre accueil", 3, settings, 148, 62);
        ui.place(view, standby, 18, 346, 148, 62); ui.place(view, preferences, 178, 346, 148, 62);
        View debug = tile("Débogage Wi-Fi", "Connexion au Mac et association", 4, debugging, 308, 40);
        ui.place(view, debug, 18, 418, 308, 40);
        TextView exit = ui.button("Fermer", 10.5f, close); ui.place(view, exit, 103, 466, 138, 29);
        music.setNextFocusDownId(rows.getChildAt(0).getId());
        if(share!=null)share.setNextFocusDownId(rows.getChildAt(0).getId());
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            row.setNextFocusUpId(i == 0 ? music.getId() : rows.getChildAt(i - 1).getId());
            row.setNextFocusDownId(i == rows.getChildCount() - 1 ? standby.getId() : rows.getChildAt(i + 1).getId());
            row.setNextFocusLeftId(row.getId()); row.setNextFocusRightId(row.getId());
        }
        standby.setNextFocusRightId(preferences.getId()); preferences.setNextFocusLeftId(standby.getId());
        standby.setNextFocusUpId(rows.getChildAt(rows.getChildCount() - 1).getId()); preferences.setNextFocusUpId(rows.getChildAt(rows.getChildCount() - 1).getId());
        standby.setNextFocusDownId(debug.getId()); preferences.setNextFocusDownId(debug.getId());
        debug.setNextFocusUpId(standby.getId()); debug.setNextFocusDownId(exit.getId());
        debug.setNextFocusLeftId(debug.getId()); debug.setNextFocusRightId(debug.getId());
        exit.setNextFocusUpId(debug.getId()); music.setNextFocusUpId(music.getId());
    }
    private View tile(String name, String detail, int icon, Runnable action, int width, int height) {
        FrameLayout tile = new FrameLayout(ui.context); tile.setBackground(ui.round(0x12FFFFFF, 13, 0x16FFFFFF, .5f));
        ui.interactive(tile, name + ". " + detail, action);
        Glyph symbol = new Glyph(ui, icon); ui.place(tile, symbol, 13, height > 60 ? 14 : 7, 22, 22);
        boolean narrow = width < 200;
        TextView heading = ui.text(name, narrow ? 11 : 12, Ui.WHITE, true);
        TextView subtitle = ui.text(detail, narrow ? 7.5f : 8.5f, 0xFFC0CDD0, false);
        ui.place(tile, heading, narrow ? 43 : 48, height > 60 ? 12 : 1, width - (narrow ? 50 : 60), height > 60 ? 22 : 20);
        ui.place(tile, subtitle, narrow ? 13 : 48, height > 60 ? 37 : 21, width - (narrow ? 23 : 60), height > 60 ? 15 : 13);
        new SelectionFeedback(tile, 1.018f, ui.px(1), ui.px(3), (selected, focused) -> {
            tile.setBackground(ui.round(selected ? 0x3BFFFFFF : 0x12FFFFFF, 13, selected ? 0xB0FFFFFF : 0x16FFFFFF, .5f));
        });
        return tile;
    }
    void render(MusicAccessService.Track track) {
        String text = track.session && !track.title.isEmpty() ? track.title + (track.playing ? "" : " · En pause") : "Spotify · Votre musique en grand";
        musicDetail.setText(text); music.setContentDescription("Mode écoute. " + text);
    }
    void focus() { music.requestFocus(); }
    private static final class Glyph extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path(); private final int type;
        Glyph(Ui ui, int type) { super(ui.context); this.type = type; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            canvas.save(); canvas.scale(getWidth() / 24f, getHeight() / 24f); paint.setColor(0xFFE2EAEB); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.6f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
            if (type == 0) {
                path.reset(); path.moveTo(9, 17); path.lineTo(9, 5); path.lineTo(20, 3); path.lineTo(20, 15); canvas.drawPath(path, paint);
                paint.setStyle(Paint.Style.FILL); canvas.drawOval(3, 15, 10, 21, paint); canvas.drawOval(14, 13, 21, 19, paint);
            } else if (type == 1) {
                canvas.drawRoundRect(3, 5, 21, 17, 3, 3, paint); canvas.drawLine(8, 21, 16, 21, paint); canvas.drawLine(12, 17, 12, 21, paint);
            } else if (type == 2) {
                path.reset(); path.moveTo(18, 3); path.cubicTo(9, 2, 3, 12, 10, 19); path.cubicTo(14, 23, 21, 20, 22, 15); path.cubicTo(14, 18, 10, 8, 18, 3); canvas.drawPath(path, paint);
            } else if (type == 4) {
                canvas.drawArc(2, 3, 22, 23, 225, 90, false, paint);
                canvas.drawArc(6, 9, 18, 21, 225, 90, false, paint);
                paint.setStyle(Paint.Style.FILL); canvas.drawCircle(12, 18, 1.6f, paint);
            } else if(type==5){
                path.reset();path.moveTo(8,17);path.lineTo(3,17);path.lineTo(3,4);path.lineTo(21,4);path.lineTo(21,17);path.lineTo(16,17);canvas.drawPath(path,paint);
                path.reset();path.moveTo(12,13);path.lineTo(6,21);path.lineTo(18,21);path.close();canvas.drawPath(path,paint);
            } else {
                path.reset();
                for(int i=0;i<48;i++){double a=(i*7.5-90)*Math.PI/180;float radius=i%6==0||i%6==5?8.2f:10;float x=12+(float)Math.cos(a)*radius,y=12+(float)Math.sin(a)*radius;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}
                path.close();canvas.drawPath(path,paint);canvas.drawCircle(12,12,3.2f,paint);
            }
            canvas.restore();
        }
    }
}
