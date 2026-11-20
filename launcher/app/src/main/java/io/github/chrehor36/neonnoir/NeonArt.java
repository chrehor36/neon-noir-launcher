package io.github.chrehor36.neonnoir;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The paintings for Art Mode: public-domain paintings from the Art Institute of Chicago's open API (no account,
 * CC0 data), narrowed to the collection picked in Launcher settings (see ArtCollections). Each collection's pool
 * is kept on disk and refreshed weekly; at night only the darker ones are shown (the API gives each work's
 * dominant color lightness).
 */
final class NeonArt {
    static final class Work {
        final String id, title, artist, year, imageId;
        final int lightness;

        Work(String id, String title, String artist, String year, String imageId, int lightness) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.year = year;
            this.imageId = imageId;
            this.lightness = lightness;
        }

        String imageUrl() {
            return "https://www.artic.edu/iiif/2/" + imageId + "/full/1686,/0/default.jpg";
        }
    }

    /**
     * Each collection (Launcher settings > Art Mode, see ArtCollections) keeps its own pool file, so switching
     * shows the new paintings at once and switching back needs no fetch. v4: pools before collections.
     */
    private static String poolName(ArtCollections.Collection c) {
        return "art_pool_v4_" + c.id + ".json";
    }
    /** The fewest works night mode draws from: when fewer are dark enough, the darkest this many are used. */
    private static final int NIGHT_MIN = 20;
    private static final long WEEK = 7L * 24 * 3600_000L;
    /** Dominant-color lightness (0-100) at or under which a work counts as dark enough for night. */
    static final int NIGHT_MAX_LIGHTNESS = 38;
    private static final int RECENT = 40;
    private static final Map<String, String> HEADERS = new HashMap<>();

    static {
        HEADERS.put("AIC-User-Agent", "NeonNoirLauncher (Android TV home screen)");
    }

    private NeonArt() {
    }

    /** The next work to show, not one of the last 40. Background thread (may fetch the pool). Null when offline. */
    static Work next(Context ctx, boolean night) {
        List<Work> pool = pool(ctx, ArtCollections.current(ctx));
        if (pool.isEmpty()) return null;
        List<Work> fit = new ArrayList<>();
        for (Work w : pool) if (!night || w.lightness <= NIGHT_MAX_LIGHTNESS) fit.add(w);
        if (night && fit.size() < NIGHT_MIN) {
            // A small pool (one style) has few truly dark works: take the darkest NIGHT_MIN instead.
            fit = new ArrayList<>(pool);
            Collections.sort(fit, (a, b) -> Integer.compare(a.lightness, b.lightness));
            fit = new ArrayList<>(fit.subList(0, Math.min(NIGHT_MIN, fit.size())));
        }
        if (fit.isEmpty()) fit = pool;
        SharedPreferences p = ctx.getSharedPreferences("neon_art", Context.MODE_PRIVATE);
        List<String> recent = new ArrayList<>(java.util.Arrays.asList(p.getString("recent", "").split(",")));
        // Skip the last 40 shown, or fewer when the set is small (the night set can be ~30): the paintings shown
        // longest ago stay eligible, so a small set cycles through in turn instead of repeating at random.
        List<String> avoid = recent.subList(0, Math.min(recent.size(), Math.min(RECENT, fit.size() * 2 / 3)));
        List<Work> fresh = new ArrayList<>();
        for (Work w : fit) if (!avoid.contains(w.id)) fresh.add(w);
        if (fresh.isEmpty()) fresh = fit;
        Work pick = fresh.get(new Random().nextInt(fresh.size()));
        recent.add(0, pick.id);
        while (recent.size() > RECENT) recent.remove(recent.size() - 1);
        p.edit().putString("recent", String.join(",", recent)).apply();
        return pick;
    }

    private static synchronized List<Work> pool(Context ctx, ArtCollections.Collection c) {
        File f = new File(ctx.getFilesDir(), poolName(c));
        List<Work> cached = f.exists() ? parse(read(f)) : Collections.emptyList();
        if (!cached.isEmpty() && System.currentTimeMillis() - f.lastModified() < WEEK) return cached;
        List<Work> fetched = fetch(ctx, c);
        if (fetched.size() >= 10) {
            JSONArray out = new JSONArray();
            for (Work w : fetched) {
                try {
                    out.put(new JSONObject().put("id", w.id).put("title", w.title).put("artist_title", w.artist)
                            .put("date_display", w.year).put("image_id", w.imageId).put("l", w.lightness));
                } catch (Exception ignored) {
                    // Skip one odd record.
                }
            }
            try {
                Files.write(f.toPath(), out.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
                // Still usable for this run.
            }
            return fetched;
        }
        return cached;
    }

    /** Search pages fetched per refresh (about 43 landscape works and 7 night-dark ones each). */
    private static final int PAGES = 5;
    /** The API serves at most the first 1,000 results of a search (10 pages of 100); later pages are a 403. */
    private static final int MAX_PAGE = 10;

    /** The search's bool clauses: public-domain paintings with an image, narrowed by the collection. */
    private static String filter(ArtCollections.Collection c) {
        String must = "\"must\":[{\"term\":{\"is_public_domain\":true}},"
                + "{\"term\":{\"artwork_type_title.keyword\":\"Painting\"}},{\"exists\":{\"field\":\"image_id\"}}"
                + (c.must.isEmpty() ? "" : "," + c.must) + "]";
        if (c.should.isEmpty()) return must;
        return must + ",\"should\":[" + c.should + "],\"minimum_should_match\":1";
    }

    /**
     * All paintings: five random pages of 100, kept when wider than tall (they fill a TV well). That search has
     * about 1,900 works but only its first 1,000 can be paged, so each page is taken in ascending or descending
     * id order: between the two, every work can turn up.
     *
     * <p>Any other collection is a small search (tens to a few hundred works): it is read in full, and upright
     * paintings are kept too (Art Mode shows those whole, on a dark ground), since many café and portrait
     * pictures are taller than wide.
     */
    private static List<Work> fetch(Context ctx, ArtCollections.Collection c) {
        List<Work> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        Random r = new Random();
        List<Integer> pages = new ArrayList<>();
        boolean whole = !c.isAll();
        double minAspect = whole ? 0.5 : 1.15;
        for (int i = 0; i < (whole ? MAX_PAGE : MAX_PAGE * 2); i++) pages.add(i);
        if (!whole) Collections.shuffle(pages, r);
        for (int k = 0; k < (whole ? MAX_PAGE : PAGES); k++) {
            int page = pages.get(k) % MAX_PAGE + 1;
            String order = pages.get(k) < MAX_PAGE ? "asc" : "desc";
            String params = "{\"query\":{\"bool\":{" + filter(c) + "}},"
                    + "\"sort\":[{\"id\":{\"order\":\"" + order + "\"}}],"
                    + "\"fields\":[\"id\",\"title\",\"artist_title\",\"date_display\",\"image_id\",\"color\",\"thumbnail\"]}";
            String url = "https://api.artic.edu/api/v1/artworks/search?limit=100&page=" + page
                    + "&params=" + NeonNet.enc(params);
            String body = NeonNet.text(ctx, url, HEADERS, WEEK);
            if (body == null) continue;
            try {
                JSONArray data = new JSONObject(body).getJSONArray("data");
                for (int i = 0; i < data.length(); i++) {
                    JSONObject o = data.getJSONObject(i);
                    JSONObject thumb = o.optJSONObject("thumbnail");
                    if (thumb == null || o.isNull("image_id")) continue;
                    double w = thumb.optDouble("width", 0), h = thumb.optDouble("height", 0);
                    if (h <= 0 || w / h < minAspect || w / h > 2.4) continue;
                    if (!seen.add(String.valueOf(o.optLong("id")))) continue;
                    JSONObject color = o.optJSONObject("color");
                    // Saturation 0-3: the museum has only a black-and-white photograph of the painting (about 1 in 20).
                    if (color != null && color.optInt("s", 50) <= 3) continue;
                    out.add(new Work(String.valueOf(o.optLong("id")), o.optString("title", "Untitled"),
                            o.isNull("artist_title") ? "Unknown artist" : o.optString("artist_title"),
                            o.isNull("date_display") ? "" : o.optString("date_display"),
                            o.getString("image_id"), color == null ? 50 : color.optInt("l", 50)));
                }
                if (whole && data.length() < 100) break; // The last page of a collection's search.
            } catch (Exception ignored) {
                // A malformed page: the others still count.
            }
        }
        return out;
    }

    private static List<Work> parse(String body) {
        List<Work> out = new ArrayList<>();
        if (body == null) return out;
        try {
            JSONArray a = new JSONArray(body);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Work(o.getString("id"), o.optString("title"), o.optString("artist_title"),
                        o.optString("date_display"), o.getString("image_id"), o.optInt("l", 50)));
            }
        } catch (Exception e) {
            out.clear();
        }
        return out;
    }

    private static String read(File f) {
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
