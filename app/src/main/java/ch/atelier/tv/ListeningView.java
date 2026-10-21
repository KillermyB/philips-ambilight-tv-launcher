package ch.atelier.tv;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.RectF;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

/** Real Spotify metadata only. The empty state never substitutes a demonstration track. */
final class ListeningView extends FrameLayout {
    private final Ui ui;
    private final Atmosphere background;
    private final ImageView cover;
    private final TextView placeholder, title, artist, state;
    private final View previous, toggle, next;
    private final LinearLayout transport, details;
    private final TextView lyrics, source, exit, backdropCredit;
    private final MusicBackdrop backdrops;
    private final InlineLyrics inlineLyrics;
    private final FrameLayout sleeve;
    private final TextView hint;
    private final MusicCard.Glyph toggleGlyph;
    private final Timeline timeline;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MusicAccessService.Track track;
    private Bitmap currentCover, currentBackdrop;
    private boolean lyricLayout;
    private int layoutMode = -1;
    private boolean active;
    private final Runnable tick = new Runnable() {
        @Override public void run() { timeline.invalidate(); if (active && track != null && track.playing) handler.postDelayed(this, 1000); }
    };

    ListeningView(Ui ui, Runnable close) {
        super(ui.context); this.ui = ui; setClipChildren(false); setClipToPadding(false);
        background = new Atmosphere(ui); addView(background, new LayoutParams(-1, -1));
        FrameLayout content = SafeArea.attach(this, ui);
        exit = ui.button("Fermer", 11, close); ui.place(content, exit, 802, 37, 108, 32);
        sleeve = new FrameLayout(ui.context); sleeve.setBackground(ui.round(0x382F3A3B, 17, 0x28FFFFFF, .5f));
        sleeve.setElevation(ui.px(9)); sleeve.setClipToOutline(true); ui.place(content, sleeve, 54, 80, 380, 380);
        placeholder = ui.text("♪", 100, 0xFFB2C7BD, false); placeholder.setGravity(Gravity.CENTER);
        placeholder.setContentDescription("Aucune pochette disponible"); sleeve.addView(placeholder, new LayoutParams(-1, -1));
        cover = new ImageView(ui.context); cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(ui.round(0xFF182322, 17, 0, 0)); cover.setClipToOutline(true);
        cover.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); sleeve.addView(cover, new LayoutParams(-1, -1));
        state = ui.text("EN ATTENTE", 8, 0xFFC1D0CB, true); state.setLetterSpacing(.16f); ui.place(content, state, 460, 128, 428, 21);
        title = ui.text("Votre musique, en grand.", 29, Ui.WHITE, true); title.setLetterSpacing(-.025f);
        title.setSingleLine(false); title.setMaxLines(3); title.setGravity(Gravity.TOP); title.setLineSpacing(0, 1.08f);
        ui.place(content, title, 460, 163, 428, 109);
        artist = ui.text("Choisissez un morceau dans Spotify.", 13, 0xFFD3DCDA, false);
        artist.setSingleLine(false); artist.setMaxLines(2); artist.setGravity(Gravity.TOP); ui.place(content, artist, 460, 285, 428, 42);
        timeline = new Timeline(ui); ui.place(content, timeline, 460, 343, 428, 28);
        previous = control(-1, "Morceau précédent"); toggle = control(0, "Lecture"); next = control(1, "Morceau suivant");
        toggleGlyph = (MusicCard.Glyph) ((FrameLayout) toggle).getChildAt(0);
        transport = new LinearLayout(ui.context); transport.setOrientation(LinearLayout.HORIZONTAL); transport.setGravity(Gravity.CENTER_VERTICAL);
        transport.setClipChildren(false); transport.setClipToPadding(false);
        transport.addView(previous, new LinearLayout.LayoutParams(ui.px(44), ui.px(44)));
        transport.addView(new View(ui.context), new LinearLayout.LayoutParams(0, 1, 1));
        transport.addView(toggle, new LinearLayout.LayoutParams(ui.px(56), ui.px(56)));
        transport.addView(new View(ui.context), new LinearLayout.LayoutParams(0, 1, 1));
        transport.addView(next, new LinearLayout.LayoutParams(ui.px(44), ui.px(44)));
        ui.place(content, transport, 460, 380, 428, 56);
        details = new LinearLayout(ui.context) {
            @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                // Text auto-sizing can change a baseline after LinearLayout measured its children.
                if (lyrics != null && source != null && lyrics.getVisibility() == VISIBLE && source.getVisibility() == VISIBLE)
                    source.offsetTopAndBottom(lyrics.getTop() + lyrics.getBaseline() - source.getTop() - source.getBaseline());
            }
        };
        details.setOrientation(LinearLayout.HORIZONTAL); details.setGravity(Gravity.CENTER_VERTICAL);
        details.setBaselineAligned(true); details.setClipChildren(false); details.setClipToPadding(false);
        lyrics = ui.button("Paroles", 10, () -> lyricAction()); lyrics.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        details.addView(lyrics, new LinearLayout.LayoutParams(ui.px(126), ui.px(34))); ui.fit(lyrics);
        source = ui.text("", 8, 0xFFC5D0CA, false); source.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sourceLayout = new LinearLayout.LayoutParams(0, -2, 1); sourceLayout.leftMargin = ui.px(12);
        details.addView(source, sourceLayout); ui.fit(source); source.setVisibility(GONE);
        ui.place(content, details, 54, 474, 380, 34);
        hint = ui.text("La musique continue quand vous quittez cet écran.", 9, 0xFFD0DCD7, false);
        ui.place(content, hint, 54, 512, 380, 17);
        backdropCredit = ui.text("", 7.5f, 0xB8FFFFFF, false); ui.place(content, backdropCredit, 54, 512, 380, 17); backdropCredit.setVisibility(GONE);
        backdrops = new MusicBackdrop(() -> updateBackdrop());
        inlineLyrics = new InlineLyrics(ui, () -> updateLyrics()); ui.place(content, inlineLyrics, 445, 212, 458, 218);
        for (TextView text : new TextView[]{exit, state, title, artist, hint, source, backdropCredit, lyrics})
            text.setShadowLayer(ui.px(1.5f), 0, ui.px(.5f), 0x78000000);
        wireFocus();
    }
    private View control(int action, String description) {
        FrameLayout button = new FrameLayout(ui.context); MusicCard.Glyph glyph = new MusicCard.Glyph(ui, action);
        LayoutParams glyphLayout = new LayoutParams(ui.px(action == 0 ? 24 : 19), ui.px(action == 0 ? 24 : 19), Gravity.CENTER);
        button.addView(glyph, glyphLayout); ui.interactive(button, description, () -> MusicAccessService.command(action));
        button.setBackground(ui.round(0x16FFFFFF, 30, 0x18FFFFFF, .5f));
        new SelectionFeedback(button, 1.08f, ui.px(1), ui.px(4), (selected, focused) -> {
            button.setBackground(ui.round(selected ? Ui.WHITE : 0x16FFFFFF, 30, selected ? 0xFFFFFFFF : 0x18FFFFFF, .5f));
            glyph.color = selected ? Ui.INK : Ui.WHITE; glyph.invalidate();
        }); return button;
    }
    void render(MusicAccessService.Track value) {
        track = value; boolean known = value.session && !value.title.isEmpty();
        title.setText(known ? value.title : "Votre musique, en grand.");
        artist.setText(known ? value.artist : !value.access ? "L’accès Spotify est désactivé dans les réglages." :
            value.session ? "Spotify n’a pas encore partagé les détails du morceau." : "Choisissez un morceau dans Spotify.");
        state.setText(known ? value.playing ? "EN ÉCOUTE" : "EN PAUSE" : "EN ATTENTE");
        if (value.cover != currentCover) { currentCover = value.cover; cover.setImageBitmap(value.cover); }
        backdrops.render(value); updateBackdrop();
        enable(previous, value.canCommand(-1)); enable(toggle, value.canCommand(0)); enable(next, value.canCommand(1));
        inlineLyrics.render(value); updateLyrics();
        placeholder.setVisibility(value.cover == null ? VISIBLE : INVISIBLE);
        cover.setVisibility(value.cover == null ? INVISIBLE : VISIBLE);
        timeline.setVisibility(known && value.duration > 0 ? VISIBLE : INVISIBLE);
        toggleGlyph.paused = !value.playing; toggleGlyph.invalidate(); toggle.setContentDescription(value.playing ? "Pause" : "Lecture");
        wireFocus(); setActive(active); timeline.invalidate();
    }
    private void updateBackdrop() {
        if (track == null || backdrops == null) return;
        MusicBackdrop.Result external = backdrops.result();
        boolean nativeArt = MusicBackdrop.wide(track.banner) || MusicBackdrop.wide(track.cover);
        Bitmap backdrop = MusicBackdrop.wide(track.banner) ? track.banner : MusicBackdrop.wide(track.cover) ? track.cover : external != null ? external.image : track.cover;
        if (backdrop != currentBackdrop) { currentBackdrop = backdrop; background.palette(backdrop); }
        backdropCredit.setText(!nativeArt && external != null ? external.credit : "");
        backdropCredit.setContentDescription(!nativeArt && external != null ? external.credit + ". " + external.page : "");
        backdropCredit.setVisibility(!nativeArt && external != null ? VISIBLE : GONE);
        applyLayout();
    }
    private void lyricAction() { if (inlineLyrics.synced()) inlineLyrics.follow(); else inlineLyrics.retry(); }
    private void updateLyrics() {
        if (inlineLyrics == null) return;
        boolean show = inlineLyrics.hasLyrics(), changed = show != lyricLayout;
        lyricLayout = show; background.reading(show);
        if (!show && inlineLyrics.hasFocus()) focusPrimary();
        inlineLyrics.animate().cancel();
        inlineLyrics.setVisibility(show ? VISIBLE : GONE);
        if (changed && show && ValueAnimator.areAnimatorsEnabled()) {
            inlineLyrics.setAlpha(0); inlineLyrics.animate().alpha(1).setDuration(160)
                .setInterpolator(new android.view.animation.PathInterpolator(.16f,1,.3f,1)).start();
        } else inlineLyrics.setAlpha(1);
        boolean known = track != null && track.session && !track.title.isEmpty() && !track.artist.isEmpty();
        boolean follow = show && inlineLyrics.synced();
        lyrics.setText(show ? follow ? inlineLyrics.following() ? "Suivi actif" : "Recentrer" : "Lecture libre" : inlineLyrics.loading() ? "Recherche…" : "Réessayer paroles");
        lyrics.setContentDescription(show ? "Recentrer les paroles sur le morceau" : "Rechercher les paroles de ce morceau");
        enable(lyrics, known && !inlineLyrics.loading() && (!show || follow));
        lyrics.setVisibility(show && !follow ? GONE : VISIBLE);
        source.setText(show ? follow ? "PAROLES · LRCLIB" : "PAROLES · LRCLIB · NON SYNCHRONISÉES" : "");
        source.setVisibility(show ? VISIBLE : GONE);
        if (!show) hint.setText(inlineLyrics.status().isEmpty() ? "La musique continue quand vous quittez cet écran." : inlineLyrics.status());
        applyLayout();
    }
    private void applyLayout() {
        boolean wide = !lyricLayout && MusicBackdrop.wide(currentBackdrop);
        int mode = lyricLayout ? 2 : wide ? 1 : 0;
        if (mode != layoutMode) {
            layoutMode = mode;
            position(details, 54, 474, 380, 34);
            if (lyricLayout) {
                position(sleeve, 54, 80, 380, 380);
                position(state, 460, 78, 428, 18); position(title, 460, 107, 428, 63); title.setMaxLines(2);
                position(artist, 460, 182, 428, 23); artist.setMaxLines(1);
                position(timeline, 460, 442, 428, 28); position(transport, 460, 474, 428, 56); hint.setVisibility(INVISIBLE);
            } else {
                position(sleeve, 54, wide ? 350 : 80, wide ? 88 : 380, wide ? 88 : 380);
                position(state, wide ? 160 : 460, wide ? 314 : 128, wide ? 728 : 428, wide ? 26 : 21);
                position(title, wide ? 160 : 460, wide ? 350 : 163, wide ? 728 : 428, wide ? 57 : 109); title.setMaxLines(wide ? 2 : 3);
                position(artist, wide ? 160 : 460, wide ? 413 : 285, wide ? 728 : 428, wide ? 23 : 42); artist.setMaxLines(wide ? 1 : 2);
                position(timeline, 460, wide ? 442 : 343, 428, 28); position(transport, 460, wide ? 474 : 380, 428, 56);
                hint.setVisibility(wide ? INVISIBLE : VISIBLE);
            }
        }
        wireFocus();
    }
    private View primaryControl() {
        // The primary escape is always a media command or Close, never a lyric
        // action which can immediately send the user back into the reader.
        for (View button : new View[]{toggle, previous, next}) if (button.isEnabled()) return button;
        return exit;
    }
    private void wireFocus() {
        View primary = primaryControl(), reading = inlineLyrics.scroll;
        boolean hasLyricAction = lyrics.isEnabled() && lyrics.getVisibility() == VISIBLE;
        View lower = hasLyricAction ? lyrics : exit;
        exit.setNextFocusDownId(lyricLayout ? reading.getId() : primary.getId());
        exit.setNextFocusLeftId(primary.getId()); exit.setNextFocusRightId(exit.getId()); exit.setNextFocusUpId(exit.getId());
        reading.setNextFocusUpId(exit.getId()); reading.setNextFocusDownId(primary.getId());
        reading.setNextFocusLeftId(primary.getId()); reading.setNextFocusRightId(primary.getId());
        previous.setNextFocusLeftId(hasLyricAction ? lyrics.getId() : previous.getId());
        previous.setNextFocusRightId(toggle.isEnabled() ? toggle.getId() : next.isEnabled() ? next.getId() : primary.getId());
        toggle.setNextFocusLeftId(previous.isEnabled() ? previous.getId() : lower.getId());
        toggle.setNextFocusRightId(next.isEnabled() ? next.getId() : exit.getId());
        next.setNextFocusLeftId(toggle.isEnabled() ? toggle.getId() : previous.isEnabled() ? previous.getId() : primary.getId());
        next.setNextFocusRightId(exit.getId());
        for (View button : new View[]{previous, toggle, next}) {
            button.setNextFocusUpId(lyricLayout ? reading.getId() : exit.getId()); button.setNextFocusDownId(lower.getId());
        }
        lyrics.setNextFocusUpId(exit.getId()); lyrics.setNextFocusDownId(primary.getId());
        lyrics.setNextFocusLeftId(primary.getId()); lyrics.setNextFocusRightId(lyricLayout ? reading.getId() : primary.getId());
    }
    private void position(View view, float x, float y, float width, float height) {
        LayoutParams layout = (LayoutParams) view.getLayoutParams();
        layout.leftMargin = ui.px(x); layout.topMargin = ui.px(y); layout.width = ui.px(width); layout.height = ui.px(height); view.setLayoutParams(layout);
    }
    private void enable(View button, boolean value) {
        boolean relocate = !value && button.hasFocus();
        button.setEnabled(value); button.setFocusable(value); button.setAlpha(value ? 1 : .25f);
        if (relocate) focusPrimary();
    }
    void focusPrimary() { primaryControl().requestFocus(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && inlineLyrics.scroll.hasFocus()) {
            if (event.getAction() == KeyEvent.ACTION_UP) focusPrimary();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    void setActive(boolean value) {
        active = value; handler.removeCallbacks(tick);
        if (value && track != null && track.playing) handler.postDelayed(tick, 1000);
        inlineLyrics.setActive(value); backdrops.setActive(value);
        if (!value) { inlineLyrics.animate().cancel(); inlineLyrics.setAlpha(1); background.stop(); }
    }
    void close() { setActive(false); inlineLyrics.close(); backdrops.close(); }
    @Override protected void onDetachedFromWindow() { close(); super.onDetachedFromWindow(); }

    private final class Timeline extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Timeline(Ui ui) { super(ui.context); paint.setTypeface(ui.body); paint.setTextSize(ui.px(8.5f)); }
        @Override protected void onDraw(Canvas canvas) {
            float y = ui.px(2), radius = ui.px(1.5f);
            paint.setColor(0x35FFFFFF); canvas.drawRoundRect(0, 0, getWidth(), y, radius, radius, paint);
            paint.setColor(0xFFE9F0EB); canvas.drawRoundRect(0, 0, getWidth() * (track == null ? 0 : track.progress()), y, radius, radius, paint);
            if (track == null) return;
            paint.setColor(0xFFC5D0CA); paint.setTextAlign(Paint.Align.LEFT); canvas.drawText(duration(track.positionMs()), 0, ui.px(22), paint);
            paint.setTextAlign(Paint.Align.RIGHT); canvas.drawText(duration(track.duration), getWidth(), ui.px(22), paint);
        }
    }
    static String duration(long milliseconds) {
        long seconds = Math.max(0, milliseconds / 1000);
        return seconds >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60) :
            String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
    static int albumColor(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return 0xFF42675C;
        int[] count = new int[512], red = new int[512], green = new int[512], blue = new int[512];
        try {
            for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) {
                int c = bitmap.getPixel(Math.min(bitmap.getWidth() - 1, x * bitmap.getWidth() / 20), Math.min(bitmap.getHeight() - 1, y * bitmap.getHeight() / 20));
                int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
                if ((c >>> 24) < 200 || Math.max(r, Math.max(g, b)) < 30 || Math.min(r, Math.min(g, b)) > 235) continue;
                int bin = (r >> 5) << 6 | (g >> 5) << 3 | b >> 5;
                count[bin]++; red[bin] += r; green[bin] += g; blue[bin] += b;
            }
        } catch (RuntimeException error) { return 0xFF42675C; }
        int best = -1; float score = -1;
        for (int i = 0; i < count.length; i++) if (count[i] > 0) {
            int r = red[i] / count[i], g = green[i] / count[i], b = blue[i] / count[i];
            float saturation = (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b))) / 255f;
            float value = count[i] * (.35f + saturation);
            if (value > score) { score = value; best = i; }
        }
        return best < 0 ? 0xFF42675C : 0xFF000000 | (red[best] / count[best]) << 16 | (green[best] / count[best]) << 8 | blue[best] / count[best];
    }
    static final class Atmosphere extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private int color = 0xFF42675C;
        private ValueAnimator animation;
        private Shader base, textShade;
        private Bitmap ambient, hero, previous;
        private float blend = 1;
        private boolean initialized, reading;
        private final RectF image = new RectF();
        Atmosphere(Ui ui) { super(ui.context); }
        void palette(Bitmap bitmap) {
            Bitmap visible = null;
            boolean animate = initialized && ValueAnimator.areAnimatorsEnabled() && isAttachedToWindow() && getWidth() > 0 && getHeight() > 0;
            if (animate) {
                int width = Math.min(1280, getWidth()), height = Math.max(1, Math.round(getHeight() * width / (float) getWidth()));
                visible = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas snapshot = new Canvas(visible); snapshot.scale(width / (float)getWidth(), height / (float)getHeight()); drawBackground(snapshot);
            }
            cancelAnimation(); releasePrevious(); previous = visible;
            if (ambient != null) { ambient.recycle(); ambient = null; }
            hero = bitmap != null && bitmap.getWidth() > bitmap.getHeight() * 1.35f ? bitmap : null;
            if (bitmap != null && !bitmap.isRecycled() && hero == null) {
                Bitmap sample = Bitmap.createScaledBitmap(bitmap, 24, 24, true);
                int[] pixels = new int[576], blurred = new int[576]; sample.getPixels(pixels, 0, 24, 0, 0, 24, 24);
                for (int pass = 0; pass < 4; pass++) {
                    for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) {
                        int red = 0, green = 0, blue = 0;
                        for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) {
                            int c = pixels[Math.max(0, Math.min(23, y + dy)) * 24 + Math.max(0, Math.min(23, x + dx))];
                            red += c >> 16 & 255; green += c >> 8 & 255; blue += c & 255;
                        }
                        blurred[y * 24 + x] = 0xFF000000 | red / 25 << 16 | green / 25 << 8 | blue / 25;
                    }
                    int[] swap = pixels; pixels = blurred; blurred = swap;
                }
                ambient = Bitmap.createBitmap(pixels, 24, 24, Bitmap.Config.ARGB_8888);
                if (sample != bitmap) sample.recycle();
            }
            color = albumColor(bitmap); initialized = true; blend = previous == null ? 1 : 0; shaders();
            if (previous == null) return;
            ValueAnimator transition = ValueAnimator.ofFloat(0, 1); animation = transition; transition.setDuration(420);
            transition.setInterpolator(new android.view.animation.PathInterpolator(.2f, 0, .2f, 1));
            transition.addUpdateListener(value -> { blend = (float)value.getAnimatedValue(); if (blend >= 1) releasePrevious(); invalidate(); });
            transition.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animator) { if (animation == animator) { animation = null; blend = 1; releasePrevious(); invalidate(); } }
            }); transition.start();
        }

        void reading(boolean value) { if (reading != value) { reading = value; shaders(); } }
        private void shaders() {
            if (getWidth() == 0 || getHeight() == 0) return;
            base = new LinearGradient(0, 0, getWidth(), getHeight(), ColorTone.mix(color, 0xFF060B10, .22f), ColorTone.mix(color, 0xFF080D13, .38f), Shader.TileMode.CLAMP);
            textShade = hero != null && reading ? new LinearGradient(getWidth() * .42f, 0, getWidth(), 0,
                new int[]{0x18000000, 0xBC000000}, null, Shader.TileMode.CLAMP) : hero != null ? new LinearGradient(0, getHeight() * .45f, 0, getHeight(),
                new int[]{0x00000000, 0xB3000000}, null, Shader.TileMode.CLAMP) :
                new RadialGradient(getWidth() * .72f, getHeight() * .55f, getWidth() * .55f,
                new int[]{0xB8000000, 0x40000000, 0x10000000}, new float[]{0, .6f, 1}, Shader.TileMode.CLAMP); invalidate();
        }
        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { shaders(); }
        @Override protected void onDraw(Canvas canvas) { drawBackground(canvas); }
        private void drawBackground(Canvas canvas) {
            if (previous != null && !previous.isRecycled()) {
                paint.setShader(null); paint.setAlpha(255); image.set(0, 0, getWidth(), getHeight()); canvas.drawBitmap(previous, null, image, paint);
                if (blend <= 0) return;
            }
            int layer = previous == null ? -1 : canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), Math.round(blend * 255));
            drawCurrent(canvas); if (layer >= 0) canvas.restoreToCount(layer);
        }
        private void drawCurrent(Canvas canvas) {
            if (base == null) { canvas.drawColor(0xFF101E20); return; }
            paint.setShader(base); canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(null);
            if (ambient != null) { image.set(0, 0, getWidth(), getHeight()); canvas.drawBitmap(ambient, null, image, paint); }
            if (hero != null && !hero.isRecycled()) {
                float scale = Math.max(getWidth() / (float)hero.getWidth(), getHeight() / (float)hero.getHeight());
                float w = hero.getWidth() * scale, h = hero.getHeight() * scale;
                image.set((getWidth() - w) / 2, (getHeight() - h) / 2, (getWidth() + w) / 2, (getHeight() + h) / 2);
                canvas.drawBitmap(hero, null, image, paint);
            }
            paint.setShader(textShade); canvas.drawRect(0, 0, getWidth(), getHeight(), paint); paint.setShader(null);
        }
        private void cancelAnimation() { ValueAnimator old = animation; animation = null; if (old != null) old.cancel(); }
        private void releasePrevious() { if (previous != null) { previous.recycle(); previous = null; } }
        void stop() { cancelAnimation(); blend = 1; releasePrevious(); invalidate(); }
        @Override protected void onDetachedFromWindow() {
            stop(); if (ambient != null) { ambient.recycle(); ambient = null; } hero = null; initialized = false; super.onDetachedFromWindow();
        }
    }
}
