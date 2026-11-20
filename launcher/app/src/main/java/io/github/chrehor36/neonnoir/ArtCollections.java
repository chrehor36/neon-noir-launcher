package io.github.chrehor36.neonnoir;

import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The paintings Art Mode can show, chosen in Launcher settings > Art Mode: every painting, a mood (curated here),
 * or one of the Art Institute of Chicago's own labels (style, subject or theme). Each is a search on the museum's
 * open API, narrowed from its public-domain paintings with an image.
 *
 * <p>The museum labels few paintings with a style, so the moods mix labels, title words and painters: Jazz bar
 * is Tonalist nocturnes (Whistler, Inness) and Ashcan New York, Nightlife the café and cabaret painters (Degas,
 * Toulouse-Lautrec). Counts are what the museum had in November 2026, counting only paintings that fit a TV.
 */
final class ArtCollections {
    static final String ALL_ID = "all";
    private static final String KEY = "collection";

    static final class Collection {
        final String id, group, label;
        /** Extra "must" clauses (JSON, comma-separated) or "". */
        final String must;
        /** "should" clauses, at least one of which must match (JSON, comma-separated), or "". */
        final String should;

        Collection(String id, String group, String label, String must, String should) {
            this.id = id;
            this.group = group;
            this.label = label;
            this.must = must;
            this.should = should;
        }

        /** Every painting: a random sample of the whole collection, wide ones only, as Art Mode always did. */
        boolean isAll() {
            return ALL_ID.equals(id);
        }

        /** "Mood · Jazz bar", or just "All paintings". */
        String title() {
            return group.isEmpty() ? label : group + " · " + label;
        }
    }

    private static String term(String field, String value) {
        return "{\"term\":{\"" + field + ".keyword\":\"" + value + "\"}}";
    }

    private static String style(String v) {
        return term("style_titles", v);
    }

    private static String subject(String v) {
        return term("subject_titles", v);
    }

    private static String theme(String v) {
        return term("theme_titles", v);
    }

    /** Any of these words in the title. */
    private static String titleWords(String words) {
        return "{\"match\":{\"title\":\"" + words + "\"}}";
    }

