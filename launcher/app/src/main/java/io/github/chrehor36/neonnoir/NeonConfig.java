package io.github.chrehor36.neonnoir;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Neon Noir settings from secrets.properties (copied into BuildConfig at build time). Every value is optional;
 * the getters say what is missing so each feature can show a short note instead of failing.
 */
final class NeonConfig {
    private NeonConfig() {
    }

    /** The TV's place as {lat, lon}: secrets.properties first, then the launcher's own config.xml; null if neither. */
    static double[] location(Context ctx) {
        double[] p = parse(BuildConfig.HOME_LATITUDE, BuildConfig.HOME_LONGITUDE);
        if (p != null) return p;
        try {
            return parse(ctx.getString(R.string.latitude), ctx.getString(R.string.longitude));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static double[] parse(String lat, String lon) {
        try {
            if (lat == null || lon == null || lat.trim().isEmpty() || lon.trim().isEmpty()) return null;
            double a = Double.parseDouble(lat.trim()), b = Double.parseDouble(lon.trim());
            if (Math.abs(a) > 90 || Math.abs(b) > 180) return null;
            return new double[]{a, b};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean celsius() {
        return "C".equalsIgnoreCase(BuildConfig.TEMPERATURE_UNIT.trim());
    }

    static String discogsUser() {
        return BuildConfig.DISCOGS_USERNAME.trim();
    }

    static String discogsToken() {
        return BuildConfig.DISCOGS_TOKEN.trim();
    }

    static String tmdbKey() {
        return BuildConfig.TMDB_API_KEY.trim();
    }

    /** PICKS_SERVICES: lowercase service names (netflix, peacock, youtube, plex, tubi, ...), in order. */
    static java.util.List<String> picksServices() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String s : BuildConfig.PICKS_SERVICES.split(",")) {
            String t = s.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]", "");
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    static String picksRegion() {
        String r = BuildConfig.PICKS_REGION.trim().toUpperCase(java.util.Locale.ROOT);
        return r.matches("[A-Z]{2}") ? r : "US";
    }

    /** "basketball/nba/bos" entries, checked for shape. */
    static List<String> teams() {
        List<String> out = new ArrayList<>();
        for (String t : BuildConfig.SCORES_TEAMS.split(",")) {
            String s = t.trim().toLowerCase(java.util.Locale.ROOT);
            if (s.matches("[a-z-]+/[a-z0-9.-]+/[a-z0-9-]+")) out.add(s);
        }
        return Collections.unmodifiableList(out);
    }

    /** "football/nfl/nfc" entries: a league's conference or group, for the "around the league" row. */
    static List<String> groups() {
        return entries(BuildConfig.SCORES_GROUPS, "[a-z-]+/[a-z0-9.-]+/[a-z0-9-]+");
    }

    /** "tennis/atp/Djokovic" entries: a player followed by (part of) their name. */
    static List<String> players() {
        return entries(BuildConfig.SCORES_PLAYERS, "[a-z-]+/[a-z0-9.-]+/[^,/]+");
    }

    /** SCORES_RACING: racing series on ESPN, e.g. "f1" (also accepted as "racing/f1"). */
    static List<String> racing() {
        List<String> out = new ArrayList<>();
        for (String t : BuildConfig.SCORES_RACING.split(",")) {
            String s = t.trim().toLowerCase(java.util.Locale.ROOT).replaceFirst("^racing/", "");
            if (s.matches("[a-z0-9-]+")) out.add(s);
        }
        return Collections.unmodifiableList(out);
    }

    private static List<String> entries(String raw, String shape) {
        List<String> out = new ArrayList<>();
        for (String t : raw.split(",")) {
            String s = t.trim();
            int cut = s.lastIndexOf('/');
            if (cut > 0) s = s.substring(0, cut).toLowerCase(java.util.Locale.ROOT) + s.substring(cut);
            if (s.matches(shape)) out.add(s);
        }
        return Collections.unmodifiableList(out);
    }

    /** Spotify playlist IDs from secrets.properties (bare IDs, spotify: URIs or open.spotify.com links). */
    /**
     * SPOTIFY_PLAYLISTS: Spotify playlist or album links (or spotify: URIs, or bare playlist IDs), comma-separated.
     * Each entry is {"playlist" or "album", id}; the first is the main one.
     */
    static List<String[]> spotifyItems() {
        List<String[]> out = new ArrayList<>();
        for (String t : BuildConfig.SPOTIFY_PLAYLISTS.split(",")) {
            String s = t.trim();
            int q = s.indexOf('?');
            if (q >= 0) s = s.substring(0, q);
            String type = s.contains("album") ? "album" : "playlist";
            int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf(':'));
            if (slash >= 0) s = s.substring(slash + 1);
            if (s.matches("[A-Za-z0-9]{22}")) out.add(new String[]{type, s});
        }
        return out;
    }
}
