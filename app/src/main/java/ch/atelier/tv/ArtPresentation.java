package ch.atelier.tv;

import android.content.Context;
import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ArtPresentation {
    final FrameLayout view;
    private final ArtStore store;
    private final ArtView canvas;
    private final AdaptiveContrast contrast;
    private final ArtworkTextTone timeTone;
    private final LinearLayout identity, time;
    private final TextView title, credit, date;
    private final MinuteClock clock;
    private final Ui ui;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private List<ArtStore.Art> works;
    private int index, generation;
    private boolean stopped, timePositioned, tonePending;
    private final Runnable advance = () -> next(1);
    private final Runnable minute = this::updateTime;
    private final Runnable recolorTime = this::recolorTime;

    ArtPresentation(Context context) {
        store = new ArtStore(context); ui = new Ui(context, false);
        view = new FrameLayout(context); canvas = new ArtView(context);
        canvas.setCover(store.prefs.getBoolean("art_cover", true));
        view.addView(canvas, new FrameLayout.LayoutParams(-1, -1));
        contrast = new AdaptiveContrast(ui, canvas); view.addView(contrast, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout content = SafeArea.attach(view, ui);
        identity = new LinearLayout(context); identity.setOrientation(LinearLayout.VERTICAL);
        credit = ui.text("", 7.5f, 0xD8DDE2E2, false); credit.setLetterSpacing(.09f);
        LinearLayout.LayoutParams creditLayout = new LinearLayout.LayoutParams(-1, -2);
        identity.addView(credit, creditLayout);
        title = ui.text("", 15, Ui.WHITE, false); title.setLetterSpacing(-.02f);
        title.setGravity(Gravity.LEFT | Gravity.TOP); title.setSingleLine(false); title.setMaxLines(2); title.setLineSpacing(0, 1.12f);
        LinearLayout.LayoutParams titleLayout = new LinearLayout.LayoutParams(-1, -2); titleLayout.topMargin = ui.px(6);
        identity.addView(title, titleLayout);
        FrameLayout.LayoutParams identityLayout = new FrameLayout.LayoutParams(ui.px(470), -2, Gravity.LEFT | Gravity.BOTTOM);
        identityLayout.leftMargin = ui.px(24); identityLayout.bottomMargin = ui.px(20);
        content.addView(identity, identityLayout);
        identity.setVisibility(store.prefs.getBoolean("dream_caption", true) ? View.VISIBLE : View.GONE);

        time = new LinearLayout(context); time.setOrientation(LinearLayout.VERTICAL); time.setGravity(Gravity.RIGHT); time.setClipChildren(false); time.setClipToPadding(false);
        clock = new MinuteClock(ui); clock.setFormat12Hour("HH:mm"); clock.setFormat24Hour("HH:mm");
        android.graphics.Typeface timeFont = new android.graphics.Typeface.Builder(context.getAssets(), "fonts/manrope.ttf").setFontVariationSettings("'wght' 500").setWeight(500).build();
        clock.setTypeface(timeFont); clock.setTextColor(Ui.WHITE); clock.setIncludeFontPadding(false);
        clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, ui.px(52)); clock.setLetterSpacing(-.04f);
        clock.setGravity(Gravity.RIGHT); time.addView(clock, new LinearLayout.LayoutParams(-1, -2));
        clock.setVisibility(store.prefs.getBoolean("dream_clock", true) ? View.VISIBLE : View.GONE);
        date = ui.text("", 10.5f, 0xFFDDE2E2, false); date.setGravity(Gravity.RIGHT); date.setPadding(0, 0, ui.px(1), 0);
        date.setTypeface(timeFont);
        LinearLayout.LayoutParams dateLayout = new LinearLayout.LayoutParams(-1, -2); dateLayout.topMargin = ui.px(8);
        time.addView(date, dateLayout); date.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> alignDate()); date.setVisibility(store.prefs.getBoolean("dream_date", true) ? View.VISIBLE : View.GONE);
        FrameLayout.LayoutParams timeLayout = new FrameLayout.LayoutParams(ui.px(260), -2, Gravity.RIGHT | Gravity.TOP);
        timeLayout.rightMargin = ui.px(52); timeLayout.topMargin = ui.px(44); content.addView(time, timeLayout);
        for (TextView text : new TextView[]{title, credit}) text.setShadowLayer(ui.px(2), 0, ui.px(.5f), 0x80000000);
        clock.setShadowLayer(0,0,0,0); date.setShadowLayer(0,0,0,0);
        contrast.follow(title, credit);
        timeTone = new ArtworkTextTone(canvas,clock,date).unify(clock,date);
        view.getViewTreeObserver().addOnGlobalLayoutListener(() -> { contrast.refresh(); if(!tonePending)timeTone.refresh(); });
        updateTime();
        works = store.list();
        if (store.prefs.getBoolean("dream_personal", false)) {
            List<ArtStore.Art> personal = new ArrayList<>(); for (ArtStore.Art art : works) if (art.personal()) personal.add(art);
            if (!personal.isEmpty()) works = personal;
        }
        ArtStore.Art selected = store.selected();
        for (int i = 0; i < works.size(); i++) if (selected != null && works.get(i).id.equals(selected.id)) index = i;
        display();
    }

    void next(int direction) {
        if (stopped || works.isEmpty()) return;
        index = (index + direction + works.size()) % works.size(); display();
    }

    private void display() {
        handler.removeCallbacks(advance); if (works.isEmpty() || stopped) return;
        ArtStore.Art art = works.get(index); final int request = ++generation;
        worker.execute(() -> {
            try {
                android.graphics.Bitmap bitmap = store.prefs.getBoolean("art_cover", true)
                    ? store.decodeCover(art, 2560) : store.decode(art, 2560);
                handler.post(() -> {
                    if (stopped || request != generation) { bitmap.recycle(); return; }
                    boolean transition = canvas.currentImage() != null;
                    canvas.setImage(bitmap, transition); title.setText(art.title); credit.setText(art.credit().toUpperCase(Locale.FRENCH));
                    identity.setContentDescription(art.title + ", " + art.credit()); contrast.artworkChanged();
                    handler.removeCallbacks(recolorTime);
                    tonePending=transition && ValueAnimator.areAnimatorsEnabled();
                    if(tonePending)handler.postDelayed(recolorTime,350);else timeTone.refresh();
                    revealIdentity();
                });
            } catch (Exception error) { android.util.Log.w("AtelierTV", "Cannot display artwork", error); }
        });
        int seconds = Math.max(30, Math.min(600, store.prefs.getInt("slideshow_seconds", 60)));
        handler.postDelayed(advance, seconds * 1000L);
    }

    private void revealIdentity() {
        for (View item : new View[]{credit, title}) { item.animate().cancel(); item.setAlpha(1); item.setTranslationY(0); item.setScaleX(1); }
        if (!ValueAnimator.areAnimatorsEnabled() || identity.getVisibility() != View.VISIBLE) return;
        android.view.animation.PathInterpolator easing = new android.view.animation.PathInterpolator(.16f, 1f, .3f, 1f);
        credit.setAlpha(0); credit.setTranslationY(ui.px(3)); credit.animate().alpha(1).translationY(0).setStartDelay(0).setDuration(220).setInterpolator(easing).start();
        title.setAlpha(0); title.setTranslationY(ui.px(6)); title.animate().alpha(1).translationY(0).setStartDelay(35).setDuration(260).setInterpolator(easing).start();
    }

    private void updateTime() {
        if (stopped) return;
        long now = System.currentTimeMillis();
        date.setText(new SimpleDateFormat("EEEE d MMMM", Locale.FRENCH).format(new Date(now)));
        alignDate();
        // Tiny minute-by-minute offsets keep the composition calm without a fixed overlay.
        int phase = (int) ((now / 60000L) % 8);
        int[] x = {-3, 0, 3, 4, 3, 0, -3, -4}, y = {-2, -3, -2, 0, 2, 3, 2, 0};
        if (timePositioned && view.isShown() && ValueAnimator.areAnimatorsEnabled()) {
            android.view.animation.PathInterpolator easing = new android.view.animation.PathInterpolator(.16f, 1f, .3f, 1f);
            time.animate().cancel(); identity.animate().cancel();
            time.animate().translationX(ui.px(x[phase])).translationY(ui.px(y[phase])).setDuration(360).setInterpolator(easing).start();
            identity.animate().translationX(ui.px(-x[phase])).translationY(ui.px(-y[phase])).setDuration(360).setInterpolator(easing).start();
        } else {
            time.setTranslationX(ui.px(x[phase])); time.setTranslationY(ui.px(y[phase]));
            identity.setTranslationX(ui.px(-x[phase])); identity.setTranslationY(ui.px(-y[phase]));
        }
        timePositioned = true;
        contrast.refresh(); timeTone.refresh(); handler.postDelayed(recolorTime,400); handler.postDelayed(minute, 60000L - now % 60000L + 50);
    }

    private void recolorTime() { tonePending=false; if(!stopped)timeTone.refresh(); }

    private void alignDate() {
        String value = date.getText().toString(); if (value.isEmpty()) return;
        android.graphics.Rect ink = new android.graphics.Rect();
        date.getPaint().getTextBounds(value, 0, value.length(), ink);
        date.setTranslationX(date.getPaint().measureText(value) - ink.right);
    }

    void stop() {
        if (stopped) return;
        stopped = true; timeTone.close(); generation++; handler.removeCallbacksAndMessages(null);
        identity.animate().cancel(); time.animate().cancel(); clock.stop(); credit.animate().cancel(); title.animate().cancel(); view.setVisibility(View.GONE); worker.shutdownNow(); canvas.setImage(null);
    }

}
