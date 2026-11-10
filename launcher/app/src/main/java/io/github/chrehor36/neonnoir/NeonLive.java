package io.github.chrehor36.neonnoir;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Live guide's data: the TV's own channel list (TvProvider), the same one Google TV's Live tab reads. It holds
 * the antenna channels after a scan in the Live TV app, and the channels that apps such as Pluto TV, Plex or Haystack
 * publish there, each with what's on now and next.
 *
 * Other apps' channels need the READ_EPG_DATA permission; without it, only this app's own channels come back.
 */
final class NeonLive {
    static final String PERMISSION = "com.android.providers.tv.permission.READ_EPG_DATA";
    private static final int MAX_CHANNELS = 300;
    private static final long WINDOW = 4 * 60 * 60_000L;

    /** Apps with their own live guides, shown as buttons when installed. */
    static final String[][] LIVE_APPS = {
            {"Pluto TV", "tv.pluto.android"},
            {"Tubi", "com.tubitv"},
            {"Roku Channel", "com.roku.web.trc", "com.roku.trc"},
            {"Peacock", "com.peacocktv.peacockandroid"},
            {"YouTube TV", "com.google.android.youtube.tvunplugged"},
            {"Sling", "com.sling"},
            {"Plex", "com.plexapp.android"},
            {"Haystack News", "com.haystack.android"},
    };

    static final class Program {
        final String title;
        final long start, end;

        Program(String title, long start, long end) {
            this.title = title;
            this.start = start;
            this.end = end;
        }
    }

    static final class Channel {
        final long id;
        final String inputId, number, name;
        String source = "";
        Program now, next;

        Channel(long id, String inputId, String number, String name) {
            this.id = id;
            this.inputId = inputId == null ? "" : inputId;
            this.number = number == null ? "" : number;
            this.name = name == null ? "" : name;
        }
    }

    private NeonLive() {
    }

    static boolean canReadAll(Context ctx) {
        return ctx.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Browsable channels, antenna first (by channel number), then each app's channels; with now and next. */
    static List<Channel> channels(Context ctx) {
        List<Channel> list = new ArrayList<>();
        ContentResolver cr = ctx.getContentResolver();
        Map<Long, Channel> byId = new HashMap<>();
        String[] cols = {TvContract.Channels._ID, TvContract.Channels.COLUMN_INPUT_ID,
                TvContract.Channels.COLUMN_DISPLAY_NUMBER, TvContract.Channels.COLUMN_DISPLAY_NAME};
        // No browsable column: only system apps may read it. TvProvider already leaves out other apps' channels
        // that the Live TV app hasn't approved (and the ones hidden there), so what comes back is the channel list.
        try (Cursor c = cr.query(TvContract.Channels.CONTENT_URI, cols, null, null, null)) {
            if (c == null) return list;
            while (c.moveToNext() && list.size() < MAX_CHANNELS) {
                Channel ch = new Channel(c.getLong(0), c.getString(1), c.getString(2), c.getString(3));
                list.add(ch);
                byId.put(ch.id, ch);
            }
        } catch (RuntimeException e) {
            // Logged so a TV that refuses the query can be told apart from one with no channels (adb logcat -s NeonLive).
            android.util.Log.w("NeonLive", "Couldn't read the channel list", e);
            return list;
        }
        if (list.isEmpty()) return list;

        Map<String, String> sources = new HashMap<>();
        Map<String, Boolean> tuners = new HashMap<>();
        TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        for (Channel ch : list) {
            if (!sources.containsKey(ch.inputId)) {
                String label = "";
                boolean tuner = false;
                try {
                    TvInputInfo info = tim == null ? null : tim.getTvInputInfo(ch.inputId);
                    if (info != null) {
                        CharSequence l = info.loadLabel(ctx);
                        label = l == null ? "" : l.toString();
                        // A tuner that ships with the TV is the antenna.
                        tuner = info.getType() == TvInputInfo.TYPE_TUNER
                                && (info.getServiceInfo().applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
                    }
                } catch (RuntimeException ignored) {
                }
                sources.put(ch.inputId, tuner ? "Antenna" : label);
                tuners.put(ch.inputId, tuner);
            }
            ch.source = sources.get(ch.inputId);
        }

        long now = System.currentTimeMillis();
        Uri programs = TvContract.Programs.CONTENT_URI.buildUpon()
                .appendQueryParameter("start_time", String.valueOf(now))
                .appendQueryParameter("end_time", String.valueOf(now + WINDOW)).build();
        String[] pcols = {TvContract.Programs.COLUMN_CHANNEL_ID, TvContract.Programs.COLUMN_TITLE,
                TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS, TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS};
        try (Cursor c = cr.query(programs, pcols, null, null, TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS)) {
            while (c != null && c.moveToNext()) {
                Channel ch = byId.get(c.getLong(0));
                if (ch == null) continue;
                Program p = new Program(c.getString(1) == null ? "" : c.getString(1), c.getLong(2), c.getLong(3));
                if (p.start <= now && p.end > now) ch.now = p;
                else if (p.start > now && (ch.next == null || p.start < ch.next.start)) ch.next = p;
            }
        } catch (RuntimeException ignored) {
            // No guide data: the channels still show and tune.
        }

        Collections.sort(list, (a, b) -> {
            boolean ta = tuners.get(a.inputId), tb = tuners.get(b.inputId);
            if (ta != tb) return ta ? -1 : 1;
            int s = a.source.compareToIgnoreCase(b.source);
            if (s != 0) return s;
            int n = compareNumbers(a.number, b.number);
            return n != 0 ? n : a.name.compareToIgnoreCase(b.name);
        });
        return list;
    }

    /** "2.1" before "11.1", "5" before "5.2". */
    static int compareNumbers(String a, String b) {
        String[] x = a.split("[.\\-]"), y = b.split("[.\\-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            if (i >= x.length) return -1;
            if (i >= y.length) return 1;
            int c;
            try {
                c = Integer.compare(Integer.parseInt(x[i].trim()), Integer.parseInt(y[i].trim()));
            } catch (NumberFormatException e) {
                c = x[i].compareToIgnoreCase(y[i]);
            }
            if (c != 0) return c;
        }
        return 0;
    }

    /** The channel's logo from the channel list, scaled to about the given height, or null. */
    static Bitmap logo(Context ctx, long channelId, int height) {
        try (InputStream in = ctx.getContentResolver().openInputStream(TvContract.buildChannelLogoUri(channelId))) {
            if (in == null) return null;
            Bitmap b = BitmapFactory.decodeStream(in);
            if (b == null || b.getHeight() <= height * 2) return b;
            return Bitmap.createScaledBitmap(b, Math.max(1, b.getWidth() * height / b.getHeight()), height, true);
        } catch (Exception e) {
            return null;
        }
    }

    /** "9:30 PM". */
    static String time(long ms) {
        return new java.text.SimpleDateFormat("h:mm a", Locale.US).format(new java.util.Date(ms));
    }

    /** The installed package of a LIVE_APPS entry, or null. */
    static String installed(Context ctx, String[] entry) {
        PackageManager pm = ctx.getPackageManager();
        for (int i = 1; i < entry.length; i++) {
            if (pm.getLeanbackLaunchIntentForPackage(entry[i]) != null || pm.getLaunchIntentForPackage(entry[i]) != null) {
                return entry[i];
            }
        }
        return null;
    }
}
