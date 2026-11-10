package io.github.chrehor36.neonnoir;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/**
 * Jazz mode's way into Spotify: open the app, a playlist, or a search for the record on the turntable. It uses
 * Spotify's own spotify: links, so it needs no Spotify account setup in the launcher.
 */
final class NeonSpotify {
    static final String TV_APP = "com.spotify.tv.android";
    static final String PHONE_APP = "com.spotify.music";

    /** A playlist or an album. The name of one of yours is filled in by loadNames(). */
    static final class Playlist {
        final String id, type;
        volatile String name;

        Playlist(String type, String id, String name) {
            this.type = type;
            this.id = id;
            this.name = name;
        }

        String uri() {
            return "spotify:" + type + ":" + id;
        }
    }

    /** Spotify's own jazz playlists (checked to exist, Nov 2026). secrets.properties SPOTIFY_PLAYLISTS replaces them. */
    private static final String[][] BUILT_IN = {
            {"37i9dQZF1DXbITWG1ZJKYt", "Jazz Classics"},
            {"37i9dQZF1DX4wta20PHgwo", "Late Night Jazz"},
            {"37i9dQZF1DWVqfgj8NZEp1", "Coffee Table Jazz"},
            {"37i9dQZF1DX0SM0LYsmbMT", "Jazz Vibes"},
            {"37i9dQZF1DX2vYju3i0lNX", "Chilled Jazz"},
            {"37i9dQZF1DX5OepaGriAIm", "Women of Jazz"},
            {"37i9dQZF1DWTbzY5gOVvKd", "Jazzy Romance"},
            {"37i9dQZF1DWV7EzJMK2FUI", "Jazz in the Background"},
            {"37i9dQZF1DWYxwmBaMqxsl", "Rainy Day Jazz"},
            {"37i9dQZF1DX7YCknf2jT6s", "State of Jazz"},
            {"37i9dQZF1DXdwTUxmGKrdN", "Smooth Jazz"},
    };

    private NeonSpotify() {
    }

    /** SPOTIFY_PLAYLISTS (the first is the main one), or the built-in jazz playlists. */
    static List<Playlist> playlists() {
        List<Playlist> out = new ArrayList<>();
        List<String[]> mine = NeonConfig.spotifyItems();
        if (!mine.isEmpty()) {
            for (String[] m : mine) out.add(new Playlist(m[0], m[1], m[0].equals("album") ? "Album" : "Playlist"));
            return out;
        }
        for (String[] p : BUILT_IN) out.add(new Playlist("playlist", p[0], p[1]));
        return out;
    }

    /**
     * Background thread. Fills in the names of your own playlists and albums from Spotify's public oEmbed page
     * (no account needed), cached for a week. True if any name changed.
     */
    static boolean loadNames(Context ctx, List<Playlist> list) {
        if (NeonConfig.spotifyItems().isEmpty()) return false;
        java.util.concurrent.atomic.AtomicBoolean changed = new java.util.concurrent.atomic.AtomicBoolean();
        List<java.util.concurrent.Callable<Void>> jobs = new ArrayList<>();
        for (Playlist p : list) {
            jobs.add(() -> {
                String body = NeonNet.text(ctx, "https://open.spotify.com/oembed?url="
                        + NeonNet.enc("https://open.spotify.com/" + p.type + "/" + p.id), null, 7 * 24 * 3600_000L);
                if (body == null) return null;
                try {
                    String title = clean(new org.json.JSONObject(body).optString("title", ""));
                    if (!title.isEmpty() && !title.equals(p.name)) {
                        p.name = title;
                        changed.set(true);
                    }
                } catch (Exception ignored) {
                    // Keep the placeholder.
                }
                return null;
            });
        }
        // Several at once: the first look-up of a long list would otherwise take a while.
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        try {
            pool.invokeAll(jobs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdown();
        }
        return changed.get();
    }

    /** Drops emoji and other symbols the TV's fonts may not have. */
    private static String clean(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); ) {
            int c = s.codePointAt(i);
            i += Character.charCount(c);
            int type = Character.getType(c);
            if (c >= 0x10000 || type == Character.OTHER_SYMBOL || type == Character.SURROGATE
                    || type == Character.PRIVATE_USE || c == 0xFE0F || c == 0x200D) continue;
            b.appendCodePoint(c);
        }
        return b.toString().replaceAll("\\s+", " ").trim();
    }

    static String installed(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        for (String p : new String[]{TV_APP, PHONE_APP}) {
            try {
                pm.getPackageInfo(p, 0);
                return p;
            } catch (PackageManager.NameNotFoundException ignored) {
                // Try the next.
            }
        }
        return null;
    }

    /** Opens Spotify's home. False when Spotify is not installed. */
    static boolean open(Context ctx) {
        String pkg = installed(ctx);
        return pkg != null && NeonRecents.launch(ctx, pkg);
    }

    static boolean openPlaylist(Context ctx, Playlist p) {
        return view(ctx, p.uri());
    }

    /** Searches Spotify for "title artist" (the record on the turntable). */
    static boolean search(Context ctx, String query) {
        return view(ctx, "spotify:search:" + Uri.encode(query));
    }

    private static boolean view(Context ctx, String uri) {
        String pkg = installed(ctx);
        if (pkg == null) return false;
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
            LastOpened.record(ctx, pkg);
            return true;
        } catch (RuntimeException e) {
            // This Spotify build takes no links: at least open the app.
            return open(ctx);
        }
    }
}
