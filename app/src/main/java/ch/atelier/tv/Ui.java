package ch.atelier.tv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

final class Ui {
    static final int WHITE = 0xFFF7F5EF, MUTED = 0xFFC2C5C4, INK = 0xFF121818;
    final Context context;
    final float unit;
    final Typeface body, medium, editorial;
    private final java.util.Map<TextView, Float> preferredTextSizes = new java.util.WeakHashMap<>();

    Ui(Context context) { this(context, true); }
    Ui(Context context, boolean themed) {
        this.context = context;
        unit = Math.min(context.getResources().getDisplayMetrics().widthPixels / 960f,
            context.getResources().getDisplayMetrics().heightPixels / 540f);
        boolean clean = themed && "cleanframe".equals(context.getSharedPreferences("atelier", 0).getString("ui_theme", "atelier"));
        String font = clean ? "fonts/manrope.ttf" : "fonts/inter.ttf";
        body = new Typeface.Builder(context.getAssets(), font).setFontVariationSettings(clean ? "'wght' 500" : "'wght' 350, 'opsz' 20").setWeight(clean ? 500 : 350).build();
        medium = new Typeface.Builder(context.getAssets(), font).setFontVariationSettings(clean ? "'wght' 600" : "'wght' 500, 'opsz' 20").setWeight(clean ? 600 : 500).build();
        editorial = medium;
    }

    int px(float value) { return Math.round(value * unit); }
    TextView text(String value, float size, int color, boolean emphasis) {
        TextView view = new TextView(context); view.setText(value); view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size) * context.getResources().getConfiguration().fontScale);
        view.setTypeface(emphasis ? medium : body); view.setIncludeFontPadding(true);
        view.setSingleLine(true); view.setEllipsize(TextUtils.TruncateAt.END);
        view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    TextView button(String title, float size, Runnable action) { return button(title, size, action, false); }
    TextView navigation(String title, float size, Runnable action) { return button(title, size, action, true); }
    private TextView button(String title, float size, Runnable action, boolean navigation) {
        TextView view = text(title, size, WHITE, !navigation); view.setGravity(Gravity.CENTER);
        view.setPadding(px(9), 0, px(9), 0);
        int idle = Color.TRANSPARENT;
        if (navigation) ArtworkTextTone.navigation(view, false);
        view.setBackground(round(idle, 16, Color.TRANSPARENT, 0));
        interactive(view, title, action);
        new SelectionFeedback(view, navigation ? 1.015f : 1.025f, 0, 0, (active, focused) -> {
            if (navigation) ArtworkTextTone.navigation(view, active);
            else { view.setTextColor(active ? INK : WHITE); view.setBackground(round(active ? WHITE : idle, 16, Color.TRANSPARENT, 0)); }
        });
        return view;
    }
    void interactive(View view, String description, Runnable action) {
        view.setId(View.generateViewId()); view.setFocusable(true); view.setClickable(true);
        view.setDefaultFocusHighlightEnabled(false); view.setContentDescription(description);
        view.setOnClickListener(item -> action.run());
    }
    GradientDrawable round(int color, float radius, int border, float thickness) {
        GradientDrawable value = new GradientDrawable(); value.setColor(color);
        value.setCornerRadius(px(radius)); value.setStroke(px(thickness), border); return value;
    }
    // Fixed chrome fits its actual box; long reading text keeps its chosen size and scrolls.
    void fit(TextView text) {
        Float preferred = preferredTextSizes.get(text);
        if (preferred == null) { preferred = text.getTextSize(); preferredTextSizes.put(text, preferred); }
        text.setHorizontallyScrolling(false); text.setIncludeFontPadding(true);
        float fontScale = context.getResources().getConfiguration().fontScale;
        int minimum = Math.max(1, Math.round(preferred / Math.max(1, fontScale) * .78f));
        text.setAutoSizeTextTypeUniformWithConfiguration(minimum, Math.max(minimum, Math.round(preferred)), 1, TypedValue.COMPLEX_UNIT_PX);
    }
    void place(FrameLayout parent, View view, float x, float y, float width, float height) {
        FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(px(width), px(height));
        layout.leftMargin = px(x); layout.topMargin = px(y); parent.addView(view, layout);
        if (view instanceof TextView && height > 0) fit((TextView) view);
    }
}
