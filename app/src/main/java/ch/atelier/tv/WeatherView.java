package ch.atelier.tv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Current conditions only on Home; forecasts remain available in the weather details. */
final class WeatherView extends FrameLayout {
    private final Ui ui;
    private final LinearLayout current;
    private Weather.Report report;
    private String city = "Moudon";
    WeatherView(Ui ui) {
        super(ui.context); this.ui = ui;
        current = new LinearLayout(ui.context); current.setOrientation(LinearLayout.HORIZONTAL);
        current.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        addView(current, new LayoutParams(-1, -1));
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    void setStartAligned(boolean start) { current.setGravity((start ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL); }
    void render(String city, Weather.Report report) {
        this.city = city; this.report = report; current.removeAllViews(); setAlpha(report != null && report.stale() ? .55f : .9f);
        if (report != null) {
            LinearLayout.LayoutParams icon = new LinearLayout.LayoutParams(ui.px(13), ui.px(13));
            icon.rightMargin = ui.px(5); current.addView(new Symbol(ui, report.code, report.daylight), icon);
        }
        TextView label = ui.text(report == null ? "—°" : report.temperature + "°", 10.5f, Ui.WHITE, false);
        label.setShadowLayer(ui.px(1.5f), 0, ui.px(.5f), 0x78000000);
        current.addView(label, new LinearLayout.LayoutParams(-2, -1)); ui.fit(label);
        setContentDescription(details());
    }
    String details() {
        if (report == null) return city + " · Météo indisponible.";
        String value = report.city + " · " + report.temperature + "° · " + Weather.description(report.code);
        if (report.stale()) value += "\nDernière observation à " + report.time.substring(11,16) + ".";
        if (report.tomorrow != null) value += "\nDemain · " + Weather.description(report.tomorrow.code) + " · " + report.tomorrow.range();
        if (report.afterTomorrow != null) value += "\nAprès-demain · " + Weather.description(report.afterTomorrow.code) + " · " + report.afterTomorrow.range();
        return value;
    }
    private static final class Symbol extends View implements ArtworkTextTone.Symbol {
        private int color=Ui.WHITE;
        public int artworkColor(){return color;}
        public void setArtworkColor(int value){color=value;invalidate();}
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int code;
        private final boolean daylight;
        Symbol(Ui ui, int code, boolean daylight) { super(ui.context); this.code = code; this.daylight = daylight; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); canvas.save(); canvas.scale(getWidth() / 24f, getHeight() / 24f);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setColor(color);
            if (code <= 1) {
                if (!daylight) { canvas.drawArc(5, 3, 20, 19, 45, 280, false, paint); canvas.drawArc(10, 1, 23, 14, 80, 165, false, paint); }
                else {
                    paint.setColor(color); canvas.drawCircle(12, 12, 4, paint);
                    for (int i = 0; i < 8; i++) { canvas.save(); canvas.rotate(i * 45, 12, 12); canvas.drawLine(12, 2, 12, 5, paint); canvas.restore(); }
                }
            } else if (code == 45 || code == 48) {
                for (int i = 0; i < 3; i++) canvas.drawLine(4 + i % 2 * 3, 7 + i * 5, 20 - i % 2 * 3, 7 + i * 5, paint);
            } else {
                canvas.drawArc(3, 8, 13, 18, 90, 210, false, paint); canvas.drawArc(7, 4, 19, 16, 185, 175, false, paint);
                canvas.drawArc(13, 9, 23, 18, 255, 195, false, paint); canvas.drawLine(8, 18, 18, 18, paint);
                if (code >= 95) { paint.setColor(color); canvas.drawLine(13, 14, 10, 20, paint); canvas.drawLine(10, 20, 14, 20, paint); canvas.drawLine(14, 20, 12, 23, paint); }
                else if (code >= 51) {
                    paint.setColor(color);
                    for (int i = 0; i < 3; i++) {
                        float x = 7 + i * 5;
                        if ((code >= 71 && code <= 77) || code == 85 || code == 86) { canvas.drawLine(x - 1, 20, x + 1, 22, paint); canvas.drawLine(x - 1, 22, x + 1, 20, paint); }
                        else canvas.drawLine(x, 20, x - 1, 23, paint);
                    }
                }
            }
            canvas.restore();
        }
    }
}
