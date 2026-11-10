package io.github.chrehor36.neonnoir;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;

/**
 * Weather and the sky: today's sunrise and sunset, the glow phase (dawn, day, sunset, night, rain), night mode
 * (set times from Launcher settings) and the "72° · Partly cloudy" text on Home's date line. The glows still
 * follow the sun; only night mode's dimming uses the set times.
 *
 * Weather comes from Open-Meteo (no account) for the place in secrets.properties. Without a place there is no
 * weather, and the sun times fall back to 07:00 and 18:30 as in the launcher's own wallpaper moods.
 */
final class NeonSky {
    enum Phase { DAWN, DAY, SUNSET, NIGHT, RAIN }

    private static final String PREFS = "neon_sky";
    private static final long WEATHER_MAX_AGE = 25 * 60_000L;

    /** What Home shows. */
    static final class Now {
        final Phase phase;
        final boolean night;
        /** "72° · Partly cloudy", or null when there is no weather. */
        final String weather;

        Now(Phase phase, boolean night, String weather) {
            this.phase = phase;
            this.night = night;
            this.weather = weather;
        }
    }

    private NeonSky() {
    }

    /** From what is already known (cached weather and sun times): fast, main thread. */
    static Now now(Context ctx) {
        SharedPreferences p = prefs(ctx);
        int[] sun = sunTimes(ctx);
        int min = minuteOfDay();
        boolean fresh = System.currentTimeMillis() - p.getLong("at", 0) < 3 * 3600_000L;
        int code = fresh ? p.getInt("code", -1) : -1;
        String weather = null;
        if (fresh && p.contains("temp")) {
            weather = Math.round(p.getFloat("temp", 0)) + "°" + (code >= 0 ? " · " + describe(code) : "");
        }
        int[] t = nightTimes(ctx);
        return new Now(phase(min, sun[0], sun[1], rainy(code)), inWindow(min, t[0], t[1]), weather);
    }

    // ---- Night mode: set times, chosen in Launcher settings (default 9:00 PM to 6:00 AM) -------------------

    static final String NIGHT_START_KEY = "night_start", NIGHT_END_KEY = "night_end";
    static final int DEFAULT_NIGHT_START = 21 * 60, DEFAULT_NIGHT_END = 6 * 60;

    /** {start, end} of night mode in minutes of the day. */
    static int[] nightTimes(Context ctx) {
        SharedPreferences s = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        return new int[]{s.getInt(NIGHT_START_KEY, DEFAULT_NIGHT_START), s.getInt(NIGHT_END_KEY, DEFAULT_NIGHT_END)};
    }

    /** True between the start and end times (across midnight when the start is later); never when they match. */
    static boolean isNight(Context ctx) {
        int[] t = nightTimes(ctx);
        return inWindow(minuteOfDay(), t[0], t[1]);
    }

    static boolean inWindow(int min, int start, int end) {
        if (start == end) return false;
        return start < end ? min >= start && min < end : min >= start || min < end;
    }

    /** "9:00 PM" for 1260. */
    static String clockLabel(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        return (h % 12 == 0 ? 12 : h % 12) + ":" + (m < 10 ? "0" : "") + m + (h < 12 ? " AM" : " PM");
    }

    /** Minutes until the night state next changes (at least 1). */
    static int minutesToNextChange(Context ctx) {
        int[] t = nightTimes(ctx);
        int min = minuteOfDay();
        int[] edges = {t[0], t[1], t[0] + 1440, t[1] + 1440};
        int best = 1440;
        for (int e : edges) if (e > min) best = Math.min(best, e - min);
        return Math.max(1, best);
    }

    static Phase phase(int min, int sunrise, int sunset, boolean rain) {
        if (min < sunrise - 30 || min >= sunset + 45) return Phase.NIGHT;
        if (rain) return Phase.RAIN;
        if (min < sunrise + 60) return Phase.DAWN;
        if (min >= sunset - 60) return Phase.SUNSET;
        return Phase.DAY;
    }

