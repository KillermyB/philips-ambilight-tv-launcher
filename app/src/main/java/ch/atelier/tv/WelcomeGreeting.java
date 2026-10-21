package ch.atelier.tv;

import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Calendar;

/** A short welcome at launch or wake, never on ordinary Home navigation. */
final class WelcomeGreeting {
    final LinearLayout view;
    private final TextView salutation, message;
    private final Ui ui;
    private final boolean centered;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    // Android can create separate launcher and HOME tasks in the same process.
    private static boolean pending = true;
    private boolean homeVisible, artworkReady, closed;
    private final Runnable reveal = this::show;
    private final Runnable dismiss = this::dismiss;
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) { pending = true; hide(); }
            else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) schedule();
        }
    };

    WelcomeGreeting(Ui ui, SharedPreferences prefs) { this(ui, prefs, false); }
    WelcomeGreeting(Ui ui, SharedPreferences prefs, boolean centered) {
        this.centered = centered; this.ui = ui; this.prefs = prefs;
        view = new LinearLayout(ui.context); view.setOrientation(LinearLayout.VERTICAL);
        View line = new View(ui.context); line.setBackgroundColor(0x90FFFFFF);
        LinearLayout.LayoutParams lineLayout = new LinearLayout.LayoutParams(ui.px(24), ui.px(1));
        lineLayout.bottomMargin = ui.px(9); if (!centered) view.addView(line, lineLayout);
        if (centered) { view.setGravity(android.view.Gravity.CENTER); view.setTag("atelier-greeting"); }
        salutation = ui.text("", 8.5f, 0xFFE2E7E7, true); salutation.setLetterSpacing(.16f);
        view.addView(salutation, new LinearLayout.LayoutParams(-2, -2));
        message = ui.text("", centered ? 22 : 25, Ui.WHITE, false); message.setLetterSpacing(-.025f);
        message.setMaxWidth(ui.px(520)); message.setSingleLine(false); message.setMaxLines(2); message.setLineSpacing(0, 1.1f);
        LinearLayout.LayoutParams messageLayout = new LinearLayout.LayoutParams(-2, -2); messageLayout.topMargin = ui.px(7);
        view.addView(message, messageLayout);
        if (centered) { salutation.setGravity(android.view.Gravity.CENTER); message.setGravity(android.view.Gravity.CENTER); }
        for (TextView text : new TextView[]{salutation, message}) text.setShadowLayer(ui.px(3), 0, ui.px(1), 0xA6000000);
        view.setVisibility(View.GONE);
        view.setFocusable(false); view.setClickable(false);
        view.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) ui.context.registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED);
        else ui.context.registerReceiver(screen, filter);
    }

    void artworkReady() { artworkReady = true; schedule(); }
    void setHomeVisible(boolean visible) {
        homeVisible = visible;
        if (!visible || !prefs.getBoolean("welcome_enabled", true)) hide(); else schedule();
    }
    private void schedule() {
        handler.removeCallbacks(reveal);
        if (!closed && pending && homeVisible && artworkReady && prefs.getBoolean("welcome_enabled", true) &&
            ((PowerManager) ui.context.getSystemService(Context.POWER_SERVICE)).isInteractive())
            handler.postDelayed(reveal, 260);
    }
    static String message(int hour) {
        if (hour >= 5 && hour < 12) return "La journée est à vous.";
        if (hour >= 12 && hour < 18) return "Faites une pause. Le reste attendra.";
        if (hour >= 18 && hour < 22) return "La soirée peut commencer.";
        return "Le monde peut attendre.";
    }
    static String salutation(int hour) {
        if (hour >= 5 && hour < 12) return "BONJOUR";
        if (hour >= 12 && hour < 18) return "BON APRÈS-MIDI";
        if (hour >= 18 && hour < 22) return "BONSOIR";
        return "BONNE NUIT";
    }
    private void show() {
        if (closed || !pending || !homeVisible || !prefs.getBoolean("welcome_enabled", true)) return;
        pending = false; view.animate().cancel();
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        salutation.setText(salutation(hour)); message.setText(message(hour));
        view.setVisibility(View.VISIBLE); view.setAlpha(1f); view.setTranslationY(0);
        if (ValueAnimator.areAnimatorsEnabled()) {
            view.setAlpha(0); view.setTranslationY(ui.px(6));
            view.animate().alpha(1f).translationY(0).setDuration(240).setInterpolator(new DecelerateInterpolator()).start();
            message.setAlpha(0); message.setTranslationY(ui.px(3));
            message.animate().alpha(1f).translationY(0).setStartDelay(55).setDuration(260).start();
        }
        handler.postDelayed(dismiss, centered ? 2600 : 4200);
    }
    private void dismiss() {
        if (!ValueAnimator.areAnimatorsEnabled()) { hide(); return; }
        view.animate().cancel();
        view.animate().alpha(0).translationY(ui.px(4)).setDuration(180)
            .withEndAction(() -> view.setVisibility(View.GONE)).start();
    }
    private void hide() {
        handler.removeCallbacks(reveal); handler.removeCallbacks(dismiss);
        view.animate().cancel(); message.animate().cancel(); message.setAlpha(1); message.setTranslationY(0); view.setVisibility(View.GONE);
    }
    void close() { closed = true; hide(); ui.context.unregisterReceiver(screen); }
}
