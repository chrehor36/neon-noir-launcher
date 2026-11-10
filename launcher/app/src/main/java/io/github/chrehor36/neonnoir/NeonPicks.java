package io.github.chrehor36.neonnoir;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Picks for you: 7 popular titles on the streaming services installed on this TV, from TMDB (free API key in
 * secrets.properties). Each opens the service's own search for the title where it can, else the app (see open()).
 */
final class NeonPicks {
    static final class Pick {
        /** pkg: the installed app, or null; storePkg: the app to offer in Google Play when it isn't installed. */
        final String title, service, posterUrl, pkg, storePkg;

        Pick(String title, String service, String posterUrl, String pkg, String storePkg) {
            this.title = title;
            this.service = service;
            this.posterUrl = posterUrl;
            this.pkg = pkg;
            this.storePkg = storePkg;
        }
    }

    /**
     * The PICKS_SERVICES name, TMDB watch-provider ID (US), the label, and the TV app packages that mean "you have it".
     * The first four are the fallback on a TV with none installed and no PICKS_SERVICES (an emulator).
     */
    private static final Object[][] SERVICES = {
            {"netflix", 8, "Netflix", new String[]{"com.netflix.ninja"}},
            {"prime", 9, "Prime Video", new String[]{"com.amazon.amazonvideo.livingroom"}},
            {"disney", 337, "Disney+", new String[]{"com.disney.disneyplus"}},
            {"max", 1899, "Max", new String[]{"com.wbd.stream", "com.hbo.hbonow"}},
            {"hulu", 15, "Hulu", new String[]{"com.hulu.livingroomplus"}},
            {"appletv", 350, "Apple TV+", new String[]{"com.apple.atve.androidtv.appletv"}},
            {"peacock", 386, "Peacock", new String[]{"com.peacocktv.peacockandroid"}},
            {"paramount", 531, "Paramount+", new String[]{"com.cbs.ott"}},
            // Free with ads (or a library card).
            {"youtube", 235, "YouTube", new String[]{"com.google.android.youtube.tv", "com.google.android.youtube.googletv"}},
            {"plex", 538, "Plex", new String[]{"com.plexapp.android"}},
            {"tubi", 73, "Tubi", new String[]{"com.tubitv"}},
            {"pluto", 300, "Pluto TV", new String[]{"tv.pluto.android"}},
            {"roku", 207, "Roku Channel", new String[]{"com.roku.web.trc", "com.roku.trc"}},
            {"kanopy", 191, "Kanopy", new String[]{"com.kanopy"}},
            {"hoopla", 212, "Hoopla", new String[]{"com.hoopladigital.android"}},
    };

    private NeonPicks() {
    }

    static boolean configured() {
        return !NeonConfig.tmdbKey().isEmpty();
    }