    /** Any of these painters, each matched as a phrase so "Henri" alone does not bring in Matisse. */
    private static String painters(String... names) {
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            if (sb.length() > 0) sb.append(',');
            sb.append("{\"match_phrase\":{\"artist_title\":\"").append(n).append("\"}}");
        }
        return sb.toString();
    }

    private static String begun(int from, int to) {
        return "{\"range\":{\"date_start\":{\"gte\":" + from + ",\"lte\":" + to + "}}}";
    }

    private static String join(String... clauses) {
        return String.join(",", clauses);
    }

    private static final String[] TONALISTS = {"Whistler", "Inness", "Tryon", "Dewing", "Twachtman", "Blakelock",
            "Albert Pinkham Ryder"};
    private static final String[] ASHCAN = {"John Sloan", "Bellows", "Glackens", "Everett Shinn", "Robert Henri",
            "George Luks", "Ernest Lawson"};
    private static final String[] CAFE = {"Toulouse-Lautrec", "Degas", "Béraud", "Forain"};

    private static String[] concat(String[] a, String[] b) {
        String[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /** Western countries (and cities the museum uses as places), for the Romantic era. */
    private static final String WESTERN = "\"France\",\"England\",\"Great Britain\",\"United Kingdom\",\"Scotland\","
            + "\"Ireland\",\"Germany\",\"Austria\",\"Switzerland\",\"Italy\",\"Naples\",\"Rome\",\"Spain\","
            + "\"Netherlands\",\"Holland\",\"Belgium\",\"Denmark\",\"Norway\",\"Sweden\",\"Russia\",\"Poland\","
            + "\"Hungary\",\"United States\"";

    static final List<Collection> ALL = Collections.unmodifiableList(Arrays.asList(
            new Collection(ALL_ID, "", "All paintings", "", ""),

            // Moods, curated.
            new Collection("jazz_bar", "Mood", "Jazz bar", begun(1855, 1929), join(  // ~55
                    style("Ashcan School"), subject("music"), theme("Drinking and Dining"),
                    titleWords("nocturne night evening moonlight cafe café bar cabaret saloon"),
                    painters(concat(TONALISTS, ASHCAN)))),
            new Collection("nightlife", "Mood", "Nightlife", begun(1855, 1929), join(  // ~35
                    style("Ashcan School"), subject("music"), theme("Drinking and Dining"),
                    titleWords("cafe café cabaret theater theatre opera ball dance dancers dancer concert circus "
                            + "saloon bar music night"),
                    painters(concat(ASHCAN, CAFE)))),
            new Collection("after_dark", "Mood", "After dark", "", join(  // ~45
                    titleWords("night nocturne moonlight moonlit evening twilight dusk lamplight sunset"),
                    painters("Whistler", "Inness", "Albert Pinkham Ryder", "Blakelock"))),
            new Collection("romanticism", "Mood", "Romantic era", "", join(  // ~75
                    "{\"bool\":{\"must\":[" + begun(1780, 1850) + ",{\"terms\":{\"place_of_origin.keyword\":["
                            + WESTERN + "]}}]}}",
                    style("romantic"))),
            new Collection("quiet_landscapes", "Mood", "Quiet landscapes", "", join(  // ~200
                    style("Barbizon School"), style("Hudson River School"), subject("landscapes"))),
            new Collection("by_the_water", "Mood", "By the water", "", join(  // ~120
                    subject("boats"), subject("river"),
                    titleWords("sea harbor harbour coast beach boats river lake"))),
            new Collection("essentials", "Mood", "Museum essentials", "", join(  // ~70
                    theme("Essentials"), theme("Art Institute Icons"))),

            // The museum's styles with enough paintings to fill an evening.
            new Collection("style_impressionism", "Style", "Impressionism", "", style("Impressionism")),
            new Collection("style_post_impressionism", "Style", "Post-Impressionism", "", style("Post-Impressionism")),
            new Collection("style_realism", "Style", "Realism", "", style("Realism")),
            new Collection("style_modernism", "Style", "Modernism", "", style("Modernism")),
            new Collection("style_baroque", "Style", "Baroque", "", join(style("Baroque"), style("17th Century"))),
            new Collection("style_renaissance", "Style", "Renaissance", "", style("Renaissance")),
            new Collection("style_japanese", "Style", "Japanese", "", style("Japanese (culture or style)")),
            new Collection("style_chinese", "Style", "Chinese", "", style("Chinese (culture or style)")),

            // The museum's subjects.
            new Collection("subject_music", "Subject", "Music", "", subject("music")),
            new Collection("subject_leisure", "Subject", "Leisure", "", subject("leisure")),
            new Collection("subject_flowers", "Subject", "Flowers", "", subject("flowers")),
            new Collection("subject_animals", "Subject", "Animals", "", subject("animals")),
            new Collection("subject_horses", "Subject", "Horses", "", subject("horses")),
            new Collection("subject_sky", "Subject", "Clouds and sky", "", join(subject("clouds"), subject("sky"))),
            new Collection("subject_interiors", "Subject", "Interiors", "", subject("interior")),
            new Collection("subject_portraits", "Subject", "Portraits", "", subject("portraits")),

            // The museum's themes.
            new Collection("theme_women_artists", "Theme", "Women artists", "", theme("Women artists"))
    ));

    private ArtCollections() {
    }

    static Collection byId(String id) {
        for (Collection c : ALL) if (c.id.equals(id)) return c;
        return ALL.get(0);
    }

    static Collection current(Context ctx) {
        return byId(ctx.getSharedPreferences("neon_art", Context.MODE_PRIVATE).getString(KEY, ALL_ID));
    }

    static void set(Context ctx, String id) {
        ctx.getSharedPreferences("neon_art", Context.MODE_PRIVATE).edit().putString(KEY, id).apply();
    }

    static List<String> titles() {
        List<String> out = new ArrayList<>();
        for (Collection c : ALL) out.add(c.title());
        return out;
    }
}
