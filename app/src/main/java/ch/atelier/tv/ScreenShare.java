package ch.atelier.tv;

import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Discovers installed third-party receivers; it never enables or starts a receiver itself. */
final class ScreenShare {
    static final String KEY = "screen_share_receiver";
    private static final String[] PACKAGES = {
        "com.nbcast.wirelessscreen", // MagiCast
        "com.creative.fastscreen.tv", // FastCast TV
        "com.ionitech.airscreen" // AirScreen
    };

    static final class Receiver {
        final String name, pkg;
        private Receiver(String name, String pkg) { this.name = name; this.pkg = pkg; }
    }

    static List<Receiver> installed(Context context) {
        PackageManager pm = context.getPackageManager();
        List<Receiver> receivers = new ArrayList<>();
        for (String pkg : PACKAGES) {
            // Requiring a usable, exported entry also excludes disabled activities.
            if (launchIntent(context, pkg) == null) continue;
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                CharSequence label = info.loadLabel(pm);
                String name = label == null ? "" : label.toString().trim();
                receivers.add(new Receiver(name.isEmpty() ? pkg : name, pkg));
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) { }
        }
        return Collections.unmodifiableList(receivers);
    }

    /** Resolve again at the moment of the user's click, so stale discovery cannot launch a removed app. */
    static Intent launchIntent(Context context, Receiver receiver) {
        return receiver == null ? null : launchIntent(context, receiver.pkg);
    }

    private static Intent launchIntent(Context context, String pkg) {
        if (!supported(pkg)) return null;
        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo app = pm.getApplicationInfo(pkg, 0);
            if (!app.enabled || (app.flags & ApplicationInfo.FLAG_INSTALLED) == 0 ||
                (app.flags & ApplicationInfo.FLAG_SUSPENDED) != 0) return null;
            int enabled = pm.getApplicationEnabledSetting(pkg);
            if (enabled == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                enabled == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                enabled == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) return null;
            Intent leanback = usable(context, pm.getLeanbackLaunchIntentForPackage(pkg), pkg);
            return leanback != null ? leanback : usable(context, pm.getLaunchIntentForPackage(pkg), pkg);
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) { return null; }
    }

    private static Intent usable(Context context, Intent candidate, String pkg) {
        if (candidate == null) return null;
        PackageManager pm = context.getPackageManager();
        ActivityInfo activity = candidate.resolveActivityInfo(pm, 0);
        if (activity == null || !pkg.equals(activity.packageName) || !activity.enabled || !activity.exported ||
            activity.applicationInfo == null || !activity.applicationInfo.enabled) return null;
        ComponentName component = new ComponentName(activity.packageName, activity.name);
        int state = pm.getComponentEnabledSetting(component);
        if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) return null;
        if (activity.permission != null && !activity.permission.isEmpty() &&
            pm.checkPermission(activity.permission, context.getPackageName()) != PackageManager.PERMISSION_GRANTED) return null;
        // The result belongs to this call and can safely receive flags in the caller.
        return new Intent(candidate).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private static boolean supported(String pkg) {
        for (String allowed : PACKAGES) if (allowed.equals(pkg)) return true;
        return false;
    }
}
