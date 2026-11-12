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
 * CC0 data). A pool of a few hundred landscape-shaped works is kept on disk and refreshed weekly; at night only
 * the darker ones are shown (the API gives each work's dominant color lightness).
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

    /** v3: pools from before the 1,000-result fix and the black-and-white filter; a new name makes every TV fetch afresh. */
    private static final String POOL = "art_pool_v3.json";
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
        List<Work> pool = pool(ctx);
        if (pool.isEmpty()) return null;
        List<Work> fit = new ArrayList<>();
        for (Work w : pool) if (!night || w.lightness <= NIGHT_MAX_LIGHTNESS) fit.add(w);
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

    private static synchronized List<Work> pool(Context ctx) {
        File f = new File(ctx.getFilesDir(), POOL);
        List<Work> cached = f.exists() ? parse(read(f)) : Collections.emptyList();
        if (!cached.isEmpty() && System.currentTimeMillis() - f.lastModified() < WEEK) return cached;
        List<Work> fetched = fetch(ctx);
        if (fetched.size() >= 20) {
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

    /**
     * Five random pages of 100 public-domain paintings, kept when wider than tall (they fill a TV well). The
     * search has about 1,900 works but only its first 1,000 can be paged, so each page is taken in ascending
     * or descending id order: between the two, every work can turn up.
     */
    private static List<Work> fetch(Context ctx) {
        List<Work> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        Random r = new Random();
        List<Integer> pages = new ArrayList<>();
        for (int i = 0; i < MAX_PAGE * 2; i++) pages.add(i);
        Collections.shuffle(pages, r);
        for (int k = 0; k < PAGES; k++) {
            int page = pages.get(k) % MAX_PAGE + 1;
            String order = pages.get(k) < MAX_PAGE ? "asc" : "desc";
            String params = "{\"query\":{\"bool\":{\"must\":[{\"term\":{\"is_public_domain\":true}},"
                    + "{\"term\":{\"artwork_type_title.keyword\":\"Painting\"}},{\"exists\":{\"field\":\"image_id\"}}]}},"
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
                    if (h <= 0 || w / h < 1.15 || w / h > 2.4) continue;
                    if (!seen.add(String.valueOf(o.optLong("id")))) continue;
                    JSONObject color = o.optJSONObject("color");
                    // Saturation 0-3: the museum has only a black-and-white photograph of the painting (about 1 in 20).
                    if (color != null && color.optInt("s", 50) <= 3) continue;
                    out.add(new Work(String.valueOf(o.optLong("id")), o.optString("title", "Untitled"),
                            o.isNull("artist_title") ? "Unknown artist" : o.optString("artist_title"),
                            o.isNull("date_display") ? "" : o.optString("date_display"),
                            o.getString("image_id"), color == null ? 50 : color.optInt("l", 50)));
                }
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
