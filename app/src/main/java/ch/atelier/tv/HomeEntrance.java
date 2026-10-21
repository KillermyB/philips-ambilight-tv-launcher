package ch.atelier.tv;

import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PowerManager;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

/** A brief entrance on launch/wake; ordinary Home presses stay immediate. */
final class HomeEntrance {
    private final Ui ui;
    private final FrameLayout home;
    // Philips may create separate launcher and HOME tasks in the same process.
    private static boolean pending = true;
    private boolean ready, visible, closed, screenOn;
    private final Runnable reveal = this::show;
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                screenOn = false; pending = true; home.removeCallbacks(reveal); reset();
            } else { screenOn = true; schedule(); }
        }
    };
    HomeEntrance(Ui ui, FrameLayout home) {
        this.ui = ui; this.home = home;
        screenOn = ui.context.getSystemService(PowerManager.class).isInteractive();
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) ui.context.registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED);
        else ui.context.registerReceiver(screen, filter);
    }
    void artworkReady() { ready = true; schedule(); }
    void visible(boolean value) { visible = value; if (value) schedule(); else { home.removeCallbacks(reveal); reset(); } }
    private void schedule() {
        home.removeCallbacks(reveal);
        if (!closed && screenOn && pending && ready && visible) home.postOnAnimation(reveal);
    }
    private void show() {
        if (closed || !screenOn || !pending || !ready || !visible) return;
        pending = false;
        if (!ValueAnimator.areAnimatorsEnabled()) { reset(); return; }
        for (int i = 0; i < home.getChildCount(); i++) {
            View child = home.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE || child instanceof MusicCard || "atelier-greeting".equals(child.getTag()) || child == home.findFocus()) continue;
            int y = child.getTop(); long delay = y >= ui.px(380) ? 95 : y >= ui.px(200) ? 45 : 0;
            child.animate().cancel(); child.setAlpha(0); child.setTranslationY(ui.px(4));
            child.animate().alpha(1).translationY(0).setStartDelay(delay).setDuration(230)
                .setInterpolator(new PathInterpolator(.16f, 1f, .3f, 1f))
                .withEndAction(() -> child.animate().setStartDelay(0)).start();
        }
    }
    private void reset() {
        for (int i = 0; i < home.getChildCount(); i++) {
            View child = home.getChildAt(i); if (child instanceof MusicCard || "atelier-greeting".equals(child.getTag())) continue;
            child.animate().cancel(); child.animate().setStartDelay(0); child.setAlpha(1); child.setTranslationY(0);
        }
    }
    void close() { closed = true; home.removeCallbacks(reveal); reset(); ui.context.unregisterReceiver(screen); }
}
