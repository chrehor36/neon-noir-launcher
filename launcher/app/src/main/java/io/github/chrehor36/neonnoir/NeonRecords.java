package io.github.chrehor36.neonnoir;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * The record picker for Jazz mode and the Tonight chip: jazz, crooners and vocal pop from your Discogs collection.
 * Until a Discogs username is set (secrets.properties), it picks from a small built-in crate of classics.
 */
final class NeonRecords {
    static final class Record {
        final String title, artist, year;
        final boolean fromDiscogs;

        Record(String title, String artist, String year, boolean fromDiscogs) {
            this.title = title;
            this.artist = artist;
            this.year = year;
            this.fromDiscogs = fromDiscogs;
        }

        /** "Artist · 1959" */
        String byline() {
            return year.isEmpty() || year.equals("0") ? artist : artist + " · " + year;
        }
    }

    /** Built-in crate: classic jazz and crooner LPs, used until Discogs is set up or reachable. */
    private static final String[][] CRATE = {
            {"Kind of Blue", "Miles Davis", "1959"},
            {"A Love Supreme", "John Coltrane", "1965"},
            {"Time Out", "The Dave Brubeck Quartet", "1959"},
            {"Moanin'", "Art Blakey & The Jazz Messengers", "1958"},
            {"Ella and Louis", "Ella Fitzgerald & Louis Armstrong", "1956"},
            {"Songs for Swingin' Lovers!", "Frank Sinatra", "1956"},
            {"Getz/Gilberto", "Stan Getz & João Gilberto", "1964"},
            {"Blue Train", "John Coltrane", "1958"},
            {"Mingus Ah Um", "Charles Mingus", "1959"},
            {"Somethin' Else", "Cannonball Adderley", "1958"},
            {"Waltz for Debby", "Bill Evans Trio", "1962"},
            {"Saxophone Colossus", "Sonny Rollins", "1956"},
            {"Lady in Satin", "Billie Holiday", "1958"},
            {"Chet Baker Sings", "Chet Baker", "1954"},
            {"In the Wee Small Hours", "Frank Sinatra", "1955"},
            {"Midnight Blue", "Kenny Burrell", "1963"},
            {"Ellington at Newport", "Duke Ellington", "1956"},
            {"Nat King Cole Sings for Two in Love", "Nat King Cole", "1953"},
    };

    /** Discogs styles that count as jazz-adjacent vocal music (in a Pop or Jazz genre). */
    private static final String[] VOCAL_STYLES = {"Vocal", "Easy Listening", "Swing", "Big Band", "Ballad", "Crooner"};

    private static List<Record> cache;
    private static long cacheAt;

