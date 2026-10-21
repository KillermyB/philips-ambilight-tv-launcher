package ch.atelier.tv;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;
import android.widget.TextView;

/** Keep Android's tested dialog behaviour, with the same glass and typography as the Home. */
final class MenuStyle {
    static void apply(AlertDialog dialog, Ui ui, ArtView painting) {
        Window window = dialog.getWindow(); if (window == null) return;
        FrameLayout container = window.findViewById(android.R.id.content);
        if (container == null || container.getChildCount() == 0) return;
        View original = container.getChildAt(0); container.removeView(original);
        GlassPanel glass = new GlassPanel(ui, painting, true); glass.addView(original, new FrameLayout.LayoutParams(-1, -2));
        int safe = SafeArea.percent(ui.context.getSharedPreferences("atelier", 0));
        int maximumHeight = ui.px(460 - safe * 10.8f);
        FrameLayout bounded = new FrameLayout(ui.context) {
            @Override protected void onMeasure(int width, int height) {
                int available = View.MeasureSpec.getMode(height) == View.MeasureSpec.UNSPECIFIED ? maximumHeight : Math.min(maximumHeight, View.MeasureSpec.getSize(height));
                super.onMeasure(width, View.MeasureSpec.makeMeasureSpec(available, View.MeasureSpec.AT_MOST));
            }
        };
        bounded.setClipChildren(false); bounded.setClipToPadding(false);
        bounded.addView(glass, new FrameLayout.LayoutParams(-1, -2));
        container.addView(bounded, new FrameLayout.LayoutParams(-1, -2));
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); window.getDecorView().setPadding(0, 0, 0, 0);
        window.setLayout(ui.px(570 * (1 - safe / 50f)), WindowManager.LayoutParams.WRAP_CONTENT); window.setWindowAnimations(0);
        WindowManager.LayoutParams attributes = window.getAttributes(); attributes.dimAmount = .14f; attributes.gravity = Gravity.CENTER; window.setAttributes(attributes);
        int titleId = ui.context.getResources().getIdentifier("alertTitle", "id", "android");
        rebuildButtons(dialog, original, ui);
        style(original, ui, titleId);
        wireMessage(dialog, ui);
        ListView list = dialog.getListView();
        if (list != null) {
            android.graphics.drawable.StateListDrawable selection = new android.graphics.drawable.StateListDrawable();
            selection.addState(new int[]{android.R.attr.state_focused}, ui.round(0x30FFFFFF, 10, 0, 0));
            selection.addState(new int[]{android.R.attr.state_pressed}, ui.round(0x30FFFFFF, 10, 0, 0));
            selection.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
            list.setSelector(selection); list.setDivider(new ColorDrawable(Color.TRANSPARENT)); list.setDividerHeight(ui.px(2));
            list.setPadding(ui.px(10), ui.px(3), ui.px(10), ui.px(6)); list.setClipToPadding(true); list.setClipChildren(true);
            list.setVerticalScrollBarEnabled(false); list.setVerticalFadingEdgeEnabled(true); list.setFadingEdgeLength(ui.px(10));
            list.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                @Override public void onChildViewAdded(View parent, View child) { style(child, ui, titleId); }
                @Override public void onChildViewRemoved(View parent, View child) { }
            });
            list.post(() -> { for (int i = 0; i < list.getChildCount(); i++) style(list.getChildAt(i), ui, titleId); });
        }
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            glass.setAlpha(0); glass.setScaleX(.985f); glass.setScaleY(.985f);
            glass.animate().alpha(1).scaleX(1).scaleY(1).setDuration(180)
                .setInterpolator(new android.view.animation.PathInterpolator(.16f, 1f, .3f, 1f)).start();
        }
    }
    private static void wireMessage(AlertDialog dialog, Ui ui) {
        TextView message=dialog.findViewById(android.R.id.message);if(message==null||message.length()==0||!message.isShown())return;
        android.view.ViewParent ancestor=message.getParent();
        while(ancestor instanceof View && !(ancestor instanceof android.widget.ScrollView))ancestor=ancestor.getParent();
        if(!(ancestor instanceof android.widget.ScrollView))return;
        final android.widget.ScrollView reader=(android.widget.ScrollView)ancestor;
        if(reader.getId()==View.NO_ID)reader.setId(View.generateViewId());
        reader.setFocusable(true);reader.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);reader.setDefaultFocusHighlightEnabled(false);reader.setSmoothScrollingEnabled(false);
        reader.setContentDescription("Texte. Haut et Bas pour lire ; Gauche ou Droite pour retrouver les boutons.");
        List<Button> buttons=new ArrayList<>();for(int which:new int[]{AlertDialog.BUTTON_NEUTRAL,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_POSITIVE}){Button button=dialog.getButton(which);if(button!=null&&button.getVisibility()==View.VISIBLE)buttons.add(button);}
        if(buttons.isEmpty())return;final Button primary=buttons.get(buttons.size()-1);
        for(int i=0;i<buttons.size();i++){Button button=buttons.get(i);button.setNextFocusUpId(reader.getId());button.setNextFocusLeftId(buttons.get(Math.max(0,i-1)).getId());button.setNextFocusRightId(buttons.get(Math.min(buttons.size()-1,i+1)).getId());}
        reader.setNextFocusUpId(primary.getId());reader.setNextFocusDownId(primary.getId());reader.setNextFocusLeftId(primary.getId());reader.setNextFocusRightId(primary.getId());
        reader.setOnFocusChangeListener((v,focus)->reader.setBackground(ui.round(focus?0x08FFFFFF:0,8,focus?0x60FFFFFF:0,focus?.5f:0)));
        reader.setOnKeyListener((v,code,event)->{
            if(code!=android.view.KeyEvent.KEYCODE_DPAD_UP&&code!=android.view.KeyEvent.KEYCODE_DPAD_DOWN&&code!=android.view.KeyEvent.KEYCODE_DPAD_LEFT&&code!=android.view.KeyEvent.KEYCODE_DPAD_RIGHT)return false;
            if(event.getAction()!=android.view.KeyEvent.ACTION_DOWN)return true;
            if(code==android.view.KeyEvent.KEYCODE_DPAD_LEFT||code==android.view.KeyEvent.KEYCODE_DPAD_RIGHT)return primary.requestFocus();
            int direction=code==android.view.KeyEvent.KEYCODE_DPAD_UP?-1:1;
            if(!reader.canScrollVertically(direction))return primary.requestFocus();
            reader.scrollBy(0,direction*Math.max(ui.px(40),reader.getHeight()/3));return true;
        });
    }
    private static void rebuildButtons(AlertDialog dialog, View original, Ui ui) {
        int panelId = ui.context.getResources().getIdentifier("buttonPanel", "id", "android");
        View panel = original.findViewById(panelId); if (!(panel instanceof ViewGroup)) return;
        List<Button> buttons = new ArrayList<>();
        for (int which : new int[]{AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_POSITIVE}) {
            Button button = dialog.getButton(which);
            if (button != null && button.getVisibility() == View.VISIBLE) buttons.add(button);
        }
        if (buttons.isEmpty()) return;
        for (Button button : buttons) if (button.getParent() instanceof ViewGroup) ((ViewGroup)button.getParent()).removeView(button);
        // AlertDialog reserves the panel minimum before allocating the scrolling list.
        // Reserve the complete footer, including the focus gutter and bottom breathing room.
        ViewGroup footer = (ViewGroup) panel; footer.removeAllViews(); footer.setPadding(0, 0, 0, 0); footer.setMinimumHeight(ui.px(64));
        LinearLayout row = new LinearLayout(ui.context); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER);
        row.setPadding(ui.px(18), ui.px(10), ui.px(18), ui.px(18)); row.setClipChildren(false); row.setClipToPadding(false);
        for (int i = 0; i < buttons.size(); i++) {
            Button button = buttons.get(i); button.setMinWidth(0); button.setMinimumWidth(0);
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(ui.px(buttons.size() == 1 ? 120 : 140), ui.px(36));
            if (i > 0) layout.leftMargin = ui.px(10); row.addView(button, layout);
        }
        footer.addView(row, new ViewGroup.LayoutParams(-1, -2));
    }
    private static void style(View view, Ui ui, int titleId) {
        if (view instanceof TextView) {
            TextView text = (TextView) view; boolean title = view.getId() == titleId;
            text.setTypeface(title ? ui.medium : ui.body);
            text.setTextColor(Ui.WHITE); text.setIncludeFontPadding(true);
            text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, ui.px(title ? 22 : view instanceof Button ? 11 : view instanceof CheckedTextView ? 12.5f : 12) * ui.context.getResources().getConfiguration().fontScale);
            text.setSingleLine(false); text.setMaxLines(title ? 3 : view instanceof Button ? 1 : view.getId()==android.R.id.message ? Integer.MAX_VALUE : 4);
            if(view.getId()==android.R.id.message)text.setEllipsize(null);
            if (title) text.setLetterSpacing(-.025f);
            if (view.getParent() instanceof ListView) {
                text.setMinHeight(ui.px(38)); text.setMinimumHeight(ui.px(38));
                text.setPadding(ui.px(18), ui.px(6), ui.px(18), ui.px(6));
            }
            if (view instanceof EditText) ((EditText) view).setHintTextColor(Ui.MUTED);
            if (view instanceof Button && !Boolean.TRUE.equals(view.getTag())) {
                Button button = (Button) view; button.setTag(true); button.setAllCaps(false); button.setDefaultFocusHighlightEnabled(false);
                button.setStateListAnimator(null); button.setElevation(0); button.setMinHeight(ui.px(34)); button.setMinimumHeight(ui.px(34)); button.setPadding(ui.px(14), ui.px(6), ui.px(14), ui.px(6));
                button.setBackground(ui.round(button.hasFocus() ? Ui.WHITE : 0x18FFFFFF, 18, 0, 0));
                if (button.hasFocus()) button.setTextColor(Ui.INK);
                ui.fit(button);
                new SelectionFeedback(button, 1.015f, 0, 0, (selected, focused) -> {
                    button.setBackground(ui.round(selected ? Ui.WHITE : 0x18FFFFFF, 18, 0, 0)); button.setTextColor(selected ? Ui.INK : Ui.WHITE);
                });
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            boolean scrolling = view instanceof ListView || view instanceof android.widget.ScrollView;
            group.setClipChildren(scrolling); group.setClipToPadding(scrolling);
            for (int i = 0; i < group.getChildCount(); i++) style(group.getChildAt(i), ui, titleId);
        }
    }
}
