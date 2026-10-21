package ch.atelier.tv;

import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Native Android adaptation of Nathan Clark's CleanFrame (MIT). See assets/licenses/cleanframe. */
final class CleanFrameHome extends FrameLayout {
    private static final int DOCK_LEFT=168, DOCK_WIDTH=624, DOCK_TOP=404, DOCK_HEIGHT=52, DOCK_BOTTOM=DOCK_TOP+DOCK_HEIGHT, CARD_HEIGHT=40;
    private static final int READING_LEFT=80, READING_WIDTH=800, READING_HEIGHT=356;
    interface Actions {
        void gallery(); void applications(); void control(); void settings();
        void launch(Apps.App app); void manage(Apps.App app);
        default void artworkPanel(boolean showing) { }
    }
    final FrameLayout content;
    final TextClock clock;
    final List<View> utilities = new ArrayList<>();
    private final Ui ui;
    private final SharedPreferences prefs;
    private final ArtView painting;
    private final Actions actions;
    private final MusicCard music;
    private final TextView caption;
    private final WeatherView weather;
    private final View quotation;
    private final AdaptiveContrast contrast;
    private final List<Apps.App> apps = new ArrayList<>();
    private final List<View> cards = new ArrayList<>();
    private CleanFrameGlass glass;
    private FrameLayout track;
    private HorizontalScrollView dockScroll;
    private View plate;
    private ArtworkTextTone tone;
    private FrameLayout infoPane;
    private View greetingView;
    private View infoClose, infoNotes, infoReading;
    private ValueAnimator expansion;
    private boolean artworkOpen;
    private boolean list, plateReady;
    private String selected = "";
    private final Runnable settled = this::refreshContrast;

