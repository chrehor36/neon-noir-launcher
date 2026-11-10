package io.github.chrehor36.neonnoir;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.io.OutputStream;

/**
 * Debug builds only: fills the TV's channel list with made-up test channels (five "antenna" channels and five free
 * streaming ones), each with half-hour programs from an hour ago to four hours ahead, and a text logo.
 * Running it again replaces them. Every name here is invented.
 */
public final class DemoChannelsActivity extends Activity {
    private static final String[][] ANTENNA = {
            {"2.1", "Metro 2", "#8E1B1B"}, {"5.1", "Channel Five", "#1B4E8E"}, {"7.1", "Seven", "#2E6B2E"},
            {"11.1", "Public TV", "#5A3A8A"}, {"11.2", "Public Kids", "#B07A12"}};
    private static final String[][] FREE = {
            {"101", "Classic Movies", "#3A3A3A"}, {"102", "Comedy All Day", "#9A4A12"}, {"103", "Sports Rewind", "#12608A"},
            {"104", "Weather Now", "#2F7F8F"}, {"105", "Kitchen Live", "#7A2A4A"}};
    private static final String[] SHOWS = {
            "Evening News", "Game Night", "The Nature Hour", "Late Kitchen", "Mystery Theater", "Morning Desk",
            "College Football Preview", "Classic Cartoons", "The Long Drive", "Weather Watch", "City Stories",
            "Quiz Time", "The Night Shift", "Old Westerns", "Weekend Sports"};

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ContentResolver cr = getContentResolver();
        try {
            // Only this app's own channels can be deleted, so this never touches real ones.
            cr.delete(TvContract.Channels.CONTENT_URI, null, null);
            int made = fill(cr, DemoTvInputService.Antenna.class, ANTENNA, TvContract.Channels.TYPE_ATSC_T, 0)
                    + fill(cr, DemoTvInputService.Free.class, FREE, TvContract.Channels.TYPE_OTHER, 5);
            Toast.makeText(this, made + " test channels added", Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            // Home runs in this process: a failure here must not take it down.
            Toast.makeText(this, "Couldn't add test channels: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        finish();
    }

    private int fill(ContentResolver cr, Class<?> service, String[][] channels, String type, int seed) {
        String inputId = TvContract.buildInputId(new ComponentName(this, service));
        long slot = 30 * 60_000L;
        long start = (System.currentTimeMillis() / slot) * slot - 2 * slot;
        int n = 0;
        for (String[] c : channels) {
            ContentValues v = new ContentValues();
            v.put(TvContract.Channels.COLUMN_INPUT_ID, inputId);
            v.put(TvContract.Channels.COLUMN_TYPE, type);
            v.put(TvContract.Channels.COLUMN_DISPLAY_NUMBER, c[0]);
            v.put(TvContract.Channels.COLUMN_DISPLAY_NAME, c[1]);
            Uri uri = cr.insert(TvContract.Channels.CONTENT_URI, v);
            if (uri == null) continue;
            long id = Long.parseLong(uri.getLastPathSegment());
            writeLogo(cr, id, c[1], Color.parseColor(c[2]));
            for (int i = 0; i < 12; i++) {
                // Some shows run an hour, so the progress bars differ.
                long len = ((n + i + seed) % 3 == 0) ? 2 * slot : slot;
                ContentValues pv = new ContentValues();
                pv.put(TvContract.Programs.COLUMN_CHANNEL_ID, id);
                pv.put(TvContract.Programs.COLUMN_TITLE, SHOWS[(n * 4 + i + seed) % SHOWS.length]);
                pv.put(TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS, start);
                pv.put(TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS, start + len);
                cr.insert(TvContract.Programs.CONTENT_URI, pv);
                start += len;
            }
            start = (System.currentTimeMillis() / slot) * slot - 2 * slot + (n % 2) * slot;
            n++;
        }
        return n;
    }

    /** A plain colored badge with the channel's initials, as a stand-in logo. */
    private static void writeLogo(ContentResolver cr, long id, String name, int color) {
        Bitmap b = Bitmap.createBitmap(160, 80, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(color);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(40);
        p.setTextAlign(Paint.Align.CENTER);
        StringBuilder initials = new StringBuilder();
        for (String w : name.split(" ")) if (!w.isEmpty()) initials.append(w.charAt(0));
        c.drawText(initials.toString(), 80, 54, p);
        try (OutputStream out = cr.openOutputStream(TvContract.buildChannelLogoUri(id))) {
            if (out != null) b.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception ignored) {
        }
    }
}
