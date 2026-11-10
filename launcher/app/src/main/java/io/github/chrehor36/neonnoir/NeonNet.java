package io.github.chrehor36.neonnoir;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Small HTTP helper for the Neon Home: GET with short timeouts, off the main thread, with a file cache so the
 * screens show the last good answer when the network is down. No library: HttpURLConnection is plenty here.
 */
final class NeonNet {
    private static final String AGENT = "NeonNoirLauncher/1.0 (Android TV home screen)";
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private NeonNet() {
    }

    static Handler main() {
        return MAIN;
    }

    static void run(Runnable job) {
        IO.execute(() -> {
            try {
                job.run();
            } catch (RuntimeException ignored) {
                // A bad answer from a service must never crash Home.
            }
        });
    }

    /** Runs work in the background and hands its result (possibly null) to the main thread. */
    static <T> void load(java.util.concurrent.Callable<T> work, Consumer<T> done) {
        IO.execute(() -> {
            T result;
            try {
                result = work.call();
            } catch (Exception e) {
                result = null;
            }
            T r = result;
            MAIN.post(() -> done.accept(r));
        });
    }

    /**
     * Text from the URL, served from the cache while it is younger than maxAgeMs. When the network fails, an
     * older cached copy is returned instead (stale beats empty on a TV). Null when there is nothing at all.
     */
    static String text(Context ctx, String url, Map<String, String> headers, long maxAgeMs) {
        File f = cacheFile(ctx, url);
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < maxAgeMs) {
            String cached = read(f);
            if (cached != null) return cached;
        }
        try {
            byte[] body = fetch(url, headers);
            write(f, body);
            return new String(body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return f.exists() ? read(f) : null;
        }
    }

    /** A picture, cached on disk; decoded at most maxWidth wide in RGB_565 (half the memory, no alpha needed). */
    static Bitmap image(Context ctx, String url, int maxWidth) {
        File f = cacheFile(ctx, url);
        if (!f.exists()) {
            try {
                write(f, fetch(url, null));
            } catch (IOException e) {
                return null;
            }
        } else {
            f.setLastModified(System.currentTimeMillis());
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), bounds);
        if (bounds.outWidth <= 0) {
            f.delete();
            return null;
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2;
        o.inSampleSize = sample;
        trim(ctx);
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    static byte[] fetch(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent", AGENT);
        c.setRequestProperty("Accept-Encoding", "identity");
        if (headers != null) for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
        try {
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > 12 * 1024 * 1024) throw new IOException("too large");
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    static String enc(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }

    private static File dir(Context ctx) {
        File d = new File(ctx.getCacheDir(), "neon");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File cacheFile(Context ctx, String url) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) sb.append(String.format("%02x", h[i]));
            return new File(dir(ctx), sb.toString());
        } catch (Exception e) {
            return new File(dir(ctx), Integer.toHexString(url.hashCode()));
        }
    }

    private static String read(File f) {
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static void write(File f, byte[] body) {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(body);
        } catch (IOException e) {
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(f)) tmp.delete();
    }

    /** Keeps the cache under about 120 MB: oldest files go first. */
    private static void trim(Context ctx) {
        File[] files = dir(ctx).listFiles();
        if (files == null) return;
        long total = 0;
        for (File f : files) total += f.length();
        if (total < 120L * 1024 * 1024) return;
        java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File f : files) {
            if (total < 90L * 1024 * 1024) break;
            total -= f.length();
            f.delete();
        }
    }
}
