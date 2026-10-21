package ch.atelier.tv;

import android.content.SharedPreferences;
import android.widget.FrameLayout;

/** Inset only interface layers. Paintings and music backgrounds keep filling the TV. */
final class SafeArea extends FrameLayout implements SharedPreferences.OnSharedPreferenceChangeListener {
    static final String KEY = "interface_margin";
    final FrameLayout content;
    private final SharedPreferences prefs;
    SafeArea(Ui ui) {
        super(ui.context); prefs = ui.context.getSharedPreferences("atelier", 0);
        setClipChildren(false); setClipToPadding(false);
        content = new FrameLayout(ui.context); content.setClipChildren(false); content.setClipToPadding(false);
        addView(content, new LayoutParams(-1, -1));
    }
    static FrameLayout attach(FrameLayout surface, Ui ui) {
        SafeArea area = new SafeArea(ui); surface.addView(area, new LayoutParams(-1, -1)); return area.content;
    }
    static int percent(SharedPreferences prefs) { return Math.max(0, Math.min(5, prefs.getInt(KEY, 0))); }
    static float scale(SharedPreferences prefs) { return 1f - percent(prefs) / 50f; }
    private void apply() {
        content.setPivotX(getWidth() / 2f); content.setPivotY(getHeight() / 2f);
        float scale = scale(prefs); content.setScaleX(scale); content.setScaleY(scale);
        getViewTreeObserver().dispatchOnGlobalLayout();
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) { super.onLayout(changed, l, t, r, b); apply(); }
    @Override public void onSharedPreferenceChanged(SharedPreferences prefs, String key) { if (KEY.equals(key)) apply(); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); prefs.registerOnSharedPreferenceChangeListener(this); apply(); }
    @Override protected void onDetachedFromWindow() { prefs.unregisterOnSharedPreferenceChangeListener(this); super.onDetachedFromWindow(); }
}
