package io.github.chrehor36.neonnoir;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;

/**
 * Art Mode as the system screensaver (the same screen as ArtActivity). Any key wakes the TV back to where it was.
 *
 * <p>TCL's firmware force-stops apps on its "background blacklist" (Peacock, for one) the moment they leave the
 * screen, and a screensaver covering them counts. Waking up then lands on Home instead of the app. So the dream
 * notes which app it covered, and if Home is in front a moment after waking, opens that app again. Pressing Home
 * to wake cancels this (see {@link #cancelReturn()}, called from HomeKeyService).
 */
public class ArtDream extends DreamService {
    private static final String DREAM_ACTIVITY = "android.service.dreams.DreamActivity";
    /** How long after waking to look at what is in front: the stopped app's task is gone by then. */
    private static final long RETURN_CHECK_MS = 1200;

    private static volatile String returnTo;
    private ArtView art;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        art = new ArtView(this);
        setContentView(art);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        returnTo = coveredApp(this);
        art.start();
    }

    @Override
    public void onDreamingStopped() {
        art.stop();
        super.onDreamingStopped();
        final String pkg = returnTo;
        if (pkg == null) return;
        final Context app = getApplicationContext();
        final long woke = System.currentTimeMillis();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!pkg.equals(returnTo)) return;
            returnTo = null;
            // Only when the app is gone and Home took its place; an app that survived is already back in front.
            if (app.getPackageName().equals(frontSince(app, woke - 500))) NeonRecents.launch(app, pkg);
        }, RETURN_CHECK_MS);
    }

    /** The Home key woke the TV: stay on Home. */
    static void cancelReturn() {
        returnTo = null;
    }

    /** The app the screensaver covered, or null when it covered Home (or usage access is off). */
    private static String coveredApp(Context ctx) {
        String front = lastResumed(ctx, System.currentTimeMillis() - 6 * 3600 * 1000L);
        return front == null || ctx.getPackageName().equals(front) ? null : front;
    }

    /** The package that came to the front after {@code since}, or null when none did. */
    private static String frontSince(Context ctx, long since) {
        return lastResumed(ctx, since);
    }

    /** The package of the last activity resumed after {@code since}, not counting the dream's own window. */
    private static String lastResumed(Context ctx, long since) {
        if (!NeonRecents.hasUsageAccess(ctx)) return null;
        try {
            UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;
            UsageEvents events = usm.queryEvents(since, System.currentTimeMillis());
            UsageEvents.Event e = new UsageEvents.Event();
            String last = null;
            while (events.getNextEvent(e)) {
                if (e.getEventType() != UsageEvents.Event.ACTIVITY_RESUMED) continue;
                if (DREAM_ACTIVITY.equals(e.getClassName())) continue;
                last = e.getPackageName();
            }
            return last;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
