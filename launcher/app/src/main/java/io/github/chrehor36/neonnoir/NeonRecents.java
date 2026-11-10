package io.github.chrehor36.neonnoir;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Process;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The dock's favorites, the Continue chip's most recent app, and All apps.
 *
 * Recency has two sources, newest wins: when Home last opened the app (LastOpened, no permission needed), and when the app was
 * last in front by any route (Android's usage stats). Usage stats need one permission granted once over ADB:
 *   adb shell appops set io.github.chrehor36.neonnoir GET_USAGE_STATS allow
 * Without it the dock still works, from what Home itself opened.
 */
final class NeonRecents {
    static final class App {
        final String pkg;
        final CharSequence label;
        final Drawable banner;   // null when the app ships no TV banner
        final Drawable icon;
        final long lastUsed;

        App(String pkg, CharSequence label, Drawable banner, Drawable icon, long lastUsed) {
            this.pkg = pkg;
            this.label = label;
            this.banner = banner;
            this.icon = icon;
            this.lastUsed = lastUsed;
        }
    }

    private NeonRecents() {
    }

    // ---- Favorites: the dock's apps, chosen in All apps (Hold OK) and ordered in the dock (Hold OK, then ‹ ›) ----

    static final int MAX_FAVORITES = 6;
    private static final String PINS = "neon_pins";
    /**
     * The owner's favorites (2026-11-09), the dock until it is changed on the TV. An app not installed yet (Plex)
     * keeps its place and shows up once it is.
     */
    private static final String[] STARTER = {
            "com.spotify.tv.android", "com.google.android.youtube.tv", "com.peacocktv.peacockandroid",
            "com.amazon.amazonvideo.livingroom", "com.plexapp.android"};
    /** Bumped when STARTER changes, so the new list replaces what is saved on the TV once. */
    private static final int STARTER_VERSION = 2;

    static List<String> favoriteIds(Context ctx) {
        android.content.SharedPreferences prefs = ctx.getSharedPreferences(PINS, Context.MODE_PRIVATE);
        if (prefs.getInt("starter", 0) < STARTER_VERSION) {
            prefs.edit().putString("order", String.join(",", STARTER)).putBoolean("chosen", true)
                    .putInt("starter", STARTER_VERSION).apply();
        }
        List<String> out = new ArrayList<>();
        for (String p : prefs.getString("order", "").split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    private static void saveFavorites(Context ctx, List<String> ids) {
        ctx.getSharedPreferences(PINS, Context.MODE_PRIVATE).edit()
                .putString("order", String.join(",", ids)).putBoolean("chosen", true).apply();
    }

    static boolean isFavorite(Context ctx, String pkg) {
        return favoriteIds(ctx).contains(pkg);
    }

    /** Adds the app at the end of the dock. False when the dock already holds MAX_FAVORITES. */
    static boolean addFavorite(Context ctx, String pkg) {
        List<String> ids = favoriteIds(ctx);
        if (ids.contains(pkg)) return true;
        if (ids.size() >= MAX_FAVORITES) return false;
        ids.add(pkg);
        saveFavorites(ctx, ids);
        return true;
    }

    static void removeFavorite(Context ctx, String pkg) {
        List<String> ids = favoriteIds(ctx);
        ids.remove(pkg);
        saveFavorites(ctx, ids);
    }

    /** Moves a favorite one place left (-1) or right (+1). False at either end. */
    static boolean moveFavorite(Context ctx, String pkg, int by) {
        List<String> ids = favoriteIds(ctx);
        int at = ids.indexOf(pkg), to = at + by;
        if (at < 0 || to < 0 || to >= ids.size()) return false;
        Collections.swap(ids, at, to);
        saveFavorites(ctx, ids);
        return true;
    }

    /** The dock: the favorites that are installed, in their order. */
    static List<App> favorites(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Map<String, ActivityInfo> byPkg = new HashMap<>();
        for (ResolveInfo r : pm.queryIntentActivities(query, 0)) {
            if (r.activityInfo != null) byPkg.putIfAbsent(r.activityInfo.packageName, r.activityInfo);
        }
        List<App> apps = new ArrayList<>();
        for (String pkg : favoriteIds(ctx)) {
            ActivityInfo ai = byPkg.get(pkg);
            if (ai == null) continue;   // uninstalled or disabled: kept in the list, back when the app is
            Drawable banner = ai.loadBanner(pm);
            if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
            apps.add(new App(pkg, ai.loadLabel(pm), banner, ai.loadIcon(pm), 0));
        }
        return apps;
    }

    /** Every TV app for All apps: A–Z, or most used (time in front over 30 days, then recency). */
    static List<App> all(Context ctx, boolean byUse) {
        PackageManager pm = ctx.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Map<String, ActivityInfo> byPkg = new HashMap<>();
        // config.xml hidden_apps: TCL's demo, manual and helper apps, still installed and working, just not tiles.
        java.util.Set<String> hidden = new java.util.HashSet<>(
                java.util.Arrays.asList(ctx.getResources().getStringArray(R.array.hidden_apps)));
        for (ResolveInfo r : pm.queryIntentActivities(query, 0)) {
            ActivityInfo ai = r.activityInfo;
            if (ai == null || ctx.getPackageName().equals(ai.packageName) || byPkg.containsKey(ai.packageName)
                    || hidden.contains(ai.packageName)) continue;
            byPkg.put(ai.packageName, ai);
        }
        // The sort key: time in front when usage access is granted, else when Home last opened the app.
        Map<String, Long> time = byUse ? usageTime(ctx) : new HashMap<>();
        boolean timed = !time.isEmpty();
        List<App> apps = new ArrayList<>();
        for (ActivityInfo ai : byPkg.values()) {
            Drawable banner = ai.loadBanner(pm);
            if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
            long key = !byUse ? 0 : timed ? time.getOrDefault(ai.packageName, 0L) : LastOpened.get(ctx, ai.packageName);
            apps.add(new App(ai.packageName, ai.loadLabel(pm), banner, ai.loadIcon(pm), key));
        }
        java.text.Collator collator = java.text.Collator.getInstance();
        Collections.sort(apps, (a, b) -> {
            if (byUse && a.lastUsed != b.lastUsed) return Long.compare(b.lastUsed, a.lastUsed);
            return collator.compare(a.label.toString(), b.label.toString());
        });
        return apps;
    }

    /** Milliseconds each app spent in front over the last 30 days (needs usage access). */
    private static Map<String, Long> usageTime(Context ctx) {
        Map<String, Long> out = new HashMap<>();
        if (!hasUsageAccess(ctx)) return out;
        try {
            UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return out;
            long now = System.currentTimeMillis();
            for (Map.Entry<String, UsageStats> e : usm.queryAndAggregateUsageStats(now - 30L * 24 * 3600 * 1000, now).entrySet()) {
                out.put(e.getKey(), e.getValue().getTotalTimeInForeground());
            }
        } catch (RuntimeException ignored) {
            // Falls back to A–Z within equal counts.
        }
        return out;
    }

    /** Apps the Continue chip never offers: stores, settings and other tools. */
    private static final String[] NOT_CONTINUE = {
            "com.android.vending", "com.android.tv.settings", "com.google.android.play.games", "com.tcl.browser",
            "com.tcl.ui_mediaCenter", "com.tcl.t_solo"};

    /** The most recently used apps, newest first (the Continue chip). */
    static List<App> recent(Context ctx, int count) {
        PackageManager pm = ctx.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(query, 0);
        Map<String, Long> usage = usage(ctx);
        // Continue offers something to go back to watching or listening: not the store, Settings or a hidden tool.
        java.util.Set<String> skip = new java.util.HashSet<>(java.util.Arrays.asList(NOT_CONTINUE));
        skip.addAll(java.util.Arrays.asList(ctx.getResources().getStringArray(R.array.hidden_apps)));
        // Rank first, from cheap data only; banners and labels load for the few apps that make the dock.
        Map<String, ActivityInfo> byPkg = new HashMap<>();
        Map<String, Long> lastUsed = new HashMap<>();
        for (ResolveInfo r : found) {
            ActivityInfo ai = r.activityInfo;
            if (ai == null || ctx.getPackageName().equals(ai.packageName) || byPkg.containsKey(ai.packageName)
                    || skip.contains(ai.packageName)) continue;
            byPkg.put(ai.packageName, ai);
            lastUsed.put(ai.packageName, Math.max(LastOpened.get(ctx, ai.packageName), usage.getOrDefault(ai.packageName, 0L)));
        }
        List<String> order = new ArrayList<>(byPkg.keySet());
        Collections.sort(order, (a, b) -> {
            long la = lastUsed.get(a), lb = lastUsed.get(b);
            if (la != lb) return Long.compare(lb, la);
            return a.compareTo(b);
        });
        List<App> apps = new ArrayList<>();
        for (String pkg : order) {
            if (apps.size() >= count) break;
            ActivityInfo ai = byPkg.get(pkg);
            Drawable banner = ai.loadBanner(pm);
            if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
            apps.add(new App(pkg, ai.loadLabel(pm), banner, ai.loadIcon(pm), lastUsed.get(pkg)));
        }
        return apps;
    }

    static boolean hasUsageAccess(Context ctx) {
        AppOpsManager ops = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) return false;
        @SuppressWarnings("deprecation")
        int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private static Map<String, Long> usage(Context ctx) {
        Map<String, Long> out = new HashMap<>();
        if (!hasUsageAccess(ctx)) return out;
        try {
            UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return out;
            long now = System.currentTimeMillis();
            Map<String, UsageStats> stats = usm.queryAndAggregateUsageStats(now - 30L * 24 * 3600 * 1000, now);
            for (Map.Entry<String, UsageStats> e : stats.entrySet()) {
                out.put(e.getKey(), e.getValue().getLastTimeUsed());
            }
        } catch (RuntimeException ignored) {
            // Some firmware throws instead of returning nothing: the dock falls back to LastOpened.
        }
        return out;
    }

    /** Starts the app's TV entry, or its phone entry when it has none. Returns false when neither exists. */
    static boolean launch(Context ctx, String pkg) {
        PackageManager pm = ctx.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
        } catch (RuntimeException e) {
            return false;
        }
        LastOpened.record(ctx, pkg);
        return true;
    }
}
