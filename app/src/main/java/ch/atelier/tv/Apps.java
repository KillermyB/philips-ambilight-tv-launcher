package ch.atelier.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class Apps {
    static final class App {
        final String name, pkg;
        final ComponentName component;
        final Drawable artwork;
        final boolean banner;

        App(Context context, ResolveInfo info) {
            PackageManager pm = context.getPackageManager();
            name = info.loadLabel(pm).toString();
            pkg = info.activityInfo.packageName;
            component = new ComponentName(pkg, info.activityInfo.name);
            Drawable image = info.activityInfo.loadBanner(pm);
            banner = image != null;
            artwork = image != null ? image : info.loadIcon(pm);
        }

        Drawable image() {
            Drawable.ConstantState state = artwork.getConstantState();
            return state == null ? artwork : state.newDrawable().mutate();
        }
    }

    static List<App> load(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<App> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(context.getPackageName()) || !seen.add(pkg)) continue;
            if (!info.activityInfo.enabled || !info.activityInfo.exported) continue;
            try { apps.add(new App(context, info)); } catch (RuntimeException ignored) { }
        }
        Collections.sort(apps, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return apps;
    }

    /** A stable visual sequence for the library; the Home favourites stay user-controlled. */
    static List<App> libraryOrder(List<App> installed) {
        List<App> ordered = new ArrayList<>(installed);
        Collections.sort(ordered, (a, b) -> {
            int rank = Integer.compare(libraryRank(a), libraryRank(b));
            return rank != 0 ? rank : a.name.compareToIgnoreCase(b.name);
        });
        return ordered;
    }
    private static final String[][] LIBRARY_ORDER = {
        {"com.google.android.youtube.tv", "YouTube"}, {"com.stremio.one", "Stremio"},
        {"com.spotify.tv.android", "Spotify"}, {"com.amazon.amazonvideo.livingroom", "Prime Video"},
        {"com.netflix.ninja", "Netflix"}, {"com.apple.atve.androidtv.appletv", "Apple TV"},
        {"com.canal.android.canal", "CANAL+"}, {"com.disney.disneyplus", "Disney+"},
        {"tv.wuaki.apptv", "Rakuten TV"}, {"tv.twitch.android.app", "Twitch"},
        {"com.google.android.youtube.tvmusic", "YouTube Music"}, {"com.google.android.videos", "Play Films"},
        {"com.android.vending", "Play Store"}, {"com.google.android.play.games", "Play Games"},
        {"com.mediatek.wwtv.tvcenter", "Live TV"}, {"com.mediatek.wwtv.mediaplayer", "MultiMediaPlayer"},
        {"", "Diffusion rapide"}, {"com.creative.fastscreen.tv", "FastCast"}, {"", "MagiCast"},
        {"com.tm.replica", "Replica"}, {"", "Aide"}, {"com.tpv.xmic.ots.cms.client", "TPV Cloud CMS"},
        {"com.android.tv.settings", "Paramètres"}
    };
    private static int libraryRank(App app) {
        String name = app.name.replace('\u00a0', ' ');
        for (int i = 0; i < LIBRARY_ORDER.length; i++)
            if (app.pkg.equals(LIBRARY_ORDER[i][0]) || name.equalsIgnoreCase(LIBRARY_ORDER[i][1])) return i;
        return LIBRARY_ORDER.length;
    }

    static int accent(String pkg) {
        if (pkg.contains("netflix")) return 0xFF842C43;
        if (pkg.contains("youtube")) return 0xFF7C3B52;
        if (pkg.contains("spotify")) return 0xFF216655;
        if (pkg.contains("stremio")) return 0xFF644496;
        if (pkg.contains("disney")) return 0xFF284D95;
        if (pkg.contains("amazon")) return 0xFF266182;
        if (pkg.contains("apple")) return 0xFF546783;
        return 0xFF4F527F;
    }

    static String description(String pkg) {
        if (pkg.contains("spotify") || pkg.contains("tvmusic")) return "La bande-son\nde votre soirée.";
        if (pkg.contains("youtube")) return "Suivez\nvotre curiosité.";
        if (pkg.contains("canal")) return "Le grand écran,\nchez vous.";
        return "La soirée\nvous appartient.";
    }
}