    /** {glowA, glowB} for the backdrop, from design/tokens.json sky_glows. */
    static int[] glows(Phase phase, boolean light) {
        switch (phase) {
            case DAWN: return light ? new int[]{rgba(240, 150, 140, .40), rgba(240, 200, 120, .40)} : new int[]{rgba(200, 60, 50, .40), rgba(226, 176, 74, .26)};
            case SUNSET: return light ? new int[]{rgba(226, 80, 50, .34), rgba(240, 180, 80, .42)} : new int[]{rgba(214, 60, 30, .60), rgba(226, 176, 74, .32)};
            // Light night and rain stay warm and pale: cool grays on cream look like haze.
            case NIGHT: return light ? new int[]{rgba(190, 150, 175, .22), rgba(210, 110, 100, .18)} : new int[]{rgba(30, 60, 130, .45), rgba(168, 19, 29, .30)};
            case RAIN: return light ? new int[]{rgba(215, 195, 185, .30), rgba(220, 150, 125, .18)} : new int[]{rgba(90, 100, 120, .42), rgba(120, 40, 40, .24)};
            case DAY:
            default: return light ? new int[]{rgba(214, 60, 60, .26), rgba(230, 180, 90, .34)} : new int[]{rgba(168, 19, 29, .55), rgba(40, 80, 150, .30)};
        }
    }

    private static int rgba(int r, int g, int b, double a) {
        return Color.argb((int) Math.round(a * 255), r, g, b);
    }

    /** Network: refreshes the weather and today's sun times when they are older than 25 minutes. Background thread. */
    static boolean refresh(Context ctx) {
        double[] at = NeonConfig.location(ctx);
        if (at == null) return false;
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + at[0] + "&longitude=" + at[1]
                + "&current=temperature_2m,weather_code&daily=sunrise,sunset&timezone=auto&forecast_days=1"
                + (NeonConfig.celsius() ? "" : "&temperature_unit=fahrenheit");
        String body = NeonNet.text(ctx, url, null, WEATHER_MAX_AGE);
        if (body == null) return false;
        try {
            JSONObject j = new JSONObject(body);
            JSONObject cur = j.getJSONObject("current");
            SharedPreferences.Editor e = prefs(ctx).edit();
            e.putFloat("temp", (float) cur.getDouble("temperature_2m"));
            e.putInt("code", cur.optInt("weather_code", -1));
            e.putLong("at", System.currentTimeMillis());
            JSONObject daily = j.optJSONObject("daily");
            if (daily != null) {
                JSONArray rise = daily.optJSONArray("sunrise"), set = daily.optJSONArray("sunset");
                if (rise != null && set != null && rise.length() > 0 && set.length() > 0) {
                    e.putString("sun_day", LocalDate.now().toString());
                    e.putInt("sunrise", minuteOf(rise.getString(0)));
                    e.putInt("sunset", minuteOf(set.getString(0)));
                }
            }
            e.apply();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /** {sunrise, sunset} in local minutes: Open-Meteo's for today, else computed from the place, else 07:00/18:30. */
    static int[] sunTimes(Context ctx) {
        SharedPreferences p = prefs(ctx);
        if (LocalDate.now().toString().equals(p.getString("sun_day", "")) && p.contains("sunrise")) {
            return new int[]{p.getInt("sunrise", 420), p.getInt("sunset", 1110)};
        }
        double[] at = NeonConfig.location(ctx);
        if (at != null) {
            int[] s = Sun.riseAndSet(LocalDate.now(), at[0], at[1], ZoneId.systemDefault());
            if (s != null) return s;
        }
        return new int[]{7 * 60, 18 * 60 + 30};
    }

    private static int minuteOf(String isoLocal) {
        LocalDateTime t = LocalDateTime.parse(isoLocal);
        return t.getHour() * 60 + t.getMinute();
    }

    static int minuteOfDay() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    static boolean rainy(int code) {
        return (code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95;
    }

    /** WMO weather code in plain words. */
    static String describe(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 55) return "Drizzle";
        if (code == 56 || code == 57) return "Freezing drizzle";
        if (code >= 61 && code <= 65) return code == 65 ? "Heavy rain" : "Rain";
        if (code == 66 || code == 67) return "Freezing rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorms";
        return "";
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
