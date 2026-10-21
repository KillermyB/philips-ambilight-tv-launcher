package ch.atelier.tv;

import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** One quiet headline at a time; rotation pauses while the user reads or leaves Home. */
final class NewsView extends FrameLayout {
    private final Ui ui;
    private final TextView credit, title;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<News.Item> items = new ArrayList<>();
    private int index;
    private boolean active;
    private String status = "Chargement des actualités…";
    private final Runnable tick = this::advance;
    private void advance() {
        if (!active || !isAttachedToWindow()) return;
        if (!hasFocus() && items.size() > 1) {
            index = (index + 1) % items.size(); render();
            if (ValueAnimator.areAnimatorsEnabled()) {
                title.animate().cancel(); title.setAlpha(.4f); title.setTranslationY(ui.px(3));
                title.animate().alpha(1f).translationY(0).setDuration(180).start();
            }
        }
        schedule();
    }
    NewsView(Ui ui, Runnable open) {
        super(ui.context); this.ui = ui;
        setClipChildren(false); setClipToPadding(false); setPadding(0, 0, 0, ui.px(4));
        ui.interactive(this, "Actualités choisies", open);
        credit = ui.text("ACTUALITÉS", 8, 0xFFD4DADB, true); credit.setLetterSpacing(.04f);
        title = ui.text(status, 12.5f, Ui.WHITE, false); title.setGravity(android.view.Gravity.TOP); title.setSingleLine(false); title.setMaxLines(2); title.setLineSpacing(0, 1.18f);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, ui.px(11.5f) * ui.context.getResources().getConfiguration().fontScale);
        title.setIncludeFontPadding(true);
        FrameLayout.LayoutParams creditLayout = new FrameLayout.LayoutParams(-1, ui.px(15)); creditLayout.leftMargin = creditLayout.rightMargin = ui.px(9); creditLayout.topMargin = ui.px(1); addView(credit, creditLayout); ui.fit(credit);
        // Reserve two full lines, including descenders and the 3px entrance shift.
        FrameLayout.LayoutParams titleLayout = new FrameLayout.LayoutParams(-1, ui.px(36)); titleLayout.leftMargin = titleLayout.rightMargin = ui.px(9); titleLayout.topMargin = ui.px(18); addView(title, titleLayout);
        ui.fit(title);
        title.setShadowLayer(ui.px(1.5f), 0, 1, 0x78000000);
        new SelectionFeedback(this, 1.015f, 0, 0, (highlighted, focused) ->
            setBackground(ui.round(highlighted ? 0x702A3035 : 0x00000000, 12, highlighted ? 0xB0FFFFFF : 0, highlighted ? .6f : 0)));
    }
    @Override public int getBaseline() {
        int baseline = title.getBaseline();
        return baseline < 0 ? baseline : ((FrameLayout.LayoutParams) title.getLayoutParams()).topMargin + baseline;
    }
    void update(List<News.Item> values, String status) {
        String current = items.isEmpty() ? "" : items.get(index).link;
        items.clear(); items.addAll(values); index = 0; this.status = status;
        for (int i = 0; i < items.size(); i++) if (items.get(i).link.equals(current)) index = i;
        render();
    }
    private void render() {
        if (items.isEmpty()) { credit.setText("ACTUALITÉS CHOISIES"); title.setText(status); setContentDescription("Actualités choisies. " + status); return; }
        News.Item item = items.get(index);
        credit.setText(item.credit() + "   ·   " + (index + 1) + " / " + items.size()); title.setText(item.title);
        setContentDescription("Actualités. " + item.title + ". " + item.credit() + ". OK pour tous les titres.");
    }
    void setActive(boolean value) { if (active == value) return; active = value; schedule(); }
    private void schedule() { handler.removeCallbacks(tick); if (active) handler.postDelayed(tick, 20000); }
    @Override protected void onDetachedFromWindow() { active = false; handler.removeCallbacksAndMessages(null); title.animate().cancel(); super.onDetachedFromWindow(); }
}