    /** Background thread. Empty without a key or a connection. */
    static List<Pick> load(Context ctx, int count) {
        List<Pick> out = new ArrayList<>();
        String key = NeonConfig.tmdbKey();
        if (key.isEmpty()) return out;
        boolean bearer = key.startsWith("eyJ");
        Map<String, String> headers = new HashMap<>();
        if (bearer) headers.put("Authorization", "Bearer " + key);

        // PICKS_SERVICES in its order; otherwise the services installed; otherwise (an emulator) the four biggest.
        List<Object[]> mine = new ArrayList<>();
        PackageManager pm = ctx.getPackageManager();
        for (String name : NeonConfig.picksServices()) {
            for (Object[] s : SERVICES) if (s[0].equals(name) && !mine.contains(s)) mine.add(s);
        }
        if (mine.isEmpty()) for (Object[] s : SERVICES) if (installedPackage(pm, (String[]) s[3]) != null) mine.add(s);
        if (mine.isEmpty()) for (int i = 0; i < 4; i++) mine.add(SERVICES[i]);

        List<List<Pick>> perService = new ArrayList<>();
        for (Object[] s : mine) {
            List<Pick> list = new ArrayList<>();
            String pkg = installedPackage(pm, (String[]) s[3]);
            for (String kind : new String[]{"tv", "movie"}) {
                // Subscription, free and free-with-ads; a floor on votes keeps out obscure titles that spike in
                // popularity.
                String url = "https://api.themoviedb.org/3/discover/" + kind + "?watch_region=" + NeonConfig.picksRegion()
                        + "&with_watch_providers=" + s[1] + "&with_watch_monetization_types=flatrate%7Cfree%7Cads"
                        + "&vote_count.gte=" + (kind.equals("tv") ? 100 : 300)
                        + "&sort_by=popularity.desc&language=en-US&page=1" + (bearer ? "" : "&api_key=" + NeonNet.enc(key));
                String body = NeonNet.text(ctx, url, headers, 6 * 3600_000L);
                if (body == null) continue;
                try {
                    JSONArray results = new JSONObject(body).getJSONArray("results");
                    for (int i = 0; i < results.length() && i < 6; i++) {
                        JSONObject r = results.getJSONObject(i);
                        String poster = r.optString("poster_path", "");
                        if (poster.isEmpty() || "null".equals(poster)) continue;
                        list.add(new Pick(r.optString(kind.equals("tv") ? "name" : "title"), (String) s[2],
                                "https://image.tmdb.org/t/p/w342" + poster, pkg, ((String[]) s[3])[0]));
                    }
                } catch (Exception ignored) {
                    // One bad answer: the other lists still count.
                }
            }
            // Shows and films alternate within a service.
            List<Pick> mixed = new ArrayList<>();
            int half = list.size() / 2;
            for (int i = 0; i < list.size(); i++) mixed.add(list.get(i % 2 == 0 ? i / 2 : half + i / 2));
            perService.add(mixed.isEmpty() ? list : mixed);
        }
        // Round-robin across services, no repeats.
        Set<String> seen = new HashSet<>();
        for (int round = 0; out.size() < count && round < 12; round++) {
            for (List<Pick> l : perService) {
                if (round < l.size() && out.size() < count && seen.add(l.get(round).title.toLowerCase(java.util.Locale.ROOT))) {
                    out.add(l.get(round));
                }
            }
        }
        return out;
    }

    private static String installedPackage(PackageManager pm, String[] pkgs) {
        for (String p : pkgs) {
            try {
                pm.getPackageInfo(p, 0);
                return p;
            } catch (PackageManager.NameNotFoundException ignored) {
                // Next.
            }
        }
        return null;
    }

    /**
     * Search links the TV apps open on their own search results (checked on the TCL, 2026-11-09: YouTube and Prime
     * Video land on the results; Pluto TV on its search page; Plex opens). %s is the encoded title.
     */
    private static final String[][] SEARCH_LINKS = {
            {"com.google.android.youtube.tv", "https://www.youtube.com/results?search_query=%s"},
            {"com.google.android.youtube.googletv", "https://www.youtube.com/results?search_query=%s"},
            {"com.amazon.amazonvideo.livingroom", "https://watch.amazon.com/search?phrase=%s"},
            {"com.plexapp.android", "https://watch.plex.tv/search?q=%s"},
            {"tv.pluto.android", "https://pluto.tv/search?query=%s"},
    };

    /**
     * The app's own search for the title, or its search link; else the app itself. A service that isn't installed
     * opens its Google Play page. Google TV's global search comes last: it belongs to Google's home screen, which
     * this launcher replaces (on the TCL it is disabled and the search is cancelled).
     */
    static boolean open(Context ctx, Pick p) {
        PackageManager pm = ctx.getPackageManager();
        if (p.pkg != null) {
            Intent search = new Intent(Intent.ACTION_SEARCH).setPackage(p.pkg)
                    .putExtra(SearchManager.QUERY, p.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (pm.resolveActivity(search, 0) != null && start(ctx, search)) {
                LastOpened.record(ctx, p.pkg);
                return true;
            }
            for (String[] link : SEARCH_LINKS) {
                if (!link[0].equals(p.pkg)) continue;
                Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(link[1], Uri.encode(p.title))))
                        .setPackage(p.pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (pm.resolveActivity(view, 0) != null && start(ctx, view)) {
                    LastOpened.record(ctx, p.pkg);
                    return true;
                }
            }
            if (NeonRecents.launch(ctx, p.pkg)) return true;
        } else if (p.storePkg != null) {
            Intent store = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + p.storePkg))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (pm.resolveActivity(store, 0) != null && start(ctx, store)) return true;
        }
        Intent global = new Intent("android.search.action.GLOBAL_SEARCH")
                .putExtra(SearchManager.QUERY, p.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return pm.resolveActivity(global, 0) != null && start(ctx, global);
    }

    private static boolean start(Context ctx, Intent i) {
        try {
            ctx.startActivity(i);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