    CleanFrameHome(Ui ui, SharedPreferences prefs, ArtView painting, MusicCard music, TextView caption, WeatherView weather, View quotation, Actions actions) {
        super(ui.context); this.ui = ui; this.prefs = prefs; this.painting = painting;
        this.music = music; this.caption = caption; this.weather = weather; this.quotation = quotation; this.actions = actions; weather.setStartAligned(true);
        setClipChildren(false); setClipToPadding(false);
        contrast = new AdaptiveContrast(ui, painting); addView(contrast, new LayoutParams(-1, -1));
        content = SafeArea.attach(this, ui);
        clock = new TextClock(ui.context); clock.setFormat12Hour("HH:mm"); clock.setFormat24Hour("HH:mm");
        clock.setTypeface(ui.body); clock.setTextColor(Ui.WHITE); clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, ui.px(24));
        clock.setIncludeFontPadding(false); clock.setLetterSpacing(-.03f); clock.setFontFeatureSettings("'tnum' 1");
        clock.setShadowLayer(ui.px(1.5f), 0, ui.px(.5f), 0x78000000);
        caption.setTypeface(ui.body); caption.setMaxLines(1); caption.setSingleLine(true); caption.setHorizontallyScrolling(false); caption.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        caption.setAutoSizeTextTypeUniformWithConfiguration(ui.px(7.5f), ui.px(9), 1, android.util.TypedValue.COMPLEX_UNIT_PX);
        build(); contrast.follow(content);
        content.getViewTreeObserver().addOnGlobalLayoutListener(() -> { View focused = findFocus(); if (cards.contains(focused)) keepVisible(focused); if (expansion == null) refreshContrast(); });
        content.getViewTreeObserver().addOnGlobalFocusChangeListener((old, focused) -> { if (focused != null && !cards.contains(focused)) hidePlate(); });
    }
    private void place(View view, float x, float y, float w, float h) {
        if (view.getParent() instanceof ViewGroup) ((ViewGroup)view.getParent()).removeView(view);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(ui.px(w), ui.px(h)); p.leftMargin = ui.px(x); p.topMargin = ui.px(y); content.addView(view, p);
    }
    private void build() {
        cancelExpansion(); if (artworkOpen) { artworkOpen=false; actions.artworkPanel(false); }
        content.removeAllViews(); infoPane=null; dockScroll=null; utilities.clear(); cards.clear(); plateReady = false;
        list = "list".equals(prefs.getString("clean_layout", "dock"));
        clock.setGravity((list ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        place(clock, list ? 760 : 70, list ? 56 : 36, list ? 68 : 76, 32);
        place(weather, list ? 844 : 158, list ? 60 : 40, 46, 24);
        music.setCompact();
        place(music, 890-MusicCard.WIDTH, list ? 106 : 86, MusicCard.WIDTH, MusicCard.HEIGHT);
        android.widget.LinearLayout footer = new android.widget.LinearLayout(ui.context);
        footer.setGravity(Gravity.CENTER_VERTICAL); footer.setBaselineAligned(true); footer.setClipChildren(false); footer.setClipToPadding(false);
        if (quotation != null) {
            if (quotation.getParent() instanceof ViewGroup) ((ViewGroup)quotation.getParent()).removeView(quotation);
            quotation.setPadding(0,0,0,0);
            footer.addView(quotation, new android.widget.LinearLayout.LayoutParams(0,-2,.62f));
        }
        if (caption.getParent() instanceof ViewGroup) ((ViewGroup)caption.getParent()).removeView(caption);
        caption.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        android.widget.LinearLayout.LayoutParams captionLayout = new android.widget.LinearLayout.LayoutParams(quotation == null ? -1 : 0,-2,quotation == null ? 0 : .38f);
        if (quotation != null) captionLayout.leftMargin = ui.px(24);
        footer.addView(caption,captionLayout);
        FrameLayout.LayoutParams footerLayout = new FrameLayout.LayoutParams(ui.px(912),-2,Gravity.BOTTOM|Gravity.LEFT);
        footerLayout.leftMargin=ui.px(24); footerLayout.bottomMargin=ui.px(14); content.addView(footer,footerLayout);
        String[] labels = {"Galerie", "Applications", "Contrôle", "Réglages"};
        Runnable[] commands = {actions::gallery, actions::applications, actions::control, actions::settings};
        float x = 706;
        for (int i = 0; i < labels.length; i++) {
            FrameLayout utility = utility(labels[i], i, commands[i]); utilities.add(utility);
            place(utility, list ? 850 : x, list ? 274 + i * 44 : 36, 40, 40); x += 48;
        }
        // Keep zoom gutters inside the scrolling child as well as around its viewport.
        track = list ? new CleanFrameWheel(ui) : new FrameLayout(ui.context); track.setClipChildren(false); track.setClipToPadding(false);
        track.setMinimumWidth(ui.px(list ? 558 : Math.max(1, apps.size()) * 100 + 8));

        plate = new FocusDot(ui.context); plate.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); plate.setAlpha(0);
        track.addView(plate,new LayoutParams(ui.px(2),ui.px(2)));
        if (list) {
            glass = null;
            place(track, 48, 86, 578, 360);
        } else {
            glass = new CleanFrameGlass(ui, painting); place(glass, DOCK_LEFT, DOCK_TOP, DOCK_WIDTH, DOCK_HEIGHT);
            HorizontalScrollView scroll = new HorizontalScrollView(ui.context); dockScroll=scroll; scroll.setHorizontalScrollBarEnabled(false); scroll.setOverScrollMode(OVER_SCROLL_NEVER);
            scroll.setSmoothScrollingEnabled(false); scroll.setFocusable(false); scroll.setPadding(ui.px(8), 0, ui.px(8), 0); scroll.setClipToPadding(false);
            scroll.addView(track, new HorizontalScrollView.LayoutParams(ui.px(Math.max(1, apps.size()) * 100 + 8), -1)); glass.addView(scroll, new LayoutParams(ui.px(DOCK_WIDTH), ui.px(DOCK_HEIGHT), Gravity.BOTTOM|Gravity.LEFT));
        }
        for (int i = 0; i < apps.size(); i++) addApp(apps.get(i), i);
        if (list && !cards.isEmpty()) ((CleanFrameWheel)track).select(Math.max(0, cards.indexOf(selectedCard())),false);
        if (apps.isEmpty()) {
            TextView empty = ui.text("Ajoutez vos applications favorites dans Réglages.", 13, Ui.WHITE, false);
            empty.setGravity(Gravity.CENTER); track.addView(empty, new LayoutParams(ui.px(list ? 554 : 736), ui.px(100)));
        }
        if(tone!=null)tone.close();java.util.ArrayList<View> toneTargets=new java.util.ArrayList<>(utilities);toneTargets.add(clock);toneTargets.add(weather);toneTargets.add(caption);toneTargets.add(quotation);if(list)toneTargets.add(track);tone=new ArtworkTextTone(painting,toneTargets.toArray(new View[0]));
        tone.unify(utilities.toArray(new View[0])).unify(clock,weather).unify(quotation,caption);if(list)tone.unify(track);
        if (greetingView != null) attachGreeting(greetingView);
        wire(); refreshContrast();
    }
    private FrameLayout utility(String label, int kind, Runnable action) {
        FrameLayout button = new FrameLayout(ui.context); button.setClipChildren(false); button.setClipToPadding(false);
        Glyph glyph = new Glyph(ui, kind); ui.place(button, glyph, 11, 11, 18, 18);
        ui.interactive(button, label, action); button.setAlpha(.82f);
        new SelectionFeedback(button, 1.03f, 0, 0, (active, focused) -> {
            button.setAlpha(active ? 1 : .82f);
            button.setBackground(ui.round(active ? 0x30FFFFFF : 0, 20, active ? 0x55FFFFFF : 0, active ? .5f : 0));
            if (active) hidePlate(); refreshContrast();
        }); return button;
    }
    private void addApp(Apps.App app, int index) {
        FrameLayout card = new FrameLayout(ui.context); card.setClipChildren(false); card.setClipToPadding(false);
        View mark = mark(app); TextView name = ui.text(app.name, list ? 38 : 12.5f, Ui.WHITE, list);
        name.setLetterSpacing(list ? -.02f : -.022f); name.setShadowLayer(ui.px(1.5f), 0, ui.px(.5f), 0x78000000);
        if (list) { ui.place(card, mark, 14, 11, 32, 32); ui.place(card, name, 64, 0, 474, 53); }
        else { ui.place(card, mark, 24, 4, 46, 32); }
        ui.interactive(card, app.name + ". Maintenez OK pour déplacer ce favori.", () -> { closeArtwork(false); actions.launch(app); });
        card.setOnLongClickListener(view -> { closeArtwork(false); actions.manage(app); return true; });
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(ui.px(list ? 548 : 94), ui.px(list ? 54 : CARD_HEIGHT));
        p.leftMargin = ui.px(list ? 5 : 4 + index * 100); p.topMargin = ui.px(list ? 153 : 6);
        if(list){FrameLayout wrapper=new FrameLayout(ui.context);wrapper.setClipChildren(false);wrapper.addView(card,new LayoutParams(-1,-1));track.addView(wrapper,p);((CleanFrameWheel)track).register(wrapper);}
        else track.addView(card,p);
        cards.add(card);
        new SelectionFeedback(card, 1f, 0, 0, (active, focused) -> {
            mark.setAlpha(active ? 1 : .84f); name.setAlpha(active ? 1 : .84f);
            mark.animate().cancel();
            float zoom=active?(list?1.12f:1.18f):1, lift=active?-ui.px(1):0;
            if (ValueAnimator.areAnimatorsEnabled()) mark.animate().scaleX(zoom).scaleY(zoom).translationY(lift).setDuration(active?145:110).setInterpolator(new PathInterpolator(.16f,1,.3f,1)).start();
            else { mark.setScaleX(zoom); mark.setScaleY(zoom); mark.setTranslationY(lift); }
            if (active) { selected = app.pkg; if(list)((CleanFrameWheel)track).select(index,true); movePlate(card); wire(); }
            refreshContrast(); removeCallbacks(settled); postDelayed(settled, 200);
        });
        mark.addOnAttachStateChangeListener(new OnAttachStateChangeListener(){
            @Override public void onViewAttachedToWindow(View v) { }
            @Override public void onViewDetachedFromWindow(View v) { v.animate().cancel(); }
        });
    }
    private View mark(Apps.App app) {
        String key = app.pkg.equals("com.google.android.youtube.tv") ? "youtube" : app.pkg.equals("com.netflix.ninja") ? "netflix" : app.pkg.equals("com.spotify.tv.android") ? "spotify" : app.pkg.equals("com.stremio.one") ? "stremio" : app.pkg.equals("com.amazon.amazonvideo.livingroom") ? "primevideo" : "";
        if (!key.isEmpty()) {
            int id = getResources().getIdentifier("cleanframe_" + key, "drawable", ui.context.getPackageName());
            ImageView image = new ToneMark(ui); image.setImageResource(id); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setPadding(ui.px(list ? 0 : 7), 0, ui.px(list ? 0 : 7), 0); image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); return image;
        }
        TextView label = ui.text(app.pkg.equals("com.canal.android.canal") ? list ? "C+" : "CANAL+" : app.name, list ? 11 : 10.5f, Ui.WHITE, true);
        label.setGravity(Gravity.CENTER); label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); ui.fit(label); return label;
    }
    private void movePlate(View card) {
        plate.animate().cancel();
        FrameLayout.LayoutParams position = (FrameLayout.LayoutParams) (list ? ((View)card.getParent()).getLayoutParams() : card.getLayoutParams());
        float x=position.leftMargin+ui.px(list?4:46), y=position.topMargin+ui.px(list?26:41);
        if (!plateReady || !ValueAnimator.areAnimatorsEnabled()) { plate.setTranslationX(x); plate.setTranslationY(y); plate.setAlpha(.65f); }
        else plate.animate().translationX(x).translationY(y).alpha(.65f).setDuration(145).setInterpolator(new PathInterpolator(.16f,1,.3f,1)).start();
        plateReady = true;
        // Native focus scrolling waits for the whole card, including the small zoom gutter.
        card.postOnAnimation(() -> { if (card.hasFocus()) keepVisible(card); });
    }
    private void keepVisible(View card) {
        if (track == null || card.getWidth() == 0 || card.getParent() != track) return;
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams)card.getLayoutParams();
        View parent = (View)track.getParent(); int gutter = ui.px(4);
        if (parent instanceof HorizontalScrollView) {
            HorizontalScrollView scroll = (HorizontalScrollView)parent;
            int width = scroll.getWidth() - scroll.getPaddingLeft() - scroll.getPaddingRight();
            int left = p.leftMargin - gutter, right = p.leftMargin + card.getWidth() + gutter, x = scroll.getScrollX();
            int next = left < x ? left : right > x + width ? right - width : x;
            scroll.scrollTo(Math.max(0, Math.min(Math.max(0, track.getWidth() - width), next)), 0);
        } else if (parent instanceof ScrollView) {
            ScrollView scroll = (ScrollView)parent;
            int height = scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom();
            int top = p.topMargin - gutter, bottom = p.topMargin + card.getHeight() + gutter, y = scroll.getScrollY();
            int next = top < y ? top : bottom > y + height ? bottom - height : y;
            scroll.scrollTo(0, Math.max(0, Math.min(Math.max(0, track.getHeight() - height), next)));
        }
    }
    private void hidePlate() { if (plate != null) { plate.animate().cancel(); plate.setAlpha(0); } }
    private View selectedCard() { for (int i = 0; i < Math.min(apps.size(), cards.size()); i++) if (apps.get(i).pkg.equals(selected)) return cards.get(i); return cards.isEmpty() ? utilities.get(0) : cards.get(0); }
    private void wire() {
        if (utilities.isEmpty()) return;
        View favorite = selectedCard();
        for (int i = 0; i < utilities.size(); i++) {
            View utility = utilities.get(i);
            utility.setNextFocusLeftId(list ? favorite.getId() : utilities.get(Math.max(0, i - 1)).getId());
            utility.setNextFocusRightId(list ? utility.getId() : utilities.get(Math.min(utilities.size() - 1, i + 1)).getId());
            utility.setNextFocusUpId(list && i > 0 ? utilities.get(i - 1).getId() : list && music.getVisibility()==VISIBLE ? music.getId() : utility.getId());
            utility.setNextFocusDownId(list && i < utilities.size() - 1 ? utilities.get(i + 1).getId() : !list && music.getVisibility()==VISIBLE ? music.getId() : favorite.getId());
        }
        boolean showQuote = quotation != null && quotation.getVisibility() == VISIBLE;
        View footer = showQuote ? quotation : caption;
        boolean showCaption = showQuote || caption.getVisibility() == VISIBLE;
        for (int i = 0; i < cards.size(); i++) {
            View card = cards.get(i);
            card.setNextFocusLeftId(list ? footer.getId() : cards.get(Math.max(0, i - 1)).getId());
            card.setNextFocusRightId(list ? utilities.get(0).getId() : cards.get(Math.min(cards.size() - 1, i + 1)).getId());
            card.setNextFocusUpId(artworkOpen ? infoClose.getId() : list ? cards.get((i-1+cards.size())%cards.size()).getId() : music.getVisibility()==VISIBLE ? music.getId() : utilities.get(0).getId());
            card.setNextFocusDownId(list ? cards.get((i+1)%cards.size()).getId() : showCaption ? footer.getId() : card.getId());
        }
        if (quotation != null) { quotation.setNextFocusUpId(favorite.getId()); quotation.setNextFocusDownId(quotation.getId()); quotation.setNextFocusLeftId(quotation.getId()); quotation.setNextFocusRightId(caption.getVisibility() == VISIBLE ? caption.getId() : quotation.getId()); }
        caption.setNextFocusUpId(artworkOpen ? infoClose.getId() : favorite.getId()); caption.setNextFocusDownId(caption.getId()); caption.setNextFocusRightId(utilities.get(utilities.size() - 1).getId()); caption.setNextFocusLeftId(showQuote ? quotation.getId() : caption.getId());
        if (music.getVisibility() == VISIBLE) {
            if(list)utilities.get(0).setNextFocusUpId(music.getId());
            music.setNextFocusUpId(utilities.get(list?0:utilities.size()-1).getId());
            music.setNextFocusLeftId(favorite.getId());music.setNextFocusRightId(utilities.get(utilities.size()-1).getId());music.returnTo(favorite.getId());
        }
    }
    void attachGreeting(View greeting) {
        greetingView=greeting;
        if (greeting.getParent() instanceof ViewGroup) ((ViewGroup)greeting.getParent()).removeView(greeting);
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(ui.px(640),-2,Gravity.CENTER); content.addView(greeting,params);
    }
    boolean isArtworkOpen() { return artworkOpen; }
    boolean isArtworkCoveringHome() { return artworkOpen || glass != null && glass.getLayoutParams().height>ui.px(DOCK_HEIGHT)+1; }
    boolean showArtwork(ArtStore.Art work, String notes, String reflection, Runnable editNotes) {
        if (list || glass == null) return false;
        if (artworkOpen) { infoClose.requestFocus(); return true; }
        float previousAlpha=infoPane==null?0:infoPane.getAlpha();
        if (infoPane != null) glass.removeView(infoPane);
        infoPane = new FrameLayout(ui.context); infoPane.setAlpha(previousAlpha); infoPane.setClipChildren(true); infoPane.setClipToPadding(true);
        android.widget.LinearLayout body=new android.widget.LinearLayout(ui.context); body.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.LinearLayout header=new android.widget.LinearLayout(ui.context); header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView image=new ImageView(ui.context); image.setImageBitmap(painting.currentImage()); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(image,new android.widget.LinearLayout.LayoutParams(ui.px(128),ui.px(72)));
        android.widget.LinearLayout identity=new android.widget.LinearLayout(ui.context); identity.setOrientation(android.widget.LinearLayout.VERTICAL);
        TextView title=ui.text(work.title,22,Ui.WHITE,true); title.setSingleLine(false);title.setMaxLines(Integer.MAX_VALUE);title.setEllipsize(null);title.setLetterSpacing(-.025f);
        identity.addView(title,new android.widget.LinearLayout.LayoutParams(-1,-2));
        TextView credit=ui.text(work.credit(),11.5f,Ui.MUTED,false);credit.setSingleLine(false);credit.setMaxLines(Integer.MAX_VALUE);credit.setEllipsize(null);
        android.widget.LinearLayout.LayoutParams creditLayout=new android.widget.LinearLayout.LayoutParams(-1,-2);creditLayout.topMargin=ui.px(5);identity.addView(credit,creditLayout);
        android.widget.LinearLayout.LayoutParams identityLayout=new android.widget.LinearLayout.LayoutParams(0,-2,1);identityLayout.leftMargin=ui.px(18);header.addView(identity,identityLayout);
        body.addView(header,new android.widget.LinearLayout.LayoutParams(-1,-2));
        String description=notes.isEmpty()?"Une œuvre de votre collection personnelle.":notes;
        TextView text=ui.text(description+(reflection.isEmpty()?"":"\n\nUNE LECTURE POSSIBLE\n\n"+reflection),13.5f,Ui.WHITE,false);
        text.setSingleLine(false);text.setMaxLines(Integer.MAX_VALUE);text.setEllipsize(null);text.setLineSpacing(0,1.35f);text.setGravity(Gravity.TOP);
        android.widget.LinearLayout.LayoutParams textLayout=new android.widget.LinearLayout.LayoutParams(-1,-2);textLayout.topMargin=ui.px(15);body.addView(text,textLayout);
        body.setPadding(0,0,0,ui.px(12));
        ScrollView reading=new ScrollView(ui.context){
            @Override protected void dispatchDraw(Canvas canvas){int save=canvas.save();canvas.clipRect(getScrollX(),getScrollY(),getScrollX()+getWidth(),getScrollY()+getHeight());super.dispatchDraw(canvas);canvas.restoreToCount(save);}
        };
        reading.setId(View.generateViewId());reading.setFocusable(true);reading.setDefaultFocusHighlightEnabled(false);reading.setVerticalScrollBarEnabled(false);reading.setOverScrollMode(OVER_SCROLL_NEVER);
        reading.setPadding(ui.px(4),ui.px(2),ui.px(4),ui.px(6));reading.addView(body,new ScrollView.LayoutParams(-1,-2));
        reading.setOnKeyListener((v,code,event)->{
            if(event.getAction()!=android.view.KeyEvent.ACTION_DOWN)return false;
            if(code==android.view.KeyEvent.KEYCODE_DPAD_LEFT||code==android.view.KeyEvent.KEYCODE_DPAD_RIGHT){infoClose.requestFocus();return true;}
            if(code==android.view.KeyEvent.KEYCODE_DPAD_DOWN&&!reading.canScrollVertically(1)){infoNotes.requestFocus();return true;}
            if(code==android.view.KeyEvent.KEYCODE_DPAD_UP&&!reading.canScrollVertically(-1)){infoClose.requestFocus();return true;}
            return false;
        });
        reading.setOnFocusChangeListener((v,focused)->reading.setBackground(ui.round(focused?0x0CFFFFFF:0,8,focused?0x30FFFFFF:0,.5f)));
        int actionHeight=ui.px(Math.max(30,26*getResources().getConfiguration().fontScale)), actionBottom=ui.px(DOCK_HEIGHT+18);
        FrameLayout.LayoutParams readLayout=new FrameLayout.LayoutParams(-1,-1);readLayout.leftMargin=readLayout.rightMargin=ui.px(22);readLayout.topMargin=ui.px(20);readLayout.bottomMargin=actionBottom+actionHeight+ui.px(14);infoPane.addView(reading,readLayout);infoReading=reading;
        TextView edit=ui.button("Vos notes",11,()->{closeArtwork(false);editNotes.run();});ui.fit(edit);infoNotes=edit;
        TextView close=ui.button("Fermer",11,()->closeArtwork(true));ui.fit(close);infoClose=close;
        FrameLayout.LayoutParams editLayout=new FrameLayout.LayoutParams(ui.px(118),actionHeight,Gravity.RIGHT|Gravity.BOTTOM);editLayout.rightMargin=ui.px(140);editLayout.bottomMargin=actionBottom;infoPane.addView(edit,editLayout);
        FrameLayout.LayoutParams closeLayout=new FrameLayout.LayoutParams(ui.px(100),actionHeight,Gravity.RIGHT|Gravity.BOTTOM);closeLayout.rightMargin=ui.px(22);closeLayout.bottomMargin=actionBottom;infoPane.addView(close,closeLayout);
        View divider=new View(ui.context);divider.setBackgroundColor(0x24FFFFFF);FrameLayout.LayoutParams line=new FrameLayout.LayoutParams(-1,ui.px(.5f),Gravity.BOTTOM);line.leftMargin=line.rightMargin=ui.px(22);line.bottomMargin=ui.px(DOCK_HEIGHT+9);infoPane.addView(divider,line);
        // Reading layout stays fixed while the material opens around it: no text reflow each frame.
        glass.addView(infoPane,0,new LayoutParams(ui.px(READING_WIDTH),ui.px(READING_HEIGHT),Gravity.TOP|Gravity.LEFT));
        artworkOpen=true; actions.artworkPanel(true); wire();
        close.setNextFocusLeftId(edit.getId());edit.setNextFocusRightId(close.getId());close.setNextFocusRightId(close.getId());edit.setNextFocusLeftId(edit.getId());
        for(View control:new View[]{close,edit}){control.setNextFocusUpId(reading.getId());control.setNextFocusDownId(selectedCard().getId());}
        reading.setNextFocusDownId(edit.getId()); reading.setNextFocusUpId(close.getId());
        close.requestFocus(); morph(true,true); return true;
    }
    boolean closeArtwork(boolean animate) {
        if (!artworkOpen) return false;
        artworkOpen=false; wire(); actions.artworkPanel(false); morph(false,animate);
        if (animate && caption.isShown()) caption.requestFocus(); return true;
    }
    private void cancelExpansion(){ if(expansion!=null){expansion.removeAllListeners();expansion.removeAllUpdateListeners();expansion.cancel();expansion=null;} }
    private void applyGlassBounds(int height,int left,int width){
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)glass.getLayoutParams();lp.height=height;lp.topMargin=ui.px(DOCK_BOTTOM)-height;lp.leftMargin=left;lp.width=width;glass.setLayoutParams(lp);
        // The surface grows around the fixed app row; favourites never slide during reading.
        if(dockScroll!=null){FrameLayout.LayoutParams row=(FrameLayout.LayoutParams)dockScroll.getLayoutParams();row.leftMargin=ui.px(DOCK_LEFT)-left;dockScroll.setLayoutParams(row);}
    }
    private void morph(boolean open, boolean animate){
        cancelExpansion(); if(glass==null)return;
        FrameLayout.LayoutParams bounds=(FrameLayout.LayoutParams)glass.getLayoutParams();
        final int start=bounds.height,fromLeft=bounds.leftMargin,fromWidth=bounds.width;
        final int target=ui.px(open?READING_HEIGHT:DOCK_HEIGHT),toLeft=ui.px(open?READING_LEFT:DOCK_LEFT),toWidth=ui.px(open?READING_WIDTH:DOCK_WIDTH);
        final FrameLayout pane=infoPane;final float to=open?1:0;
        glass.setMorphing(true);
        if(!animate||!ValueAnimator.areAnimatorsEnabled()){
            applyGlassBounds(target,toLeft,toWidth);if(pane!=null){pane.setAlpha(to);pane.setVisibility(open?VISIBLE:GONE);}glass.setMorphing(false);refreshContrast();actions.artworkPanel(artworkOpen);return;
        }
        if(pane!=null){pane.setVisibility(VISIBLE);if(open&&start==ui.px(DOCK_HEIGHT))pane.setAlpha(0);}
        final float alpha=pane==null?0:pane.getAlpha();
        expansion=ValueAnimator.ofFloat(0,1);expansion.setDuration(open?280:200);expansion.setInterpolator(new PathInterpolator(.22f,1,.36f,1));
        expansion.addUpdateListener(value->{float p=(float)value.getAnimatedValue();applyGlassBounds(Math.round(start+(target-start)*p),Math.round(fromLeft+(toLeft-fromLeft)*p),Math.round(fromWidth+(toWidth-fromWidth)*p));if(pane!=null){float textProgress=open?Math.max(0,Math.min(1,(p-.12f)/.88f)):p;pane.setAlpha(alpha+(to-alpha)*textProgress);}});
        expansion.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator animation){expansion=null;if(pane!=null&&!artworkOpen)pane.setVisibility(GONE);glass.setMorphing(false);refreshContrast();actions.artworkPanel(artworkOpen);}});expansion.start();
    }
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event){
        if(artworkOpen&&event.getKeyCode()==android.view.KeyEvent.KEYCODE_BACK){if(event.getAction()==android.view.KeyEvent.ACTION_UP)closeArtwork(true);return true;}
        return super.dispatchKeyEvent(event);
    }
    void render(List<Apps.App> values, List<View> focusTargets) {
        String before = selected; apps.clear(); apps.addAll(values); build(); selected = before;
        focusTargets.clear(); focusTargets.addAll(cards);
    }
    void refreshWidgets() { wire(); refreshContrast(); }
    void focusFavorite() { if (artworkOpen) infoClose.requestFocus(); else selectedCard().requestFocus(); }
    void artworkChanged() { if(tone!=null)tone.artworkChanged();contrast.artworkChanged(); if (glass != null) glass.artworkChanged(); }
    void refreshContrast() { if(tone!=null)tone.refresh();contrast.refresh(); }
    @Override protected void onDetachedFromWindow() { removeCallbacks(settled); if(tone!=null)tone.close(); cancelExpansion(); if (plate != null) plate.animate().cancel(); super.onDetachedFromWindow(); }

    /** A quiet location cue; the logo carries the selection, not a surrounding surface. */
    private static final class FocusDot extends View implements ArtworkTextTone.Symbol {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        FocusDot(android.content.Context context){super(context);paint.setColor(Ui.WHITE);}
        public int artworkColor(){return paint.getColor();}
        public void setArtworkColor(int color){paint.setColor(color);invalidate();}
        @Override protected void onDraw(Canvas canvas){canvas.drawCircle(getWidth()/2f,getHeight()/2f,Math.min(getWidth(),getHeight())/2f,paint);}
    }
    private static final class ToneMark extends ImageView implements ArtworkTextTone.Symbol {
        private int color=Ui.WHITE;
        ToneMark(Ui ui){super(ui.context);}
        public int artworkColor(){return color;}
        public void setArtworkColor(int value){color=value;setImageTintList(android.content.res.ColorStateList.valueOf(value));}
    }
    private static final class Glyph extends View implements ArtworkTextTone.Symbol {
        private int color=Ui.WHITE;
        public int artworkColor(){return color;}
        public void setArtworkColor(int value){color=value;invalidate();}
        private final Paint paint = new Paint(3); private final Path path = new Path(); private final int kind;
        Glyph(Ui ui, int kind) { super(ui.context); this.kind = kind; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            canvas.save(); canvas.scale(getWidth()/24f, getHeight()/24f); paint.setStyle(Paint.Style.STROKE); paint.setStrokeJoin(Paint.Join.ROUND); paint.setStrokeCap(Paint.Cap.ROUND);
            for (int pass = 0; pass < 1; pass++) {
                paint.setColor(color); paint.setStrokeWidth(1.6f);
                if (kind == 0) { canvas.drawRoundRect(3.5f,4.5f,20.5f,19.5f,2,2,paint); canvas.drawCircle(9,9.8f,1.5f,paint); path.reset(); path.moveTo(4.5f,17.5f); path.lineTo(9,13); path.lineTo(12,16); path.lineTo(14.5f,13.5f); path.lineTo(19.5f,18.5f); canvas.drawPath(path,paint); }
                else if (kind == 1) { for (int y = 0; y < 2; y++) for(int x = 0; x < 2; x++) canvas.drawRoundRect(4+x*9.5f,4+y*9.5f,10.5f+x*9.5f,10.5f+y*9.5f,1.6f,1.6f,paint); }
                else if (kind == 2) { canvas.drawRoundRect(3,4.5f,21,17,2,2,paint); canvas.drawLine(8,21,16,21,paint); canvas.drawLine(12,17,12,21,paint); }
                else {
                    path.reset();
                    for(int i=0;i<48;i++){double angle=Math.toRadians(-90+i*7.5f);int tooth=i%6;float radius=tooth==1||tooth==2||tooth==3?9.4f:7.4f;float x=12+(float)Math.cos(angle)*radius,y=12+(float)Math.sin(angle)*radius;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}
                    path.close();canvas.drawPath(path,paint);canvas.drawCircle(12,12,3.2f,paint);
                }
            }
            canvas.restore();
        }
    }
}
