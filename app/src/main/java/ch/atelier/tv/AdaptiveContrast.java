package ch.atelier.tv;

import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Cached, feathered shadows behind actual text lines, sampled only when content changes. */
final class AdaptiveContrast extends View {
    private final Ui ui;
    private final ArtView painting;
    private final List<TextView> texts = new ArrayList<>();
    private final List<View> targets = new ArrayList<>();
    private final List<Region> regions = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Bitmap shadow;
    private boolean queued;
    private final Runnable refresh = () -> { queued = false; calculate(); };
    private final Runnable settled = this::refresh;
    private static final class Region {
        final RectF rect; final float shade;
        Region(RectF rect, float shade) { this.rect = rect; this.shade = shade; }
    }
    AdaptiveContrast(Ui ui, ArtView painting) {
        super(ui.context); this.ui = ui; this.painting = painting;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void follow(View... values) { for (View view : values) if (!targets.contains(view)) { targets.add(view); add(view); } refresh(); }
    private void collect(View view, List<TextView> current) {
        if (view instanceof TextView) { if (!current.contains(view)) current.add((TextView) view); }
        else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), current);
        }
    }
    private void add(View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view; if (texts.contains(text)) return; texts.add(text);
            text.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
                @Override public void afterTextChanged(Editable e) { }
            });
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) add(group.getChildAt(i));
        }
    }
    void refresh() { if (!queued) { queued = true; post(refresh); } }
    void artworkChanged() { refresh(); removeCallbacks(settled); postDelayed(settled, 800); }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { refresh(); }
    private void calculate() {
        if (!isAttachedToWindow() || getWidth() == 0) return;
        regions.clear();
        android.graphics.Matrix own = new android.graphics.Matrix(), inverse = new android.graphics.Matrix();
        transformMatrixToGlobal(own); own.invert(inverse);
        // Weather rows are rebuilt after each refresh; inspect the current children too.
        List<TextView> current = new ArrayList<>(); for (View target : targets) collect(target, current);
        for (TextView text : current) {
            if(ArtworkTextTone.controls(text))continue;
            Layout layout = text.getLayout();
            if (!text.isShown() || text.getText().length() == 0 || layout == null) continue;
            android.graphics.Matrix position = new android.graphics.Matrix();
            text.transformMatrixToGlobal(position); position.postConcat(inverse);
            float x = text.getTotalPaddingLeft() - text.getScrollX();
            float y = text.getTotalPaddingTop() - text.getScrollY();
            for (int line = 0; line < (text.getMaxLines() > 0 ? Math.min(layout.getLineCount(), text.getMaxLines()) : layout.getLineCount()); line++) {
                RectF area = new RectF(x + layout.getLineLeft(line), y + layout.getLineTop(line),
                    x + layout.getLineRight(line), y + layout.getLineBottom(line));
                if (area.width() <= 0 || area.top >= text.getHeight()) continue;
                position.mapRect(area);
                // Selected navigation has its own opaque light surface and dark text.
                if (text.hasFocus() && ColorTone.luminance(text.getCurrentTextColor()) < .15) continue;
                float shade = painting.shadeBehind(area, text.getCurrentTextColor());
                if (shade > .025f) { area.inset(-ui.px(4), -ui.px(1)); regions.add(new Region(area, shade)); }
            }
        }
        renderShadow(); invalidate();
    }
    private void renderShadow() {
        if (regions.isEmpty()) { shadow = null; return; }
        int width = Math.max(1, getWidth() / 6), height = Math.max(1, getHeight() / 6);
        Bitmap mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(mask); canvas.scale(width / (float) getWidth(), height / (float) getHeight());
        for (Region region : regions) {
            // Slightly more presence on bright paintings; keep medium and dark backgrounds unchanged.
            float strength = region.shade * (region.shade > .35f ? .52f : .45f);
            paint.setColor(Math.round(Math.min(.34f, strength) * 255) << 24);
            float edge = ui.px(3);
            canvas.drawRoundRect(region.rect.left - edge, region.rect.top - edge, region.rect.right + edge, region.rect.bottom + edge, ui.px(8), ui.px(8), paint);
        }
        int[] pixels = new int[width * height], next = new int[pixels.length];
        mask.getPixels(pixels, 0, width, 0, 0, width, height);
        int radius = Math.max(2, ui.px(10) / 6), span = radius * 2 + 1;
        // Two separable box passes, at one sixth resolution. Nothing blurs on each frame.
        for (int pass = 0; pass < 2; pass++) {
            for (int y = 0; y < height; y++) {
                int sum = 0, row = y * width;
                for (int i = -radius; i <= radius; i++) sum += pixels[row + Math.max(0, Math.min(width - 1, i))] >>> 24;
                for (int x = 0; x < width; x++) {
                    next[row + x] = (sum / span) << 24;
                    sum -= pixels[row + Math.max(0, x - radius)] >>> 24;
                    sum += pixels[row + Math.min(width - 1, x + radius + 1)] >>> 24;
                }
            }
            for (int x = 0; x < width; x++) {
                int sum = 0;
                for (int i = -radius; i <= radius; i++) sum += next[Math.max(0, Math.min(height - 1, i)) * width + x] >>> 24;
                for (int y = 0; y < height; y++) {
                    pixels[y * width + x] = (sum / span) << 24;
                    sum -= next[Math.max(0, y - radius) * width + x] >>> 24;
                    sum += next[Math.min(height - 1, y + radius + 1) * width + x] >>> 24;
                }
            }
        }
        // Overlapping text rows must never accumulate into a dark panel.
        for (int i = 0; i < pixels.length; i++) pixels[i] = Math.min(92, pixels[i] >>> 24) << 24;
        mask.setPixels(pixels, 0, width, 0, 0, width, height); shadow = mask;
    }
    @Override protected void onDraw(Canvas canvas) {
        if (shadow == null) return;
        paint.setColor(0xFFFFFFFF); canvas.drawBitmap(shadow, null, new RectF(0, 0, getWidth(), getHeight()), paint);
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); refresh(); }
    @Override protected void onDetachedFromWindow() {
        removeCallbacks(refresh); removeCallbacks(settled); queued = false; regions.clear(); shadow = null; super.onDetachedFromWindow();
    }
}