    /** The most recent Sunday 12:00 AM, local time (today's, when today is Sunday). */
    static long lastSundayMidnight() {
        java.time.ZonedDateTime midnight = LocalDate.now()
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY))
                .atStartOfDay(java.time.ZoneId.systemDefault());
        return midnight.toInstant().toEpochMilli();
    }

    private NeonRecords() {
    }

    static boolean usingDiscogs() {
        return !NeonConfig.discogsUser().isEmpty();
    }

    /** All the jazz records: Discogs when set up and reachable, else the built-in crate. Background thread. */
    static synchronized List<Record> all(Context ctx) {
        // The collection is read again once a week, after midnight on Sunday (or the first time it's needed after
        // that, if the TV was off); records added in between show up then.
        if (cache != null && cacheAt >= lastSundayMidnight()) return cache;
        cacheAt = System.currentTimeMillis();
        List<Record> out = usingDiscogs() ? discogs(ctx) : Collections.emptyList();
        if (out.isEmpty()) {
            out = new ArrayList<>();
            for (String[] r : CRATE) out.add(new Record(r[0], r[1], r[2], false));
        }
        cache = out;
        return out;
    }

    private static final String CHOSEN = "neon_tonight";

    /**
     * Tonight's record: the one picked in Jazz mode today (Pick another), else the day's own pick, the same one all
     * day and a new one each day. Home's Tonight chip and Jazz mode both read it, so they always agree.
     */
    static Record tonight(Context ctx) {
        android.content.SharedPreferences prefs = ctx.getSharedPreferences(CHOSEN, Context.MODE_PRIVATE);
        if (LocalDate.now().toString().equals(prefs.getString("day", ""))) {
            return new Record(prefs.getString("title", ""), prefs.getString("artist", ""), prefs.getString("year", ""),
                    prefs.getBoolean("discogs", false));
        }
        List<Record> all = all(ctx);
        if (all.isEmpty()) return null;
        Random r = new Random(LocalDate.now().toEpochDay() * 31 + all.size());
        return all.get(r.nextInt(all.size()));
    }

    /**
     * False when a record picked today is a different one (main thread: prefs only). A day's own pick is not
     * checked here; Jazz mode reloads on a new day itself.
     */
    static boolean isTonight(Context ctx, Record r) {
        android.content.SharedPreferences prefs = ctx.getSharedPreferences(CHOSEN, Context.MODE_PRIVATE);
        if (!LocalDate.now().toString().equals(prefs.getString("day", ""))) return true;
        return r.title.equals(prefs.getString("title", "")) && r.artist.equals(prefs.getString("artist", ""));
    }

    /** Pick another in Jazz mode: this is tonight's record for the rest of the day. */
    static void choose(Context ctx, Record r) {
        ctx.getSharedPreferences(CHOSEN, Context.MODE_PRIVATE).edit()
                .putString("day", LocalDate.now().toString()).putString("title", r.title).putString("artist", r.artist)
                .putString("year", r.year).putBoolean("discogs", r.fromDiscogs).apply();
    }

    /** Any record other than the current one. */
    static Record another(Context ctx, Record current) {
        List<Record> all = all(ctx);
        if (all.isEmpty()) return null;
        if (all.size() == 1) return all.get(0);
        Random r = new Random();
        Record pick;
        int tries = 0;
        do {
            pick = all.get(r.nextInt(all.size()));
        } while (++tries < 20 && current != null && pick.title.equals(current.title) && pick.artist.equals(current.artist));
        return pick;
    }

    private static List<Record> discogs(Context ctx) {
        List<Record> out = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        String token = NeonConfig.discogsToken();
        if (!token.isEmpty()) headers.put("Authorization", "Discogs token=" + token);
        String base = "https://api.discogs.com/users/" + NeonNet.enc(NeonConfig.discogsUser())
                + "/collection/folders/0/releases?per_page=100&sort=added&sort_order=desc&page=";
        // Up to 1,000 records. A downloaded copy counts until the next Sunday midnight (weekly refresh).
        long maxAge = Math.max(1, System.currentTimeMillis() - lastSundayMidnight());
        for (int page = 1; page <= 10; page++) {
            String body = NeonNet.text(ctx, base + page, headers, maxAge);
            if (body == null) break;
            try {
                JSONObject j = new JSONObject(body);
                JSONArray releases = j.getJSONArray("releases");
                for (int i = 0; i < releases.length(); i++) {
                    JSONObject info = releases.getJSONObject(i).getJSONObject("basic_information");
                    if (!jazzy(info)) continue;
                    JSONArray artists = info.optJSONArray("artists");
                    String artist = artists != null && artists.length() > 0
                            ? artists.getJSONObject(0).optString("name", "") : "";
                    // Discogs numbers duplicate names: "Miles Davis (2)".
                    artist = artist.replaceAll("\\s*\\(\\d+\\)$", "");
                    int year = info.optInt("year", 0);
                    out.add(new Record(info.optString("title", ""), artist, year > 0 ? String.valueOf(year) : "", true));
                }
                JSONObject pagination = j.optJSONObject("pagination");
                if (pagination == null || page >= pagination.optInt("pages", 1)) break;
            } catch (Exception e) {
                break;
            }
        }
        return out;
    }

    /** Jazz, crooners and vocal pop: the Jazz genre, or a vocal style within Pop or Jazz. */
    private static boolean jazzy(JSONObject info) {
        JSONArray genres = info.optJSONArray("genres"), styles = info.optJSONArray("styles");
        boolean pop = false;
        if (genres != null) {
            for (int i = 0; i < genres.length(); i++) {
                String g = genres.optString(i).toLowerCase(Locale.ROOT);
                if (g.equals("jazz")) return true;
                if (g.equals("pop")) pop = true;
            }
        }
        if (pop && styles != null) {
            for (int i = 0; i < styles.length(); i++) {
                for (String v : VOCAL_STYLES) if (v.equalsIgnoreCase(styles.optString(i))) return true;
            }
        }
        return false;
    }
}
