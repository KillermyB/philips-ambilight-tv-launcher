package ch.atelier.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Apps.App> apps = new ArrayList<>();
    private final List<View> favoriteViews = new ArrayList<>();
    private final List<String> favorites = new ArrayList<>();
    private Ui ui;
    private boolean cleanTheme;
    private CleanFrameHome cleanHome;
    private ArtStore art;
    private SharedPreferences prefs;
    private FrameLayout root, home, homeContent, panel, favoritesRow;
    private LinearLayout quotation;
    private TextView quoteText, quoteCredit, homeNav, settingsNav, date, artTitle, note, noteLabel, panelCount;
    private final List<View> navigation = new ArrayList<>();
    private Wisdom wisdom;
    private TextClock clock;
    private ArtView wallpaper;
    private AdaptiveContrast contrast;
    private WelcomeGreeting greeting;
    private HomeEntrance entrance;
    private HomeSlideshow slideshow;
    private Weather weather;
    private WeatherView weatherView;
    private News news;
    private NewsView newsView;
    private List<News.Item> headlines = new ArrayList<>();
    private String newsStatus = "Chargement des actualités…";
    private LinearLayout newsList;
    private FocusScrollView newsScroll;
    private View newsSources;
    private TextView newsSubtitle;
    private MusicCard musicCard;
    private ArtworkTextTone homeTone;
    private ListeningView listening;
    private ControlCenter controlCenter;
    private Dialog controlDialog;
    private View artworkFocus;
    private ArtStore.Art currentArtwork;
    private MusicAccessService.Track musicTrack = new MusicAccessService.Track(false, null, null);
    private final MusicAccessService.Listener musicListener = value -> { musicTrack = value; refreshMusic(); };
    private int weatherSearch;
    private View lastHomeFocus, panelClose;
    private ArtTransfer transfer;
    private Dialog activeDialog;
    private boolean destroyed, loading, importing, resumed, applyingFrame, advanceAfterDialog;
    private int panelRequest;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); ui = new Ui(this); art = new ArtStore(this); prefs = art.prefs; prefs.edit().remove("home_soft_blur").apply(); cleanTheme = "cleanframe".equals(prefs.getString("ui_theme", "atelier")); wisdom = new Wisdom(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        createHome(); entrance = new HomeEntrance(ui, cleanHome == null ? homeContent : cleanHome.content);
        weather = new Weather(prefs, report -> weatherView.render(prefs.getString("weather_city", "Moudon"), report));
        news = new News(prefs, (items, status) -> { headlines = items; newsStatus = status; newsView.update(items, status); renderNewsRows(); });
        slideshow = new HomeSlideshow(art, (work, bitmap, advanceQuote, animate) -> {
            applyingFrame = true;
            SharedPreferences.Editor edit = prefs.edit().putString("art", work.id);
            if (advanceQuote) edit.putInt("quote_offset", prefs.getInt("quote_offset", 0) + 1);
            edit.apply(); applyingFrame = false;
            currentArtwork = work; wallpaper.setImage(bitmap, animate); artTitle.setText(cleanTheme ? work.title : work.title + " · " + work.credit());
            artTitle.setContentDescription(work.title + ", " + work.credit() + ". OK pour le cartel.");
            artworkFocus.setContentDescription(work.title + ", " + work.credit() + ". Appui long sur OK pour le cartel."); refreshWidgets();
            greeting.artworkReady(); entrance.artworkReady();
            if (cleanHome != null) cleanHome.artworkChanged(); else { contrast.artworkChanged(); if(homeTone!=null)homeTone.artworkChanged(); }
        });
        prefs.registerOnSharedPreferenceChangeListener(this);
        refreshWidgets(); loadApps();
    }

    @Override protected void onResume() {
        super.onResume(); resumed = true; MusicAccessService.observe(this, musicListener); refreshWidgets(); updateSlideshowVisibility();
        if (lastHomeFocus != null && panel == null && lastHomeFocus.isAttachedToWindow() && lastHomeFocus.isShown()) lastHomeFocus.requestFocus();
        if (importing && transfer == null) showTransfer();
    }
    @Override protected void onPause() { resumed = false; if (cleanHome != null) cleanHome.closeArtwork(false); weatherSearch++; MusicAccessService.forget(musicListener); updateSlideshowVisibility(); super.onPause(); }
    @Override protected void onStop() { stopTransfer(); super.onStop(); }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); weatherSearch++; if (cleanHome != null) cleanHome.closeArtwork(false);
        if (activeDialog != null) activeDialog.dismiss();
        closePanel(); focusFavorites(); updateSlideshowVisibility();
    }

    private void createHome() {
        root = new FrameLayout(this); home = new FrameLayout(this);
        root.addView(home, new FrameLayout.LayoutParams(-1, -1)); setContentView(root);
        home.setClipChildren(false); home.setClipToPadding(false);
        wallpaper = new ArtView(this); wallpaper.setCover(true);
        home.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));
        contrast = new AdaptiveContrast(ui, wallpaper); home.addView(contrast, new FrameLayout.LayoutParams(-1, -1));
        homeContent = SafeArea.attach(home, ui);
        TextView accueil = ui.navigation("Accueil", 11, () -> focusFavorites());
        homeNav = accueil;
        TextView applications = ui.navigation("Applications", 11, () -> showApps());
        TextView gallery = ui.navigation("Galerie", 11, () -> showGallery());
        TextView settings = ui.navigation("Réglages", 11, () -> showSettings());
        settingsNav = settings;
        TextView control = ui.navigation("Contrôle", 11, () -> showControlCenter()); control.setContentDescription("Centre de contrôle");
        ui.place(homeContent, accueil, 41, 36, 79, 31);
        ui.place(homeContent, applications, 127, 36, 107, 31);
        ui.place(homeContent, gallery, 241, 36, 78, 31);
        ui.place(homeContent, control, 326, 36, 79, 31);
        ui.place(homeContent, settings, 412, 36, 87, 31);
        clock = new TextClock(this); clock.setFormat12Hour("HH:mm"); clock.setFormat24Hour("HH:mm");
        clock.setTypeface(ui.body); clock.setTextColor(Ui.WHITE);
        clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, ui.px(36)); clock.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        ui.place(homeContent, clock, 732, 18, 180, 57);
        date = ui.text("", 10, 0xFFE3E7E6, false); date.setGravity(Gravity.RIGHT);
        ui.place(homeContent, date, 727, 82, 185, 21);
        weatherView = new WeatherView(ui); ui.place(homeContent, weatherView, 807, 108, 105, 22);

        quotation = new LinearLayout(this); quotation.setOrientation(LinearLayout.VERTICAL);
        artworkFocus = quotation;
        ui.interactive(quotation, "Citation philosophique. OK pour une autre citation, appui long pour le cartel.", () -> prefs.edit().putInt("quote_offset", prefs.getInt("quote_offset", 0) + 1).apply());
        quotation.setOnLongClickListener(view -> { showArtworkInfo(); return true; });
        ui.place(homeContent, quotation, 50, 211, 530, -2);
        quoteText = ui.text("", 18, Ui.WHITE, false);
        quoteText.setSingleLine(false); quoteText.setMaxLines(2); quoteText.setLineSpacing(0, 1.18f);
        quoteText.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
        quoteText.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        quotation.addView(quoteText, new LinearLayout.LayoutParams(-1, -2));
        quoteCredit = ui.text("", 9.5f, 0xFFE3E7E6, false);
        LinearLayout.LayoutParams creditLayout = new LinearLayout.LayoutParams(-1, ui.px(21));
        creditLayout.topMargin = ui.px(15); quotation.addView(quoteCredit, creditLayout); ui.fit(quoteCredit);

        noteLabel = ui.text("VOTRE MESSAGE", 8, 0xFFD0D8C9, true); noteLabel.setLetterSpacing(.14f);
        ui.place(homeContent, noteLabel, 655, 252, 250, 18);
        note = ui.text("", 15, Ui.WHITE, false);
        note.setSingleLine(false); note.setMaxLines(3); note.setLineSpacing(0, 1.12f);
        ui.place(homeContent, note, 654, 277, 250, 85);
        artTitle = ui.text("", 9, 0xFFE3E7E6, false); artTitle.setGravity(Gravity.RIGHT | Gravity.TOP);
        artTitle.setSingleLine(false); artTitle.setMaxLines(2); artTitle.setLineSpacing(0, 1.2f);
        ui.interactive(artTitle, "Informations sur le tableau", () -> showArtworkInfo());
        artTitle.setOnLongClickListener(view -> { showArtworkInfo(); return true; });
        for (View target : new View[]{quotation, artTitle}) new SelectionFeedback(target, 1f, 0, 0, (active, focused) -> {
            artTitle.setPaintFlags(active ? artTitle.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG : artTitle.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
            artTitle.setVisibility(active || prefs.getBoolean("widget_art", true) ? View.VISIBLE : View.INVISIBLE);
        });
        musicCard = new MusicCard(ui, () -> showListening()); ui.place(homeContent, musicCard, 912-MusicCard.WIDTH, 145, MusicCard.WIDTH, MusicCard.HEIGHT);
        LinearLayout footer = new LinearLayout(this); footer.setGravity(Gravity.TOP); footer.setBaselineAligned(true); footer.setClipChildren(false); footer.setClipToPadding(false);
        newsView = new NewsView(ui, () -> showNews());
        if (quoteInFooter()) {
            homeContent.removeView(quotation); quotation.setPadding(ui.px(9), 0, ui.px(9), ui.px(4)); quotation.setBaselineAlignedChildIndex(0);
            LinearLayout.LayoutParams credit = (LinearLayout.LayoutParams)quoteCredit.getLayoutParams(); credit.topMargin = ui.px(4); credit.height = ui.px(16); quoteCredit.setLayoutParams(credit);
            quoteCredit.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, ui.px(8.5f)); ui.fit(quoteCredit);
            footer.addView(quotation, new LinearLayout.LayoutParams(0, -2, .62f));
        } else footer.addView(newsView, new LinearLayout.LayoutParams(0, -2, .62f));
        LinearLayout.LayoutParams artworkCaption = new LinearLayout.LayoutParams(0, -2, .38f); artworkCaption.leftMargin = ui.px(26); footer.addView(artTitle, artworkCaption);
        FrameLayout.LayoutParams footerLayout = new FrameLayout.LayoutParams(ui.px(870), -2);
        footerLayout.leftMargin = ui.px(41); footerLayout.gravity = Gravity.BOTTOM; footerLayout.bottomMargin = ui.px(10); homeContent.addView(footer, footerLayout);
        for (TextView text : Arrays.asList(quoteText, quoteCredit, clock, date, note, noteLabel, artTitle))
            text.setShadowLayer(ui.px(1.5f), 0, ui.px(.5f), 0x78000000);

        favoritesRow = new FrameLayout(this); favoritesRow.setClipChildren(false); favoritesRow.setClipToPadding(false);
        ui.place(homeContent, favoritesRow, 50, 387, 864, 78);
        navigation.addAll(Arrays.asList(accueil, applications, gallery, control, settings));
        greeting = new WelcomeGreeting(ui, prefs, cleanTheme);
        FrameLayout.LayoutParams greetingLayout = new FrameLayout.LayoutParams(-2, -2);
        greetingLayout.leftMargin = ui.px(50); greetingLayout.topMargin = ui.px(106);
        homeContent.addView(greeting.view, greetingLayout);
        for (View text : navigation) contrast.follow(text);
        contrast.follow(clock, date, weatherView, quotation, artTitle, newsView, note, noteLabel, greeting.view);
        home.getViewTreeObserver().addOnGlobalLayoutListener(this::refreshHomeContrast);
        if (cleanTheme) {
            homeContent.setVisibility(View.INVISIBLE); contrast.setVisibility(View.GONE);
            cleanHome = new CleanFrameHome(ui, prefs, wallpaper, musicCard, artTitle, weatherView, quoteInFooter() ? quotation : null, new CleanFrameHome.Actions() {
                @Override public void gallery() { showGallery(); }
                @Override public void applications() { showApps(); }
                @Override public void control() { showControlCenter(); }
                @Override public void settings() { showSettings(); }
                @Override public void launch(Apps.App app) { MainActivity.this.launch(app); }
                @Override public void manage(Apps.App app) { showFavoriteActions(app, null); }
                @Override public void artworkPanel(boolean showing) { updateSlideshowVisibility(); refreshMusic(); }
            });
            home.addView(cleanHome, new FrameLayout.LayoutParams(-1, -1)); clock = cleanHome.clock; cleanHome.attachGreeting(greeting.view);
        }
        if(!cleanTheme){java.util.ArrayList<View> toneTargets=new java.util.ArrayList<>(navigation);toneTargets.addAll(Arrays.asList(clock,date,weatherView,quotation,artTitle,note,noteLabel));homeTone=new ArtworkTextTone(wallpaper,toneTargets.toArray(new View[0])).unify(navigation.toArray(new View[0])).unify(clock,date,weatherView).unify(quotation,artTitle).unify(noteLabel,note);}
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((previous, focused) -> {
            home.post(this::refreshHomeContrast);
            if (panel == null && focused != null) lastHomeFocus = focused;
        });
    }

    private void loadApps() {
        if (loading) return; loading = true;
        worker.execute(() -> {
            List<Apps.App> result = Apps.load(getApplicationContext());
            runOnUiThread(() -> {
                if (destroyed) return; loading = false; apps.clear(); apps.addAll(result);
                restoreFavorites(); renderFavorites(); android.util.Log.i("AtelierTV", "Ready: " + apps.size() + " apps");
            });
        });
    }

    private void restoreFavorites() {
        favorites.clear(); String saved = prefs.getString("favorites", null);
        if (saved != null) try {
            JSONArray values = new JSONArray(saved);
            for (int i = 0; i < values.length() && favorites.size() < 6; i++) {
                String pkg = values.getString(i); if (findApp(pkg) != null && !favorites.contains(pkg)) favorites.add(pkg);
            }
        } catch (Exception ignored) { }
        if (favorites.isEmpty()) for (String pkg : Arrays.asList("com.google.android.youtube.tv", "com.netflix.ninja", "com.spotify.tv.android",
            "com.stremio.one", "com.canal.android.canal", "com.amazon.amazonvideo.livingroom")) if (findApp(pkg) != null) favorites.add(pkg);
        // Apply the explicitly requested order once, preserving later user edits.
        if (!prefs.getBoolean("layout_v2", false)) {
            for (String pkg : Arrays.asList("com.google.android.youtube.tv", "com.spotify.tv.android")) favorites.remove(pkg);
            if (findApp("com.google.android.youtube.tv") != null) favorites.add(0, "com.google.android.youtube.tv");
            if (findApp("com.spotify.tv.android") != null) favorites.add(Math.min(2, favorites.size()), "com.spotify.tv.android");
            while (favorites.size() > 6) favorites.remove(favorites.size() - 1);
            prefs.edit().putBoolean("layout_v2", true).putString("favorites", new JSONArray(favorites).toString()).apply();
        }
        if (favorites.isEmpty() && !apps.isEmpty()) favorites.add(apps.get(0).pkg);
        if (!prefs.getBoolean("favorites_prime_v1", false) && findApp("com.amazon.amazonvideo.livingroom") != null) {
            int apple = favorites.indexOf("com.apple.atve.androidtv.appletv");
            favorites.remove("com.amazon.amazonvideo.livingroom");
            if (apple >= 0) favorites.remove("com.apple.atve.androidtv.appletv");
            if (favorites.size() >= 6) favorites.remove(favorites.size() - 1);
            favorites.add(apple < 0 ? favorites.size() : Math.min(apple, favorites.size()), "com.amazon.amazonvideo.livingroom");
            prefs.edit().putBoolean("favorites_prime_v1", true).apply(); saveFavorites();
        }
        if (!prefs.getBoolean("favorites_order_v0130", false)) {
            List<String> first = Arrays.asList("com.google.android.youtube.tv", "com.netflix.ninja", "com.spotify.tv.android");
            favorites.removeAll(first); int position = 0;
            for (String pkg : first) if (findApp(pkg) != null) favorites.add(position++, pkg);
            while (favorites.size() > 6) favorites.remove(favorites.size() - 1);
            prefs.edit().putBoolean("favorites_order_v0130", true).apply(); saveFavorites();
        }
    }
    private void renderFavorites() {
        if (cleanHome != null) {
            List<Apps.App> ordered = new ArrayList<>(); for (String pkg : favorites) { Apps.App app = findApp(pkg); if (app != null) ordered.add(app); }
            cleanHome.render(ordered, favoriteViews); refreshMusic();
            if (panel == null && activeDialog == null) focusFavorites(); return;
        }
        favoritesRow.removeAllViews(); favoriteViews.clear(); int index = 0;
        for (String pkg : favorites) {
            Apps.App app = findApp(pkg); if (app == null) continue;
            FrameLayout cell = appCell(app, 132); ui.place(favoritesRow, cell, index++ * 146, 0, 132, 78);
            View card = cell.getChildAt(0); favoriteViews.add(card); card.setNextFocusUpId(artworkFocus.getId());
        }
        if (!favoriteViews.isEmpty()) {
            for (View control : navigation) control.setNextFocusDownId(favoriteViews.get(0).getId());
            artworkFocus.setNextFocusUpId(homeNav.getId()); artworkFocus.setNextFocusDownId(favoriteViews.get(0).getId());
            wireNewsFocus();
            musicCard.returnTo(favoriteViews.get(Math.min(2, favoriteViews.size() - 1)).getId()); refreshMusic();
            if (panel == null) focusFavorites();
        }
    }
    private FrameLayout appCell(Apps.App app, int width) {
        return appCell(app, width, false, null);
    }
    private FrameLayout appCell(Apps.App app, int width, boolean coverBanner, Runnable selected) {
        FrameLayout cell = new FrameLayout(this); cell.setClipChildren(false); cell.setClipToPadding(false);
        FrameLayout card = new FrameLayout(this); float radius = 10;
        card.setBackground(ui.round(0xFF151D1C, radius, 0, 0));
        card.setClipToOutline(true);
        FocusFrame focusFrame = new FocusFrame(card, ui, Color.TRANSPARENT, radius); focusFrame.innerOnly(app.image()); card.setForeground(focusFrame);
        ImageView image = new ImageView(this); image.setImageDrawable(app.image());
        image.setScaleType(coverBanner && app.banner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER); image.setAdjustViewBounds(false);
        if (!app.banner) image.setPadding(ui.px(28), ui.px(12), ui.px(28), ui.px(12));
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(image, new FrameLayout.LayoutParams(-1, -1));
        ui.place(cell, card, 0, 0, width, width * 9f / 16f);
        ui.interactive(card, app.name + (favorites.contains(app.pkg) ? ". Maintenez OK pour déplacer ou gérer ce favori." : ". Maintenez OK pour ajouter aux favoris."), () -> launch(app));
        new SelectionFeedback(card, 1.055f, ui.px(2), ui.px(8), (active, focused) -> {
            focusFrame.setActive(active);
            if (active && selected != null) selected.run();
        }, true);
        card.setOnLongClickListener(view -> {
            if (favorites.contains(app.pkg)) showFavoriteActions(app, selected);
            else { toggleFavorite(app); if (selected != null) selected.run(); }
            return true;
        }); return cell;
    }
    private void launch(Apps.App app) {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (RuntimeException error) { toast("Cette application ne peut pas être ouverte."); loadApps(); }
    }

    private void refreshWidgets() {
        if (clock == null) return;
        if (greeting != null) greeting.setHomeVisible(homeAvailable());
        if (entrance != null) entrance.visible(homeAvailable());
        clock.setVisibility(prefs.getBoolean("widget_clock", true) ? View.VISIBLE : View.INVISIBLE);
        weatherView.setVisibility(prefs.getBoolean("widget_weather", true) ? View.VISIBLE : View.INVISIBLE);
        date.setVisibility(prefs.getBoolean("widget_date", true) ? View.VISIBLE : View.INVISIBLE);
        date.setText(new SimpleDateFormat("EEEE d MMMM", Locale.FRENCH).format(new Date()));
        boolean showArt = prefs.getBoolean("widget_art", true);
        artTitle.setVisibility(showArt ? View.VISIBLE : View.INVISIBLE);
        String message = prefs.getString("note", ""); boolean showNote = prefs.getBoolean("widget_note", false) && !message.isEmpty();
        note.setText(message); note.setVisibility(showNote ? View.VISIBLE : View.INVISIBLE); noteLabel.setVisibility(showNote ? View.VISIBLE : View.INVISIBLE);
        org.json.JSONObject quote = wisdom.today(prefs.getInt("quote_offset", 0));
        fitQuotation(quote.optString("text"), quote.optString("author")); quoteCredit.setText(quote.optString("author") + " · " + quote.optString("reference"));
        quoteText.setVisibility(prefs.getBoolean("widget_quote", true) ? View.VISIBLE : View.INVISIBLE);
        quoteCredit.setVisibility(View.GONE);
        quotation.setVisibility(prefs.getBoolean("widget_quote", true) ? View.VISIBLE : View.INVISIBLE);
        refreshMusic();
        boolean showNews = !quoteInFooter() && prefs.getBoolean("widget_news", true);
        if (!showNews && newsView.hasFocus()) focusFavorites();
        newsView.setVisibility(showNews ? View.VISIBLE : View.INVISIBLE); wireNewsFocus();
        refreshHomeContrast();
    }
    private void refreshMusic() {
        if (musicCard == null) return;
        boolean visible = musicVisible() && (cleanHome == null || !cleanHome.isArtworkCoveringHome());
        if (!visible && musicCard.findFocus()!=null) focusFavorites();
        musicCard.setVisibility(visible ? View.VISIBLE : View.INVISIBLE); musicCard.render(musicTrack);
        if (!navigation.isEmpty()) {
            View settings = settingsNav;
            if (visible) { settings.setNextFocusDownId(musicCard.getId()); musicCard.setNextFocusUpId(settings.getId()); }
            else if (!favoriteViews.isEmpty()) settings.setNextFocusDownId(favoriteViews.get(0).getId());
        }
        musicCard.setActive(resumed && panel==null && activeDialog==null && visible && musicCard.isShown());
        if (cleanHome != null) cleanHome.refreshWidgets();
        if (listening != null) { listening.render(musicTrack); listening.setActive(resumed && activeDialog == null); }
        if (controlCenter != null) controlCenter.render(musicTrack);
    }
    private boolean musicVisible() {
        return prefs.getBoolean("widget_music", true) && musicTrack.access && musicTrack.session && musicTrack.playing;
    }
    private void fitQuotation(String text, String author) {
        quoteText.setSingleLine(true);quoteText.setHorizontallyScrolling(false);quoteText.setMaxLines(1);quoteText.setLineSpacing(0,1);
        quoteText.setAutoSizeTextTypeUniformWithConfiguration(ui.px(quoteInFooter()?8:10),ui.px(quoteInFooter()?10:13),1,android.util.TypedValue.COMPLEX_UNIT_PX);
        quoteText.setText(text+(author.isEmpty()?"":" — "+author));
    }
    private boolean homeAvailable() { return resumed && panel == null && activeDialog == null && (cleanHome == null || !cleanHome.isArtworkCoveringHome()); }
    private void updateSlideshowVisibility() {
        if (contrast != null) refreshHomeContrast();
        if (greeting != null) greeting.setHomeVisible(homeAvailable());
        if (entrance != null) entrance.visible(homeAvailable());
        if (slideshow != null) slideshow.setVisible(homeAvailable());
        if (weather != null) weather.setVisible(homeAvailable() && prefs.getBoolean("widget_weather", true));
        if (musicCard != null) musicCard.setActive(resumed && panel==null && activeDialog==null && musicVisible() && musicCard.isShown());
        boolean newsActive = homeAvailable() && prefs.getBoolean("widget_news", true);
        if (news != null) news.setVisible(newsActive);
        if (newsView != null) newsView.setActive(newsActive && cleanHome == null && !quoteInFooter());
        if (listening != null) listening.setActive(resumed && activeDialog == null);
    }
    private boolean quoteInFooter() { return "quote".equals(prefs.getString("footer_content", "quote")); }
    private void wireNewsFocus() {
        if (cleanHome != null) { cleanHome.refreshWidgets(); return; }
        if (favoriteViews.isEmpty()) return;
        if (quoteInFooter()) {
            boolean visible = quotation.getVisibility() == View.VISIBLE;
            for (View favorite : favoriteViews) { favorite.setNextFocusUpId(homeNav.getId()); favorite.setNextFocusDownId(visible ? quotation.getId() : favorite.getId()); }
            quotation.setNextFocusUpId(favoriteViews.get(0).getId()); quotation.setNextFocusDownId(quotation.getId()); quotation.setNextFocusLeftId(quotation.getId());
            quotation.setNextFocusRightId(artTitle.getVisibility() == View.VISIBLE ? artTitle.getId() : quotation.getId());
            artTitle.setNextFocusLeftId(visible ? quotation.getId() : artTitle.getId()); artTitle.setNextFocusUpId(favoriteViews.get(favoriteViews.size()-1).getId()); artTitle.setNextFocusDownId(artTitle.getId());
            return;
        }
        boolean visible = prefs.getBoolean("widget_news", true);
        for (View favorite : favoriteViews) {
            favorite.setNextFocusUpId(artworkFocus.getId()); favorite.setNextFocusDownId(visible ? newsView.getId() : favorite.getId());
        }
        artworkFocus.setNextFocusDownId(favoriteViews.get(0).getId());
        newsView.setNextFocusUpId(favoriteViews.get(0).getId()); newsView.setNextFocusDownId(newsView.getId());
        newsView.setNextFocusRightId(newsView.getId()); newsView.setNextFocusLeftId(newsView.getId());
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences shared, String key) {
        if (destroyed || applyingFrame) return;
        if ("footer_content".equals(key)) { recreate(); return; }
        if ("ui_theme".equals(key)) { if (cleanTheme != "cleanframe".equals(prefs.getString("ui_theme", "atelier"))) recreate(); return; }
        if ("clean_layout".equals(key)) { renderFavorites(); refreshWidgets(); return; }
        if ("art".equals(key)) { slideshow.showSelected(); return; }
        if (key.startsWith("home_")) slideshow.preferencesChanged();
        if (key.startsWith("weather_") || "widget_weather".equals(key)) updateSlideshowVisibility();
        if (key.startsWith("news_") || "widget_news".equals(key)) updateSlideshowVisibility();
        if (key.startsWith("news_") && !"news_cache".equals(key) && news != null) news.preferencesChanged();
        refreshWidgets();
    }

    private FrameLayout openPanel(String title, String subtitle) {
        if (cleanHome != null) cleanHome.closeArtwork(false);
        closePanel(); home.setVisibility(View.INVISIBLE); panel = new GlassPanel(ui, wallpaper, true);
        updateSlideshowVisibility();
        root.addView(panel, new FrameLayout.LayoutParams(-1, -1)); panelRequest++;
        FrameLayout content = SafeArea.attach(panel, ui);
        TextView heading = ui.text(title, 31, Ui.WHITE, false); heading.setTypeface(ui.editorial);
        ui.place(content, heading, 48, 34, 730, 46);
        ui.place(content, ui.text(subtitle, 11, Ui.MUTED, false), 50, 87, 850, 23);
        TextView close = ui.button("Fermer  ×", 11, () -> closePanel()); panelClose = close;
        ui.place(content, close, 800, 42, 112, 32);
        close.requestFocus(); return content;
    }
    private void closePanel() {
        newsList = null; newsSubtitle = null; newsScroll = null; newsSources = null;
        if (listening != null) { listening.close(); listening = null; }
        stopTransfer(); importing = false; panelRequest++;
        if (panel == null) { updateSlideshowVisibility(); return; }
        cancelViewAnimations(panel); root.removeView(panel); panel = null; panelClose = null; home.setVisibility(View.VISIBLE);
        if (lastHomeFocus != null && lastHomeFocus.isAttachedToWindow() && lastHomeFocus.isShown()) lastHomeFocus.requestFocus(); else focusFavorites();
        updateSlideshowVisibility();
    }
    private void showApps() {
        FrameLayout content = openPanel("Applications", "À regarder, à écouter, à explorer. Appui long sur OK pour les favoris.");
        TextView selection = ui.text("", 15, Ui.WHITE, true);
        TextView position = ui.text(apps.size() + " applications", 10, Ui.MUTED, false); position.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        selection.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        position.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        View separator = new View(this); separator.setBackgroundColor(0x20FFFFFF); ui.place(content, separator, 50, 490, 860, .5f);
        View marker = new View(this); marker.setBackground(ui.round(Ui.WHITE, 2, 0, 0)); ui.place(content, marker, 50, 505, 3, 15);
        ui.place(content, selection, 63, 499, 555, 26); ui.place(content, position, 623, 501, 287, 22);
        List<Apps.App> ordered = Apps.libraryOrder(apps);
        FocusScrollView scroll = new FocusScrollView(ui); scroll.setPadding(ui.px(14), ui.px(16), ui.px(14), ui.px(16));
        scroll.setClipToPadding(true); scroll.setVerticalScrollBarEnabled(false); scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setSmoothScrollingEnabled(false); scroll.setFocusable(false);
        GridLayout grid = new GridLayout(this); grid.setColumnCount(5); grid.setClipChildren(false); grid.setClipToPadding(false);
        List<View> cards = new ArrayList<>(); final int[] firstVisibleRow = {0};
        for (int index = 0; index < ordered.size(); index++) {
            Apps.App app = ordered.get(index); final int number = index + 1;
            FrameLayout cell = appCell(app, 156, true, () -> {
                selection.setText(app.name);
                position.setText(number + " / " + ordered.size() + (favorites.contains(app.pkg) ? "   ·   Sur l’accueil" : "   ·   Toutes les applications"));
                // Keep three complete rows, including the zoom gutter at both edges.
                int row = (number - 1) / 5;
                if (row < firstVisibleRow[0]) firstVisibleRow[0] = row;
                else if (row > firstVisibleRow[0] + 2) firstVisibleRow[0] = row - 2;
                scroll.post(() -> {
                    if (scroll.isAttachedToWindow() && scroll.hasFocus()) scroll.scrollTo(0, ui.px(firstVisibleRow[0] * 112));
                });
            });
            // The gutter belongs to the cell: zoom and elevation never touch a neighbour.
            FrameLayout.LayoutParams tile = (FrameLayout.LayoutParams) cell.getChildAt(0).getLayoutParams();
            tile.leftMargin = ui.px(8); tile.topMargin = ui.px(8); cell.getChildAt(0).setLayoutParams(tile);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(); params.width = ui.px(172); params.height = ui.px(112);
            grid.addView(cell, params); cards.add(cell.getChildAt(0));
        }
        scroll.addView(grid, new ScrollView.LayoutParams(-1, -2)); ui.place(content, scroll, 36, 120, 888, 368);
        for (int i = 0; i < cards.size(); i++) {
            View card = cards.get(i); int column = i % 5;
            card.setNextFocusLeftId(cards.get(column == 0 ? i : i - 1).getId());
            card.setNextFocusRightId(cards.get(column == 4 || i == cards.size() - 1 ? i : i + 1).getId());
            card.setNextFocusUpId(i < 5 ? panelClose.getId() : cards.get(i - 5).getId());
            card.setNextFocusDownId(cards.get(i + 5 < cards.size() ? i + 5 : i / 5 < (cards.size() - 1) / 5 ? cards.size() - 1 : i).getId());
            card.setNextFocusForwardId(i + 1 < cards.size() ? cards.get(i + 1).getId() : panelClose.getId());
        }
        if (!cards.isEmpty()) {
            panelClose.setNextFocusDownId(cards.get(0).getId()); cards.get(0).requestFocusFromTouch();
        } else { marker.setVisibility(View.GONE); selection.setText("Aucune application disponible"); }
    }

    private void showGallery() {
        FrameLayout content = openPanel("Votre galerie", "Choisissez le décor de votre accueil. Appui long pour gérer un tableau personnel.");
        TextView add = ui.button("Ajouter mes tableaux", 11, () -> showTransfer()); ui.place(content, add, 50, 127, 219, 34);
        TextView fullscreen = ui.button("Plein écran", 11, () -> launchGallery()); ui.place(content, fullscreen, 281, 127, 158, 34);
        TextView appearance = ui.button("Affichage", 11, () -> showAppearance()); ui.place(content, appearance, 451, 127, 123, 34);
        TextView slideshowButton = ui.button("Diaporama", 11, () -> showHomeSlideshow()); ui.place(content, slideshowButton, 586, 127, 136, 34);
        FocusScrollView scroll = new FocusScrollView(ui);
        GridLayout grid = new GridLayout(this); grid.setColumnCount(3); grid.setClipChildren(false); grid.setClipToPadding(false);
        grid.setPadding(ui.px(6), ui.px(8), ui.px(6), ui.px(8));
        List<ArtStore.Art> works = art.list(); final int request = panelRequest;
        List<View> cards = new ArrayList<>();
        for (ArtStore.Art work : works) {
            FrameLayout cell = new FrameLayout(this); cell.setClipChildren(false); cell.setClipToPadding(false);
            FrameLayout card = new FrameLayout(this);
            card.setBackground(ui.round(0xFF182322, 10, 0, 0)); card.setClipToOutline(true);
            ImageView image = new ImageView(this); image.setScaleType(prefs.getBoolean("art_cover", true) ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
            image.setBackground(ui.round(0xFF182322, 10, 0, 0)); image.setClipToOutline(true);
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); card.addView(image, new FrameLayout.LayoutParams(-1, -1));
            FocusFrame focusFrame = new FocusFrame(card, ui, 0x44FFFFFF); card.setForeground(focusFrame);
            ui.place(cell, card, 8, 8, 264, 148.5f);
            ui.place(cell, ui.text(work.title, 11, Ui.WHITE, true), 10, 166, 261, 22);
            ui.place(cell, ui.text(work.artist, 9, Ui.MUTED, false), 10, 190, 261, 18);
            ui.interactive(card, work.title + ", " + work.artist, () -> { prefs.edit().putString("art", work.id).apply(); closePanel(); });
            new SelectionFeedback(card, 1.025f, ui.px(1), ui.px(5), (active, focused) -> focusFrame.setActive(active));
            if (work.personal()) card.setOnLongClickListener(view -> { manageArtwork(work); return true; });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(); params.width = ui.px(286); params.height = ui.px(218); grid.addView(cell, params);
            cards.add(card); scroll.keepTogether(card, cell);
            worker.execute(() -> {
                if (destroyed || request != panelRequest) return;
                try {
                    Bitmap bitmap = prefs.getBoolean("art_cover", true) ? art.decodeCover(work, 320) : art.decode(work, 320);
                    runOnUiThread(() -> { if (!destroyed && request == panelRequest) image.setImageBitmap(bitmap); else bitmap.recycle(); });
                } catch (Exception error) { android.util.Log.w("AtelierTV", "Thumbnail failed", error); }
            });
        }
        scroll.addView(grid, new ScrollView.LayoutParams(-1, -2)); ui.place(content, scroll, 36, 172, 888, 318);
        View[] toolbar = {add, fullscreen, appearance, slideshowButton};
        for (int i = 0; i < cards.size(); i++) {
            View card = cards.get(i); int column = i % 3;
            card.setNextFocusLeftId(cards.get(column == 0 ? i : i - 1).getId());
            card.setNextFocusRightId(cards.get(column == 2 || i == cards.size() - 1 ? i : i + 1).getId());
            card.setNextFocusUpId(i < 3 ? toolbar[column].getId() : cards.get(i - 3).getId());
            card.setNextFocusDownId(cards.get(i + 3 < cards.size() ? i + 3 : i / 3 < (cards.size() - 1) / 3 ? cards.size() - 1 : i).getId());
        }
        for (int i = 0; i < toolbar.length; i++) {
            toolbar[i].setNextFocusLeftId(toolbar[Math.max(0, i - 1)].getId());
            toolbar[i].setNextFocusRightId(i == toolbar.length - 1 ? panelClose.getId() : toolbar[i + 1].getId());
            toolbar[i].setNextFocusUpId(panelClose.getId());
            if (!cards.isEmpty()) toolbar[i].setNextFocusDownId(cards.get(Math.min(i, Math.min(2, cards.size() - 1))).getId());
        }
        panelClose.setNextFocusDownId(slideshowButton.getId());
        ui.place(content, ui.text(works.size() + " œuvres · Les proportions originales sont conservées", 10, Ui.MUTED, false), 50, 502, 800, 23);
        add.requestFocusFromTouch();
    }
    private void manageArtwork(ArtStore.Art work) {
        dialogBuilder().setTitle(work.title).setItems(new String[]{"Afficher en fond", "Supprimer de la TV"}, (dialog, index) -> {
            if (index == 0) { prefs.edit().putString("art", work.id).apply(); closePanel(); }
            else dialogBuilder().setTitle("Supprimer ce tableau de la TV ?")
                .setMessage("Le fichier original sur votre téléphone ou Mac est conservé.")
                .setPositiveButton("Supprimer", (confirm, which) -> { art.remove(work); showGallery(); })
                .setNegativeButton("Annuler", null).show();
        }).show();
    }

    private void showTransfer() {
        FrameLayout content = openPanel("Vos tableaux, sans cloud.", "Votre téléphone ou Mac doit être connecté au même réseau que la télévision.");
        importing = true;
        TextView explanation = ui.text("Ouvrez cette adresse dans votre navigateur,\npuis saisissez le code affiché ci-dessous.", 14, Ui.MUTED, false);
        explanation.setSingleLine(false); explanation.setMaxLines(2); explanation.setLineSpacing(0, 1.3f);
        ui.place(content, explanation, 50, 137, 508, 62);
        TextView address = ui.text("Préparation…", 24, Ui.WHITE, true); ui.place(content, address, 50, 211, 504, 48);
        TextView codeLabel = ui.text("CODE D’ASSOCIATION", 9, Ui.MUTED, true); codeLabel.setLetterSpacing(.16f);
        ui.place(content, codeLabel, 52, 286, 440, 20);
        TextView code = ui.text("", 52, Ui.WHITE, false); code.setLetterSpacing(.14f); ui.place(content, code, 49, 316, 470, 74);
        FrameLayout card = new FrameLayout(this); card.setBackground(ui.round(0x12FFFFFF, 14, 0x25FFFFFF, .7f));
        ui.place(content, card, 594, 145, 315, 262);
        TextView title = ui.text("Votre collection.\nVotre regard.", 29, Ui.WHITE, false); title.setTypeface(ui.editorial); title.setSingleLine(false); title.setMaxLines(2);
        ui.place(card, title, 23, 23, 270, 78);
        TextView details = ui.text("JPEG, PNG ou WebP\n20 Mo maximum par image\n60 tableaux personnels\n\nLes originaux restent sur la TV.", 11, Ui.MUTED, false);
        details.setSingleLine(false); details.setMaxLines(5); details.setLineSpacing(0, 1.35f); ui.place(card, details, 25, 116, 267, 124);
        panelCount = ui.text("Restez sur cet écran pendant le transfert. Session de 15 minutes.", 11, Ui.MUTED, false);
        ui.place(content, panelCount, 50, 451, 850, 31);
        try {
            transfer = new ArtTransfer(art, () -> runOnUiThread(() -> {
                if (destroyed) return;
                if (importing && panelCount != null) panelCount.setText(art.list().size() + " tableaux dans votre galerie · Vos changements sont enregistrés.");
            }));
            address.setText(transfer.address); address.setContentDescription("Adresse d’import " + transfer.address);
            code.setText(transfer.pin); code.setContentDescription("Code d’association " + transfer.pin);
        } catch (Exception error) { address.setText("Connexion indisponible"); code.setText(""); panelCount.setText(error.getMessage()); }
    }
    private void stopTransfer() { if (transfer != null) { transfer.close(); transfer = null; } }
    private void launchGallery() { startActivity(new Intent(this, GalleryActivity.class)); }

    private void showWidgets() {
        String[] keys = {"widget_quote", "widget_clock", "widget_date", "widget_art", "widget_note", "widget_weather", "widget_music", "widget_news", "welcome_enabled"};
        String[] titles = {"Citation philosophique", "Horloge", "Date", "Titre et artiste du tableau", "Message personnel", "Météo et prévisions", "Carte Spotify", "Actualités choisies", "Salutation brève au démarrage"}; boolean[] values = new boolean[keys.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = prefs.getBoolean(keys[i], i != 4);
            if (cleanHome != null && i != 0 && i != 1 && i != 3 && i != 5 && i != 6 && i != 8) titles[i] += " · thème Atelier";
        }
        dialogBuilder().setTitle("Les widgets de votre accueil")
            .setMultiChoiceItems(titles, values, (dialog, index, checked) -> prefs.edit().putBoolean(keys[index], checked).apply())
            .setNeutralButton("Modifier mon message", (dialog, index) -> editMessage())
            .setNegativeButton("Autre citation", (dialog, index) -> prefs.edit().putInt("quote_offset", prefs.getInt("quote_offset", 0) + 1).apply())
            .setPositiveButton("Terminé", null).show();
    }
    private void editMessage() {
        EditText input = new EditText(this); input.setText(prefs.getString("note", "")); input.setTypeface(ui.body);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)}); input.setHint("Prenez le temps.");
        dialogBuilder().setTitle("Votre message à l’écran").setView(input)
            .setPositiveButton("Enregistrer", (dialog, which) -> {
                String message = input.getText().toString().trim(); prefs.edit().putString("note", message).putBoolean("widget_note", !message.isEmpty()).apply();
            }).setNegativeButton("Annuler", null).show();
    }
    private void showAppearance() {
        dialogBuilder().setTitle("Affichage · galerie et veille")
            .setSingleChoiceItems(new String[]{"Adapter · œuvre entière", "Remplir · recadrage centré"}, prefs.getBoolean("art_cover", true) ? 1 : 0,
                (dialog, which) -> prefs.edit().putBoolean("art_cover", which == 1).apply())
            .setPositiveButton("Terminé", null).show();
    }
    private void showHomeSlideshow() {
        boolean[] values = {prefs.getBoolean("home_slideshow", true), prefs.getBoolean("home_shuffle", true),
            prefs.getBoolean("home_personal", false)};
        String[] keys = {"home_slideshow", "home_shuffle", "home_personal"};
        dialogBuilder().setTitle("Diaporama de l’accueil")
            .setMultiChoiceItems(new String[]{"Défilement automatique et citations synchronisées", "Ordre aléatoire sans répétition", "Uniquement mes tableaux personnels"}, values,
                (dialog, index, checked) -> {
                    if (index == 2 && checked) {
                        boolean found = false; for (ArtStore.Art work : art.list()) if (work.personal()) found = true;
                        if (!found) { ((AlertDialog) dialog).getListView().setItemChecked(index, false); toast("Ajoutez d’abord un tableau personnel dans Galerie."); return; }
                    }
                    prefs.edit().putBoolean(keys[index], checked).apply();
                })
            .setNeutralButton("Durée", (dialog, which) -> {
                int[] seconds = {30, 60, 180, 300, 600}; int selected = 1;
                for (int i = 0; i < seconds.length; i++) if (prefs.getInt("home_seconds", 60) == seconds[i]) selected = i;
                dialogBuilder().setTitle("Changer le tableau et la citation toutes les…")
                    .setSingleChoiceItems(new String[]{"30 secondes", "1 minute", "3 minutes", "5 minutes", "10 minutes"}, selected,
                        (choice, index) -> prefs.edit().putInt("home_seconds", seconds[index]).apply())
                    .setPositiveButton("Terminé", null).show();
            })
            .setNegativeButton("Tableau suivant", (dialog, which) -> { advanceAfterDialog = true; closePanel(); })
            .setPositiveButton("Terminé", null).show();
    }
    private void showDreamOptions() {
        String[] keys = {"dream_caption", "dream_clock", "dream_date", "dream_personal", "dream_bright"};
        boolean[] values = {prefs.getBoolean("dream_caption", true), prefs.getBoolean("dream_clock", true), prefs.getBoolean("dream_date", true),
            prefs.getBoolean("dream_personal", false), !prefs.getBoolean("dream_bright", false)};
        dialogBuilder().setTitle("Écran de veille · Galerie d’art")
            .setMultiChoiceItems(new String[]{"Titre de l’œuvre et artiste", "Grande horloge", "Date discrète", "Uniquement mes tableaux personnels", "Lumière douce"}, values,
                (dialog, index, checked) -> prefs.edit().putBoolean(keys[index], index == 4 ? !checked : checked).apply())
            .setNeutralButton("Délai de veille TV", (dialog, index) -> openDreamSettings())
            .setPositiveButton("Défilement", (dialog, index) -> {
                int[] seconds = {30, 60, 180, 300}; int selectedIndex = 1;
                for (int i = 0; i < seconds.length; i++) if (prefs.getInt("slideshow_seconds", 60) == seconds[i]) selectedIndex = i;
                dialogBuilder().setTitle("Changer de tableau toutes les…")
                    .setSingleChoiceItems(new String[]{"30 secondes", "1 minute", "3 minutes", "5 minutes"}, selectedIndex,
                        (select, which) -> prefs.edit().putInt("slideshow_seconds", seconds[which]).apply()).setPositiveButton("Terminé", null).show();
            }).setNegativeButton("Fermer", null).show();
    }
    private void showSettings() {
        dialogBuilder().setTitle("Votre Atelier")
            .setItems(new String[]{"Thème de l’accueil", "Applications favorites", "Widgets et citations", "Affichage des tableaux", "Diaporama de l’accueil", "Écran de veille", "Météo et prévisions", "Musique Spotify", "À propos du tableau", "Actualités choisies", "Marge de sécurité", "Contenu du bas de l’accueil", "Réglages de la télévision"}, (dialog, which) -> {
                switch (which) { case 0: showThemeChooser(); break; case 1: customizeApps(); break; case 2: showWidgets(); break; case 3: showAppearance(); break;
                    case 4: showHomeSlideshow(); break; case 5: showDreamOptions(); break; case 6: showWeather(); break;
                    case 7: showMusicSettings(); break; case 8: showArtworkInfo(); break; case 9: showNews(); break; case 10: showSafeMargin(); break; case 11: showFooterOptions(); break; default: safeStart(new Intent(Settings.ACTION_SETTINGS), "Réglages indisponibles."); }
            }).setNegativeButton("Retour", null).show();
    }
    private void showFooterOptions() {
        dialogBuilder().setTitle("Sous les applications")
            .setSingleChoiceItems(new String[]{"Citation philosophique", "Titres d’actualité · thème Atelier"}, quoteInFooter() ? 0 : 1, (dialog, which) -> {
                dialog.dismiss(); String value = which == 0 ? "quote" : "news";
                if (!value.equals(prefs.getString("footer_content", "quote"))) prefs.edit().putString("footer_content", value).apply();
            }).setNegativeButton("Retour", null).show();
    }
    private void showThemeChooser() {
        AlertDialog.Builder choices = dialogBuilder().setTitle("Thème de l’accueil")
            .setSingleChoiceItems(new String[]{"Atelier · votre interface", "CleanFrame · interface épurée"}, cleanTheme ? 1 : 0, (dialog, which) -> {
                String theme = which == 1 ? "cleanframe" : "atelier"; dialog.dismiss();
                if (!theme.equals(prefs.getString("ui_theme", "atelier"))) prefs.edit().putString("ui_theme", theme).apply();
            }).setNegativeButton("Retour", null);
        if (cleanTheme) choices.setNeutralButton("Présentation CleanFrame", (dialog, which) -> showCleanFrameLayout());
        choices.show();
    }
    private void showCleanFrameLayout() {
        dialogBuilder().setTitle("Présentation CleanFrame")
            .setSingleChoiceItems(new String[]{"Barre horizontale · verre", "Liste verticale"}, "list".equals(prefs.getString("clean_layout", "dock")) ? 1 : 0, (dialog, which) -> {
                dialog.dismiss(); String layout = which == 1 ? "list" : "dock";
                if (!layout.equals(prefs.getString("clean_layout", "dock"))) prefs.edit().putString("clean_layout", layout).apply();
            }).setNegativeButton("Retour", null).show();
    }
    private void refreshHomeContrast() { if (cleanHome != null) cleanHome.refreshContrast(); else if (contrast != null) { if(homeTone!=null)homeTone.refresh();contrast.refresh(); } }
    private void showSafeMargin() {
        dialogBuilder().setTitle("Marge de sécurité")
            .setSingleChoiceItems(new String[]{"0 % · Affichage actuel", "1 % · Très légère", "2 % · Légère", "3 % · Confort", "4 % · Large", "5 % · Maximale"}, SafeArea.percent(prefs),
                (dialog, which) -> prefs.edit().putInt(SafeArea.KEY, which).apply())
            .setPositiveButton("Terminé", null).show();
    }
    private void showMusicSettings() {
        boolean allowed = MusicAccessService.allowed(this);
        dialogBuilder().setTitle("Carte Spotify")
            .setMessage((allowed ? "Accès Android activé.\n" : "Android demande un accès spécial aux notifications pour lire les sessions média.\n") +
                "Le service utilise uniquement la session Spotify et ne conserve pas votre historique d’écoute.\n\n" +
                (musicTrack.title.isEmpty() ? "Lancez un morceau sur Spotify, puis revenez à l’accueil avec Home." : musicTrack.title + "\n" + musicTrack.artist))
            .setNeutralButton("Accès Android", (dialog, which) -> safeStart(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), "Ce réglage n’est pas exposé par le menu Philips."))
            .setPositiveButton("Mode écoute", (dialog, which) -> showListening()).setNegativeButton("Fermer", null).show();
    }
    private void showListening() {
        if (cleanHome != null) cleanHome.closeArtwork(false);
        if (activeDialog != null) activeDialog.dismiss();
        closePanel(); home.setVisibility(View.INVISIBLE);
        listening = new ListeningView(ui, () -> closePanel()); panel = listening;
        root.addView(panel, new FrameLayout.LayoutParams(-1, -1)); panelRequest++;
        listening.render(musicTrack); updateSlideshowVisibility(); listening.focusPrimary();
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) { panel.setAlpha(0); panel.animate().alpha(1).setDuration(180).setInterpolator(new android.view.animation.PathInterpolator(.16f,1,.3f,1)).start(); }
    }
    private Runnable afterControl(Runnable action) { return () -> { if (controlDialog != null) controlDialog.dismiss(); action.run(); }; }
    private void showAirPlay() {
        List<ScreenShare.Receiver> receivers=ScreenShare.installed(this);
        if(receivers.isEmpty()){
            dialogBuilder().setTitle("AirPlay").setMessage("Pour partager l’écran de votre iPhone ou de votre Mac, installez un récepteur AirPlay sur la TV. Aucun récepteur compatible n’est actuellement disponible.")
                .setPositiveButton("Voir sur Google Play",(dialog,which)->safeStart(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("market://details?id=com.ionitech.airscreen")),"Google Play n’est pas disponible."))
                .setNegativeButton("Fermer",null).show();return;
        }
        ScreenShare.Receiver selected=receivers.get(0);String preferred=prefs.getString(ScreenShare.KEY,"");
        for(ScreenShare.Receiver receiver:receivers)if(receiver.pkg.equals(preferred))selected=receiver;
        final ScreenShare.Receiver receiver=selected;
        AlertDialog.Builder options=dialogBuilder().setTitle("AirPlay · Partage d’écran")
            .setMessage("Ouvrez le récepteur sur la TV, puis utilisez la recopie d’écran de votre appareil Apple.\n\niPhone / iPad : Centre de contrôle → Recopie de l’écran.\nMac : Centre de contrôle → Recopie de l’écran.\n\nChoisissez le nom affiché sur la TV. Les deux appareils doivent être sur le même réseau Wi-Fi.\n\nRécepteur sur la TV : "+receiver.name+". La recopie passe par cette application, avec les éventuelles limites qu’elle indique.")
            .setPositiveButton("Ouvrir AirPlay",(dialog,which)->launchAirPlay(receiver)).setNegativeButton("Fermer",null);
        if(receivers.size()>1)options.setNeutralButton("Autre récepteur",(dialog,which)->chooseAirPlayReceiver());
        options.show();
    }
    private void chooseAirPlayReceiver(){
        List<ScreenShare.Receiver> receivers=ScreenShare.installed(this);if(receivers.isEmpty()){showAirPlay();return;}
        String[] labels=new String[receivers.size()];for(int i=0;i<labels.length;i++)labels[i]=receivers.get(i).name;
        dialogBuilder().setTitle("Récepteur AirPlay sur la TV").setItems(labels,(dialog,index)->{ScreenShare.Receiver receiver=receivers.get(index);prefs.edit().putString(ScreenShare.KEY,receiver.pkg).apply();launchAirPlay(receiver);})
            .setNegativeButton("Retour",(dialog,which)->showAirPlay()).show();
    }
    private void launchAirPlay(ScreenShare.Receiver receiver){
        Intent intent=ScreenShare.launchIntent(this,receiver);if(intent==null){toast("Ce récepteur n’est plus disponible. Choisissez-en un autre.");showAirPlay();return;}
        safeStart(intent,"Impossible d’ouvrir le récepteur AirPlay.");
    }
    private void openDebugSettings() {
        Intent wireless = new Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS");
        if (wireless.resolveActivity(getPackageManager()) != null) {
            safeStart(wireless, "Le réglage de débogage n’est pas disponible."); return;
        }
        // Philips exposes its developer page, rather than a separate wireless activity.
        safeStart(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), "Ouvrez les options pour les développeurs dans les réglages de la TV.");
    }
    private void showControlCenter() {
        if (controlDialog != null && controlDialog.isShowing()) { dismissControl(); return; }
        if (activeDialog != null) activeDialog.dismiss();
        List<ControlCenter.Source> sources = new ArrayList<>();
        try {
            TvInputManager manager = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
            if (manager != null) for (TvInputInfo input : manager.getTvInputList()) if (input.isPassthroughInput() && !input.isHidden(this)) {
                CharSequence custom = input.loadCustomLabel(this); String label = (custom == null ? input.loadLabel(this) : custom).toString();
                int state = manager.getInputState(input.getId());
                String status = state == TvInputManager.INPUT_STATE_CONNECTED ? "Connectée" : state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY ? "En veille" : "Non connectée";
                sources.add(new ControlCenter.Source(label, status, afterControl(() -> safeStart(new Intent(Intent.ACTION_VIEW,
                    TvContract.buildChannelUriForPassthroughInput(input.getId())), "Utilisez la touche Sources de la télécommande."))));
            }
        } catch (RuntimeException ignored) { }
        sources.sort((first, second) -> {
            int connected = Boolean.compare(!"Connectée".equals(first.status), !"Connectée".equals(second.status));
            return connected != 0 ? connected : first.label.compareToIgnoreCase(second.label);
        });
        controlCenter = new ControlCenter(ui, wallpaper, sources, afterControl(() -> showInputs()), afterControl(() -> showListening()),
            afterControl(() -> openDreamSettings()), afterControl(() -> showSettings()), afterControl(() -> openDebugSettings()), () -> dismissControl(), afterControl(() -> showAirPlay()));
        controlCenter.render(musicTrack);
        // A custom Dialog gives this fixed-size panel its full height; the
        // AlertDialog button container otherwise removes space at the bottom.
        Dialog dialog = new Dialog(this); dialog.setContentView(controlCenter.view);
        dialog.show(); controlDialog = dialog; trackDialog(dialog);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent); dialog.getWindow().getDecorView().setPadding(0, 0, 0, 0);
            dialog.getWindow().setLayout(ui.px(344), ui.px(512)); dialog.getWindow().setWindowAnimations(0);
            WindowManager.LayoutParams params = dialog.getWindow().getAttributes(); params.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
            float safeScale = SafeArea.scale(prefs);
            controlCenter.view.setPivotX(ui.px(172)); controlCenter.view.setPivotY(ui.px(256));
            controlCenter.view.setScaleX(safeScale); controlCenter.view.setScaleY(safeScale);
            params.x = ui.px(16 + SafeArea.percent(prefs) * 9.6f - (1 - safeScale) * 172); params.y = 0; params.dimAmount = .12f; dialog.getWindow().setAttributes(params);
        }
        dialog.setOnKeyListener((value, key, event) -> {
            if (key != android.view.KeyEvent.KEYCODE_BACK && key != android.view.KeyEvent.KEYCODE_MENU) return false;
            if (event.getAction() == android.view.KeyEvent.ACTION_UP) dismissControl(); return true;
        });
        controlCenter.focus();
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            controlCenter.view.setAlpha(0); controlCenter.view.setTranslationX(ui.px(20));
            controlCenter.view.animate().alpha(1).translationX(0).setDuration(220)
                .setInterpolator(new android.view.animation.PathInterpolator(.16f, 1f, .3f, 1f)).start();
        }
    }
    private void dismissControl() {
        Dialog dialog = controlDialog; ControlCenter center = controlCenter; if (dialog == null || center == null || Boolean.TRUE.equals(center.view.getTag())) return;
        center.view.setTag(true);
        center.view.animate().cancel();
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) { dialog.dismiss(); return; }
        center.view.animate().alpha(0).translationX(ui.px(16)).setDuration(120).withEndAction(() -> dialog.dismiss()).start();
    }
    private void showArtworkInfo() {
        ArtStore.Art work = currentArtwork == null ? art.selected() : currentArtwork; if (work == null) return;
        if (cleanHome != null && cleanHome.showArtwork(work, art.notes(work), art.interpretation(work), () -> editArtworkNotes(work))) return;
        LinearLayout content = sheetContent();
        TextView eyebrow = ui.text("DANS VOTRE GALERIE", 8, 0xFFC6CDD4, true); eyebrow.setLetterSpacing(.10f);
        content.addView(eyebrow, new LinearLayout.LayoutParams(-1, ui.px(17)));
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView preview = new ImageView(this); preview.setImageBitmap(wallpaper.currentImage());
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP); preview.setBackground(ui.round(0xFF34383D, 10, 0, 0)); preview.setClipToOutline(true);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(preview, new LinearLayout.LayoutParams(ui.px(84), ui.px(101)));
        LinearLayout identity = new LinearLayout(this); identity.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams identityLayout = new LinearLayout.LayoutParams(0, -2, 1); identityLayout.leftMargin = ui.px(20); header.addView(identity, identityLayout);
        TextView title = ui.text(work.title, 23, Ui.WHITE, true); title.setLetterSpacing(-.025f); title.setSingleLine(false); title.setMaxLines(3); title.setLineSpacing(0, 1.08f);
        identity.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView artist = ui.text(work.artist, 11, 0xFFE0E3E8, false);
        LinearLayout.LayoutParams artistLayout = new LinearLayout.LayoutParams(-1, ui.px(20)); artistLayout.topMargin = ui.px(9); identity.addView(artist, artistLayout); ui.fit(artist);
        if (!work.year.isEmpty()) identity.addView(ui.text(work.year, 9, 0xFFADB7C0, false), new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams headerLayout = new LinearLayout.LayoutParams(-1, -2); headerLayout.topMargin = ui.px(12); content.addView(header, headerLayout);
        View divider = new View(this); divider.setBackgroundColor(0x25FFFFFF);
        LinearLayout.LayoutParams dividerLayout = new LinearLayout.LayoutParams(-1, ui.px(.5f)); dividerLayout.topMargin = ui.px(21); dividerLayout.bottomMargin = ui.px(17); content.addView(divider, dividerLayout);
        String notes = art.notes(work);
        if (notes.isEmpty()) notes = work.personal() ? "Une œuvre de votre collection personnelle. Ajoutez quelques lignes avec « Vos notes » pour composer son cartel." : "Une œuvre de votre collection Atelier TV.";
        addSheetBody(content, notes);
        if (!work.source.isEmpty()) {
            TextView source = ui.text("Reproduction · " + android.net.Uri.parse(work.source).getHost(), 8, 0xFFAEB9C2, false);
            LinearLayout.LayoutParams sourceLayout = new LinearLayout.LayoutParams(-1, ui.px(19)); sourceLayout.topMargin = ui.px(13); content.addView(source, sourceLayout);
        }
        showSheet(content, "Vos notes", () -> editArtworkNotes(work), "Lire", () -> {
            if (activeDialog != null) activeDialog.dismiss(); showArtworkReading(work);
        });
    }
    private void showArtworkReading(ArtStore.Art work) {
        FrameLayout content = openPanel("Autour de l’œuvre", work.title + " · " + work.credit());
        final int request = panelRequest;
        ImageView preview = new ImageView(this); preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackground(ui.round(0xFF172124, 14, 0, 0)); preview.setClipToOutline(true);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); ui.place(content, preview, 50, 143, 326, 290);
        worker.execute(() -> {
            try {
                Bitmap bitmap = art.decode(work, 900);
                runOnUiThread(() -> {
                    if (!destroyed && request == panelRequest) {
                        float fit = Math.min(ui.px(326) / (float)bitmap.getWidth(), ui.px(290) / (float)bitmap.getHeight());
                        FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams)preview.getLayoutParams();
                        layout.width = Math.max(1, Math.round(bitmap.getWidth() * fit)); layout.height = Math.max(1, Math.round(bitmap.getHeight() * fit));
                        layout.leftMargin = ui.px(50) + (ui.px(326) - layout.width) / 2; layout.topMargin = ui.px(143) + (ui.px(290) - layout.height) / 2;
                        preview.setLayoutParams(layout); preview.setImageBitmap(bitmap);
                    } else bitmap.recycle();
                });
            } catch (Exception error) { android.util.Log.w("AtelierTV", "Reading preview unavailable", error); }
        });
        String reflection = art.interpretation(work), notes = art.notes(work);
        TextView body = ui.text(notes + (reflection.isEmpty() ? "" : "\n\nUNE LECTURE POSSIBLE\n\n" + reflection), 13.5f, Ui.WHITE, false);
        body.setSingleLine(false); body.setEllipsize(null); body.setIncludeFontPadding(true); body.setLineSpacing(0, 1.45f); body.setGravity(Gravity.TOP);
        ScrollView reading = new ScrollView(this); reading.setId(View.generateViewId()); reading.setFocusable(true);
        reading.setVerticalScrollBarEnabled(false); reading.setOverScrollMode(View.OVER_SCROLL_NEVER); reading.setDefaultFocusHighlightEnabled(false);
        reading.setPadding(ui.px(18), ui.px(16), ui.px(18), ui.px(18)); reading.setClipToOutline(true);
        reading.setBackground(ui.round(0x0AFFFFFF, 14, 0x28FFFFFF, .5f)); reading.addView(body, new ScrollView.LayoutParams(-1, -2));
        reading.setOnFocusChangeListener((view, focused) -> reading.setBackground(ui.round(0x0AFFFFFF, 14, focused ? 0x90FFFFFF : 0x28FFFFFF, .5f)));
        ui.place(content, reading, 408, 137, 502, 348);
        TextView edit = ui.button("Vos notes", 10.5f, () -> editArtworkNotes(work)); ui.place(content, edit, 640, 42, 145, 32);
        edit.setNextFocusRightId(panelClose.getId()); edit.setNextFocusDownId(reading.getId()); panelClose.setNextFocusDownId(reading.getId()); reading.setNextFocusUpId(panelClose.getId());
        TextView credit = ui.text("Lecture originale Atelier TV · Interprétation", 9, Ui.MUTED, false); credit.setSingleLine(false); credit.setMaxLines(2);
        ui.place(content, credit, 50, 447, 326, 38);
        ui.place(content, ui.text("Haut / Bas pour lire · Retour pour retrouver l’accueil", 9, Ui.MUTED, false), 50, 502, 810, 23);
        reading.requestFocusFromTouch();
    }
    private LinearLayout sheetContent() {
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false); content.setClipToPadding(false);
        content.setPadding(ui.px(25), ui.px(23), ui.px(25), ui.px(19)); return content;
    }
    private void addSheetBody(LinearLayout content, String text) {
        TextView body = ui.text(text, 12, 0xFFE0E4E9, false); body.setSingleLine(false); body.setEllipsize(null); body.setLineSpacing(0, 1.42f);
        content.addView(body, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextView sheetButton(String title, Runnable action) {
        TextView button = ui.text(title, 10.5f, Ui.WHITE, true); button.setGravity(Gravity.CENTER); ui.interactive(button, title, action);
        button.setBackground(ui.round(0x20FFFFFF, 18, 0, 0));
        new SelectionFeedback(button, 1.035f, 0, ui.px(2), (active, focused) -> {
            button.setTextColor(active ? Ui.INK : Ui.WHITE); button.setBackground(ui.round(active ? 0xFFF4F6F8 : 0x20FFFFFF, 18, 0, 0));
        }); return button;
    }
    private void showSheet(LinearLayout content, String actionTitle, Runnable action) {
        showSheet(content, actionTitle, action, null, null);
    }
    private void showSheet(LinearLayout content, String actionTitle, Runnable action, String extraTitle, Runnable extraAction) {
        shadowSheetText(content);
        GlassPanel glass = new GlassPanel(ui, wallpaper);
        LinearLayout stack = new LinearLayout(this); stack.setOrientation(LinearLayout.VERTICAL); stack.setClipChildren(false); stack.setClipToPadding(false);
        glass.addView(stack, new FrameLayout.LayoutParams(-1, -2));
        final int maximum = ui.px(405 - SafeArea.percent(prefs) * 10.8f);
        ScrollView scroll = new ScrollView(this) {
            @Override protected void dispatchDraw(android.graphics.Canvas canvas) {
                int saved = canvas.save(); canvas.clipRect(getScrollX(), getScrollY(), getScrollX() + getWidth(), getScrollY() + getHeight());
                super.dispatchDraw(canvas); canvas.restoreToCount(saved);
            }
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int available = View.MeasureSpec.getMode(heightSpec) == View.MeasureSpec.UNSPECIFIED ? maximum : Math.min(maximum, View.MeasureSpec.getSize(heightSpec));
                super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(available, View.MeasureSpec.AT_MOST));
            }
        };
        scroll.setId(View.generateViewId()); scroll.setFocusable(true); scroll.setDefaultFocusHighlightEnabled(false);
        scroll.setVerticalScrollBarEnabled(false); scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        content.setPadding(ui.px(25), ui.px(23), ui.px(25), ui.px(6));
        // Small credits can wrap inside the scrollable area, independently of system text size.
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            if (child instanceof TextView && child.getLayoutParams().height > 0) {
                ((TextView)child).setSingleLine(false); ((TextView)child).setMaxLines(3); child.getLayoutParams().height = -2;
            }
        }
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2)); stack.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        actions.setClipChildren(false); actions.setClipToPadding(false);
        final Dialog[] holder = new Dialog[1];
        if (extraAction != null) {
            TextView read = sheetButton(extraTitle, extraAction);
            LinearLayout.LayoutParams readLayout = new LinearLayout.LayoutParams(ui.px(75), ui.px(33)); readLayout.rightMargin = ui.px(9); actions.addView(read, readLayout);
        }
        if (action != null) {
            TextView extra = sheetButton(actionTitle, () -> action.run());
            LinearLayout.LayoutParams extraLayout = new LinearLayout.LayoutParams(ui.px(132), ui.px(33)); extraLayout.rightMargin = ui.px(9); actions.addView(extra, extraLayout);
        }
        TextView close = sheetButton("Fermer", () -> dismissSheet(holder[0], glass)); actions.addView(close, new LinearLayout.LayoutParams(ui.px(98), ui.px(33)));
        FrameLayout footer = new FrameLayout(this); footer.setClipChildren(false); footer.setClipToPadding(false);
        FrameLayout.LayoutParams actionsLayout = new FrameLayout.LayoutParams(-1, ui.px(37));
        actionsLayout.leftMargin = actionsLayout.rightMargin = ui.px(25); actionsLayout.topMargin = ui.px(12); footer.addView(actions, actionsLayout);
        stack.addView(footer, new LinearLayout.LayoutParams(-1, ui.px(68)));
        scroll.setNextFocusDownId(close.getId());
        for (int i = 0; i < actions.getChildCount(); i++) { TextView button = (TextView)actions.getChildAt(i); ui.fit(button); button.setNextFocusUpId(scroll.getId()); }
        if (activeDialog != null) activeDialog.dismiss();
        Dialog dialog = new Dialog(this); dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(glass); dialog.show(); trackDialog(dialog); holder[0] = dialog;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(ui.px(570), WindowManager.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setWindowAnimations(0);
            WindowManager.LayoutParams params = dialog.getWindow().getAttributes(); params.gravity = Gravity.BOTTOM | Gravity.LEFT;
            params.x = ui.px(40 + SafeArea.percent(prefs) * 9.6f); params.y = ui.px(30 + SafeArea.percent(prefs) * 5.4f); params.dimAmount = .08f; dialog.getWindow().setAttributes(params);
        }
        dialog.setOnKeyListener((value, key, event) -> {
            if (key != android.view.KeyEvent.KEYCODE_BACK) return false;
            if (event.getAction() == android.view.KeyEvent.ACTION_UP) dismissSheet(dialog, glass); return true;
        });
        close.requestFocus();
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            glass.setAlpha(0); glass.setTranslationY(ui.px(16)); glass.setScaleX(.975f); glass.setScaleY(.975f);
            glass.post(() -> {
                if (!dialog.isShowing() || !glass.isAttachedToWindow() || Boolean.TRUE.equals(glass.getTag())) return;
                glass.setPivotX(0); glass.setPivotY(glass.getHeight());
                glass.animate().alpha(1).translationY(0).scaleX(1).scaleY(1).setDuration(220)
                    .setInterpolator(new android.view.animation.PathInterpolator(.16f, 1f, .3f, 1f)).start();
            });
        }
    }
    private void dismissSheet(Dialog dialog, View glass) {
        if (dialog == null || !dialog.isShowing() || Boolean.TRUE.equals(glass.getTag())) return;
        glass.setTag(true); glass.animate().cancel();
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) { dialog.dismiss(); return; }
        glass.animate().alpha(0).translationY(ui.px(10)).scaleX(.985f).scaleY(.985f).setDuration(110)
            .withEndAction(() -> dialog.dismiss()).start();
    }
    private void shadowSheetText(View view) {
        if (view instanceof TextView) ((TextView) view).setShadowLayer(ui.px(1.5f), 0, ui.px(.5f), 0xAA000000);
        else if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) shadowSheetText(group.getChildAt(i));
        }
    }
    private void showNews() {
        FrameLayout content = openPanel("Actualités choisies", "");
        newsSubtitle = ui.text(newsStatus, 11, Ui.MUTED, false); ui.place(content, newsSubtitle, 50, 87, 850, 23);
        TextView sources = ui.button("Vos sources", 10, () -> showNewsSources()); ui.place(content, sources, 50, 119, 128, 29); newsSources = sources;
        newsScroll = new FocusScrollView(ui);
        newsList = new LinearLayout(this); newsList.setOrientation(LinearLayout.VERTICAL);
        newsList.setClipChildren(false); newsList.setClipToPadding(false);
        newsList.setPadding(ui.px(10), ui.px(10), ui.px(10), ui.px(10)); newsScroll.addView(newsList);
        ui.place(content, newsScroll, 40, 155, 880, 338);
        ui.place(content, ui.text("Titres et résumés des flux officiels · OK pour le détail", 9, Ui.MUTED, false), 50, 504, 810, 18);
        renderNewsRows();
        sources.setNextFocusUpId(panelClose.getId()); panelClose.setNextFocusDownId(sources.getId());
        if (!headlines.isEmpty()) newsList.getChildAt(0).requestFocusFromTouch(); else sources.requestFocusFromTouch();
    }
    private void renderNewsRows() {
        if (newsList == null) return;
        String focusedLink = newsList.hasFocus() && getCurrentFocus() != null ? String.valueOf(getCurrentFocus().getTag()) : "";
        newsList.removeAllViews(); newsSubtitle.setText(newsStatus);
        for (News.Item item : headlines) {
            FrameLayout row = new FrameLayout(this); row.setTag(item.link); ui.interactive(row, item.title + ". " + item.credit(), () -> showNewsItem(item));
            TextView credit = ui.text(item.credit() + " · " + item.date(), 9, Ui.MUTED, false); ui.place(row, credit, 17, 10, 800, 19);
            TextView title = ui.text(item.title, 14, Ui.WHITE, true); title.setSingleLine(false); title.setMaxLines(2); title.setLineSpacing(0, 1.12f); ui.place(row, title, 17, 33, 800, 42);
            new SelectionFeedback(row, 1.008f, 0, 0, (active, focused) -> row.setBackground(ui.round(active ? 0xFF303B40 : 0xFF1A2528, 13, active ? 0xB0FFFFFF : 0, active ? .6f : 0)));
            row.setBackground(ui.round(0xFF1A2528, 13, 0, 0));
            LinearLayout.LayoutParams rowLayout = new LinearLayout.LayoutParams(-1, ui.px(88)); rowLayout.bottomMargin = ui.px(9); newsList.addView(row, rowLayout);
            if (item.link.equals(focusedLink)) row.requestFocus();
        }
        for (int i = 0; i < newsList.getChildCount(); i++) {
            View row = newsList.getChildAt(i);
            row.setNextFocusUpId(i == 0 ? newsSources.getId() : newsList.getChildAt(i - 1).getId());
            row.setNextFocusDownId(newsList.getChildAt(Math.min(i + 1, newsList.getChildCount() - 1)).getId());
            row.setNextFocusLeftId(row.getId()); row.setNextFocusRightId(row.getId());
        }
        if (newsList.getChildCount() > 0) newsSources.setNextFocusDownId(newsList.getChildAt(0).getId());
        if (headlines.isEmpty()) newsList.addView(ui.text(newsStatus, 13, Ui.MUTED, false), new LinearLayout.LayoutParams(-1, ui.px(58)));
    }
    private void showNewsItem(News.Item item) {
        LinearLayout content = sheetContent();
        TextView source = ui.text(item.credit().toUpperCase(Locale.FRENCH), 8, 0xFFC6CDD4, true); source.setLetterSpacing(.08f);
        content.addView(source, new LinearLayout.LayoutParams(-1, ui.px(20)));
        TextView title = ui.text(item.title, 22, Ui.WHITE, true); title.setLetterSpacing(-.02f); title.setSingleLine(false); title.setMaxLines(5); title.setLineSpacing(0, 1.1f);
        LinearLayout.LayoutParams titleLayout = new LinearLayout.LayoutParams(-1, -2); titleLayout.topMargin = ui.px(10); content.addView(title, titleLayout);
        TextView date = ui.text("Publié le " + item.date(), 9, Ui.MUTED, false);
        LinearLayout.LayoutParams dateLayout = new LinearLayout.LayoutParams(-1, ui.px(36)); dateLayout.bottomMargin = ui.px(8); content.addView(date, dateLayout);
        addSheetBody(content, item.summary.isEmpty() ? "Le flux de cette source ne fournit pas de résumé pour ce titre." : item.summary);
        TextView domain = ui.text("Résumé du flux RSS · " + item.source.host, 8, Ui.MUTED, false);
        LinearLayout.LayoutParams domainLayout = new LinearLayout.LayoutParams(-1, ui.px(23)); domainLayout.topMargin = ui.px(10); content.addView(domain, domainLayout);
        showSheet(content, "Lire sur le site", () -> safeStart(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(item.link)), "Aucun navigateur disponible sur la TV. Source : " + item.source.host));
    }
    private void showNewsSources() {
        String[] titles = new String[News.SOURCES.length]; boolean[] values = new boolean[titles.length];
        for (int i = 0; i < titles.length; i++) { News.Source source = News.SOURCES[i]; titles[i] = source.category + " · " + source.name; values[i] = news.enabled(source); }
        dialogBuilder().setTitle("Vos sources d’actualités")
            .setMultiChoiceItems(titles, values, (dialog, index, checked) -> prefs.edit().putBoolean("news_" + News.SOURCES[index].key, checked).apply())
            .setNeutralButton("Actualiser", (dialog, which) -> { news.refresh(true); toast("Actualisation des titres…"); })
            .setPositiveButton("Terminé", (dialog, which) -> news.refresh(true)).setNegativeButton("Fermer", null).show();
    }
    private void editArtworkNotes(ArtStore.Art work) {
        EditText input = new EditText(this); input.setText(art.notes(work)); input.setTypeface(ui.body); input.setMinLines(3); input.setMaxLines(6);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)});
        dialogBuilder().setTitle("Vos notes · " + work.title).setView(input)
            .setPositiveButton("Enregistrer", (dialog, which) -> art.notes(work, input.getText().toString()))
            .setNeutralButton("Texte initial", (dialog, which) -> art.resetNotes(work)).setNegativeButton("Annuler", null).show();
    }
    private void showWeather() {
        dialogBuilder().setTitle("Météo · " + weather.city())
            .setMessage(weatherView.details() + "\n\nPrévisions Open-Meteo · open-meteo.com\nCC BY 4.0 · villes : GeoNames")
            .setNeutralButton("Changer de ville", (dialog, which) -> searchWeatherCity())
            .setNegativeButton("Actualiser", (dialog, which) -> { prefs.edit().putBoolean("widget_weather", true).apply(); weather.refresh(true); })
            .setPositiveButton("Terminé", null).show();
    }
    private void searchWeatherCity() {
        EditText input = new EditText(this); input.setText(weather.city()); input.setTypeface(ui.body);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)}); input.selectAll();
        dialogBuilder().setTitle("Ville de la météo").setView(input).setPositiveButton("Rechercher", (dialog, which) -> {
            String query = input.getText().toString().trim(); if (query.length() < 2) { toast("Saisissez au moins deux caractères."); return; }
            final int request = ++weatherSearch; toast("Recherche de la ville…");
            worker.execute(() -> {
                Weather.City[] found;
                try { found = Weather.search(query); } catch (Exception error) { found = null; }
                final Weather.City[] cities = found;
                runOnUiThread(() -> {
                    if (destroyed || !resumed || request != weatherSearch) return;
                    if (cities == null) { toast("Recherche indisponible. Vérifiez la connexion Internet."); return; }
                    if (cities.length == 0) { toast("Aucune ville trouvée. Essayez avec son pays."); return; }
                    String[] labels = new String[cities.length]; for (int i = 0; i < cities.length; i++) labels[i] = cities[i].detail;
                    dialogBuilder().setTitle("Choisir la ville").setItems(labels, (choice, index) -> weather.select(cities[index]))
                        .setNegativeButton("Annuler", null).show();
                });
            });
        }).setNegativeButton("Annuler", null).show();
    }

    private void customizeApps() {
        if (apps.isEmpty()) return;
        String[] names = new String[apps.size()]; boolean[] checked = new boolean[apps.size()]; List<String> draft = new ArrayList<>(favorites);
        for (int i = 0; i < apps.size(); i++) { names[i] = apps.get(i).name; checked[i] = draft.contains(apps.get(i).pkg); }
        dialogBuilder().setTitle("Vos essentiels · 6 applications maximum")
            .setMultiChoiceItems(names, checked, (dialog, index, enabled) -> {
                String pkg = apps.get(index).pkg;
                if (enabled && draft.size() >= 6) { ((AlertDialog) dialog).getListView().setItemChecked(index, false); toast("Retirez un favori avant d’en ajouter un."); }
                else if (enabled) draft.add(pkg); else draft.remove(pkg);
            }).setPositiveButton("Enregistrer", (dialog, which) -> {
                if (draft.isEmpty()) { toast("Conservez au moins une application."); return; }
                favorites.clear(); favorites.addAll(draft); saveFavorites(); renderFavorites();
            }).setNegativeButton("Annuler", null).show();
    }
    private void showFavoriteActions(Apps.App app, Runnable changed) {
        dialogBuilder().setTitle(app.name)
            .setItems(new String[]{"Déplacer", "Retirer des favoris"}, (dialog, which) -> {
                if (which == 0) showFavoritePositions(app);
                else { toggleFavorite(app); if (changed != null) changed.run(); }
            }).setNegativeButton("Retour", null).show();
    }
    private void showFavoritePositions(Apps.App app) {
        if (!favorites.contains(app.pkg)) return;
        String[] positions = new String[favorites.size()];
        for (int i = 0; i < positions.length; i++) {
            Apps.App current = findApp(favorites.get(i));
            positions[i] = (i + 1) + " · " + (current == null ? "Favori" : current.name);
        }
        dialogBuilder().setTitle("Déplacer · " + app.name)
            .setSingleChoiceItems(positions, favorites.indexOf(app.pkg), (dialog, position) -> {
                moveFavorite(app.pkg, position); dialog.dismiss();
            }).setNegativeButton("Annuler", null).show();
    }
    private void moveFavorite(String pkg, int position) {
        if (!favorites.contains(pkg) || position < 0 || position >= favorites.size()) return;
        favorites.remove(pkg); favorites.add(position, pkg); saveFavorites(); renderFavorites();
        int target = favorites.indexOf(pkg);
        if (panel == null && target < favoriteViews.size()) favoriteViews.get(target).post(() -> {
            if (activeDialog == null && panel == null && target < favoriteViews.size()) favoriteViews.get(target).requestFocus();
        });
    }
    private void toggleFavorite(Apps.App app) {
        if (favorites.contains(app.pkg)) { if (favorites.size() == 1) { toast("Conservez un favori."); return; } favorites.remove(app.pkg); }
        else { if (favorites.size() >= 6) { customizeApps(); return; } favorites.add(app.pkg); }
        saveFavorites(); renderFavorites(); toast("Favoris mis à jour");
    }
    private void saveFavorites() { prefs.edit().putString("favorites", new JSONArray(favorites).toString()).apply(); }
    private void showInputs() {
        try {
            TvInputManager manager = (TvInputManager) getSystemService(TV_INPUT_SERVICE); List<TvInputInfo> inputs = new ArrayList<>();
            if (manager != null) for (TvInputInfo item : manager.getTvInputList()) if (item.isPassthroughInput() && !item.isHidden(this)) inputs.add(item);
            if (inputs.isEmpty()) { Apps.App tv = findApp("com.mediatek.wwtv.tvcenter"); if (tv != null) launch(tv); else toast("Utilisez la touche Sources de la télécommande."); return; }
            String[] labels = new String[inputs.size()];
            for (int i = 0; i < inputs.size(); i++) {
                CharSequence name = inputs.get(i).loadCustomLabel(this); labels[i] = (name == null ? inputs.get(i).loadLabel(this) : name).toString();
            }
            dialogBuilder().setTitle("Sources de la télévision").setItems(labels, (dialog, index) ->
                safeStart(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputs.get(index).getId())), "Utilisez la touche Sources de la télécommande."))
                .setNegativeButton("Retour", null).show();
        } catch (RuntimeException error) { toast("Utilisez la touche Sources de la télécommande."); }
    }
    private void safeStart(Intent intent, String message) { try { startActivity(intent); } catch (RuntimeException error) { toast(message); } }
    private AlertDialog.Builder dialogBuilder() {
        if (cleanHome != null) cleanHome.closeArtwork(false);
        return new AlertDialog.Builder(this) {
            @Override public AlertDialog show() {
                if (activeDialog != null) activeDialog.dismiss();
                AlertDialog dialog = super.show(); trackDialog(dialog);
                MenuStyle.apply(dialog, ui, wallpaper);
                return dialog;
            }
        };
    }
    private void cancelViewAnimations(View view) {
        view.animate().cancel(); view.clearAnimation();
        if (view instanceof android.view.ViewGroup) { android.view.ViewGroup group=(android.view.ViewGroup)view; for(int i=0;i<group.getChildCount();i++)cancelViewAnimations(group.getChildAt(i)); }
    }
    private void trackDialog(Dialog dialog) {
        activeDialog = dialog; updateSlideshowVisibility();
        dialog.setOnDismissListener(value -> {
            if (dialog.getWindow()!=null) cancelViewAnimations(dialog.getWindow().getDecorView());
            if (dialog == controlDialog) { controlDialog = null; controlCenter = null; }
            if (activeDialog != dialog) return;
            activeDialog = null; updateSlideshowVisibility();
            if (advanceAfterDialog) { advanceAfterDialog = false; slideshow.next(false); }
        });
    }
    private void openDreamSettings() {
        Intent intent = new Intent(Settings.ACTION_DREAM_SETTINGS);
        if (intent.resolveActivity(getPackageManager()) == null) intent = new Intent().setComponent(new ComponentName(
            "com.android.tv.settings", "com.android.tv.settings.device.display.daydream.DaydreamActivity"));
        safeStart(intent, "Ouvrez les préférences d’écran de veille de la TV.");
    }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private Apps.App findApp(String pkg) { for (Apps.App app : apps) if (app.pkg.equals(pkg)) return app; return null; }
    private void focusFavorites() { if (cleanHome != null) cleanHome.focusFavorite(); else if (!favoriteViews.isEmpty()) favoriteViews.get(0).requestFocus(); }
    @Override public boolean onKeyDown(int key, android.view.KeyEvent event) {
        if (key == android.view.KeyEvent.KEYCODE_MENU && event.getRepeatCount() == 0) { showControlCenter(); return true; }
        return super.onKeyDown(key, event);
    }
    @Override public void onBackPressed() { if (cleanHome != null && cleanHome.closeArtwork(true)) return; if (panel != null) closePanel(); else focusFavorites(); }
    @Override protected void onDestroy() {
        destroyed = true; panelRequest++; weatherSearch++; MusicAccessService.forget(musicListener); musicCard.setActive(false); stopTransfer(); slideshow.close(); weather.close(); news.close(); newsView.setActive(false);
        if (activeDialog != null) activeDialog.dismiss();
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        if (listening != null) listening.close();
        greeting.close(); entrance.close(); if(homeTone!=null)homeTone.close();
        worker.shutdownNow(); wallpaper.setImage(null); super.onDestroy();
    }
}
