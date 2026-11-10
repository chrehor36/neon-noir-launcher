package io.github.chrehor36.neonnoir;

import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The Neon Noir Home, laid over the CleanYourTV launcher's own Home.
 *
 * The launcher underneath keeps doing everything it already does well: the Home button guard, the remote's key
 * helpers and the inputs menu. This overlay is everything you see: Home (clock, chips, dock), Jazz mode, More
 * (Picks and Scores), Live (channels and inputs) and All apps. Back returns to Home from the others; the Home button always lands on Home.
 *
 * Sizes are written in mockup pixels (the canvas boards are 1280x720) and converted by {@link NeonUi#m(float)}, so the
 * numbers here match the mockups one to one.
 */
final class NeonHome extends FrameLayout {
    enum Screen { HOME, JAZZ, MORE, APPS, LIVE }

    private static final String PREFS = "neon_home";
    private static final String KEY_LIGHT = "light_mode";
    private static final String KEY_AUTO_DARK = "auto_dark_night";
    private static final long WEATHER_EVERY = 30 * 60_000L;
    private static final long SCORES_EVERY = 2 * 60_000L;

    private final Activity activity;
    private final ViewGroup launcherRoot;
    private final NeonUi ui;

    private NeonPalette palette;
    private NeonBackgroundView background;
    private LinearLayout homeContent;
    private NeonJazzView jazz;
    private NeonMoreView more;
    private NeonAllAppsView apps;
    private NeonLiveView live;
    private View veil;
    private Screen screen = Screen.HOME;

    private NeonClockView clock;
    private TextView dayMain, dayRed, dayGold, dateLine;
    private LinearLayout chipRow, dock;
    private View moreLink;
    private ObjectAnimator flicker;
    private View firstTile;
    /** Content description of the Home button that opened Jazz, More or All apps. */
    private String returnFocus;

    // What the chips show; null hides the chip.
    private NeonRecents.App continueApp;
    private NeonRecords.Record tonight;
    private String tonightDay;
    private String scoresLine;
    private boolean scoresLive;
    private long weatherAt, scoresAt;
    private NeonSky.Phase shownPhase;

    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            tick();
            long now = System.currentTimeMillis();
            postDelayed(this, 60_000 - (now % 60_000) + 50);
        }
    };

    /** Adds the overlay on top of the activity's content and shows it. */
    static NeonHome attach(Activity activity, Runnable unused) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        ViewGroup launcherRoot = (ViewGroup) content.getChildAt(0);
        NeonHome home = new NeonHome(activity, launcherRoot);
        content.addView(home, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        home.show();
        return home;
    }

    private NeonHome(Activity activity, ViewGroup launcherRoot) {
        super(activity);
        this.activity = activity;
        this.launcherRoot = launcherRoot;
        applyNightDefault();
        palette = NeonPalette.of(prefs().getBoolean(KEY_LIGHT, false));
        ui = new NeonUi(activity, palette);
        setClickable(true);   // touches and clicks never fall through to the grid underneath
        // Clickable makes a view focusable on Android 8+. If Home itself held the focus, the remote's arrows
        // would search from a full-screen box and find nothing, so the focus must always sit on a tile.
        setFocusable(NOT_FOCUSABLE);
        build();
    }

    // ---- public hooks for MainActivity -----------------------------------------------------------------

    boolean isShowing() {
        return getVisibility() == VISIBLE;
    }

    /** Home button, Back from elsewhere, or first start: always the Home screen. */
    void show() {
        setVisibility(VISIBLE);
        // Hidden, not just covered: Android still draws covered views, so anything animating underneath would
        // redraw the whole screen.
        launcherRoot.setVisibility(INVISIBLE);
        launcherRoot.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        go(Screen.HOME);   // refreshes the content and focuses the first app
        // Android may already have put the focus on the first chip; the Home button always starts on the first app.
        post(() -> {
            if (screen == Screen.HOME && firstTile != null) firstTile.requestFocus();
        });
        startMotion();
    }

    private boolean pendingLive;

    /** The remote's Free TV button (app_keys "live"): More, scrolled to Live, on the first channel. */
    void showLive() {
        show();
        pendingLive = true;   // handed to More inside go(), before a cached channel list arrives in more.enter()
        go(Screen.MORE);
    }

    /** MainActivity.onResume: the recent apps and the chips may have changed while an app was open. */
    void onResume() {
        if (!isShowing()) return;
        if (applyNightDefault()) {
            rebuildForPalette();
            return;
        }
        if (screen == Screen.HOME) {
            refresh();
            post(this::focusFirst);
        } else if (screen == Screen.MORE) {
            more.resume();   // back from a channel or a pick: refreshed, keeping its place
        } else if (screen == Screen.LIVE) {
            live.resume();   // back from a channel: the guide refreshes and keeps its place
        } else if (screen == Screen.APPS) {
            apps.resume();   // back from Uninstall or App info: an app may be gone
        }
        startMotion();
    }

    /** MainActivity's package receiver: an app was installed, removed or switched off. */
    void packagesChanged() {
        if (!isShowing()) return;
        if (screen == Screen.APPS && apps != null) apps.resume();
        else if (screen == Screen.HOME) refresh();
    }

    void onPause() {
        stopMotion();
    }

    /** Art Mode (idle timer or the frame button). */
    void startArt() {
        activity.startActivity(new Intent(activity, ArtActivity.class));
    }

    // ---- layout ---------------------------------------------------------------------------------------------

    private int m(float mockupPx) {
        return ui.m(mockupPx);
    }

    private void build() {
        removeAllViews();
        jazz = null;
        more = null;
        apps = null;
        live = null;
        background = new NeonBackgroundView(getContext(), palette);
        addView(background, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        homeContent = ui.column();
        homeContent.setGravity(Gravity.CENTER_HORIZONTAL);
        // Side padding stays small: six banners plus five buttons need about 1190 mockup px of the 1280.
        homeContent.setPadding(m(32), m(30), m(32), m(18));
        homeContent.setClipChildren(false);
        addView(homeContent, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Center: clock, day name, date and weather.
        LinearLayout center = ui.column();
        center.setGravity(Gravity.CENTER);
        homeContent.addView(center, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        clock = new NeonClockView(getContext(), palette);
        center.addView(clock, new LinearLayout.LayoutParams(m(240), m(240)));

        FrameLayout day = new FrameLayout(getContext());
        dayGold = dayText(palette.edgeGold);
        dayRed = dayText(palette.edgeRed);
        dayMain = dayText(palette.text);
        dayGold.setTranslationX(m(2));
        dayRed.setTranslationX(-m(2));
        day.addView(dayGold);
        day.addView(dayRed);
        day.addView(dayMain);
        LinearLayout.LayoutParams dayLp = NeonUi.wrap();
        dayLp.topMargin = m(16);
        center.addView(day, dayLp);

        dateLine = ui.text(17, palette.textSecondary, ui.regular);
        dateLine.setLetterSpacing(0.3f);
        dateLine.setAllCaps(true);
        LinearLayout.LayoutParams dateLp = NeonUi.wrap();
        dateLp.topMargin = m(16);
        center.addView(dateLine, dateLp);

        // Chips above the dock: Tonight, Continue, Scores.
        chipRow = ui.row();
        chipRow.setGravity(Gravity.CENTER);
        chipRow.setClipChildren(false);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, m(54));
        chipLp.bottomMargin = m(18);
        homeContent.addView(chipRow, chipLp);

        // The dock: favorites, a divider, then All apps, Jazz mode, Art mode, Light/Dark and Settings.
        dock = ui.row();
        dock.setPadding(m(18), m(14), m(18), m(14));
        dock.setBackground(ui.glass(16));
        dock.setClipChildren(false);
        dock.setClipToPadding(false);
        homeContent.addView(dock, NeonUi.wrap());

        // "More" under the dock: Picks for you, Scores and Live.
        LinearLayout moreRow = ui.row();
        moreRow.setPadding(m(14), m(4), m(14), m(4));
        TextView chevron = ui.text(18, palette.label, ui.bold);
        chevron.setText("⌄");
        moreRow.addView(chevron);
        TextView moreText = ui.text(16, palette.label, ui.regular);
        moreText.setText("More");
        moreRow.addView(moreText, ui.wrap(6));
        moreRow.setContentDescription("More: Picks for you, Scores and Live");
        ui.linkButton(moreRow);
        moreRow.setOnClickListener(v -> go(Screen.MORE));
        LinearLayout.LayoutParams mlp = NeonUi.wrap();
        mlp.topMargin = m(8);
        homeContent.addView(moreRow, mlp);
        moreLink = moreRow;

        // Night mode: a warm veil over Home, Jazz, More and All apps (never over other apps).
        veil = new View(getContext());
        veil.setBackgroundColor(android.graphics.Color.argb(64, 26, 12, 0));
        veil.setVisibility(GONE);
        addView(veil, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        shownPhase = null;
        updateSky();
    }

    private TextView dayText(int color) {
        TextView t = ui.text(50, color, ui.regular);
        t.setLetterSpacing(0.42f);
        t.setAllCaps(true);
        // Letter-spacing adds space after the last letter too; the same space before it keeps the word centered.
        t.setPadding(Math.round(m(50) * 0.42f), 0, 0, 0);
        return t;
    }

    // ---- screens --------------------------------------------------------------------------------------------

    private void go(Screen next) {
        if (screen == Screen.JAZZ && jazz != null && next != Screen.JAZZ) jazz.leave();
        if (screen == Screen.MORE && more != null && next != Screen.MORE) more.leave();
        if (screen == Screen.LIVE && live != null && next != Screen.LIVE) live.leave();
        if (screen == Screen.HOME && next != Screen.HOME) {
            // Remember the button that opened the screen, so Back lands on it again.
            View f = homeContent.findFocus();
            returnFocus = f == null || f.getContentDescription() == null ? null : f.getContentDescription().toString();
        }
        boolean returning = screen != Screen.HOME && next == Screen.HOME;
        screen = next;
        homeContent.setVisibility(next == Screen.HOME ? VISIBLE : GONE);
        if (next == Screen.JAZZ && jazz == null) {
            jazz = new NeonJazzView(getContext(), ui, () -> go(Screen.HOME));
            addView(jazz, indexOfChild(veil), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        if (next == Screen.MORE && more == null) {
            more = new NeonMoreView(activity, ui, () -> go(Screen.HOME), () -> go(Screen.LIVE));
            addView(more, indexOfChild(veil), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        if (next == Screen.APPS && apps == null) {
            apps = new NeonAllAppsView(getContext(), ui, () -> go(Screen.HOME), this::refresh);
            addView(apps, indexOfChild(veil), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        if (next == Screen.LIVE && live == null) {
            live = new NeonLiveView(activity, ui, this::backToMore);
            addView(live, indexOfChild(veil), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        if (live != null) live.setVisibility(next == Screen.LIVE ? VISIBLE : GONE);
        if (jazz != null) jazz.setVisibility(next == Screen.JAZZ ? VISIBLE : GONE);
        if (more != null) more.setVisibility(next == Screen.MORE ? VISIBLE : GONE);
        if (apps != null) apps.setVisibility(next == Screen.APPS ? VISIBLE : GONE);
        updateSky();
        switch (next) {
            case JAZZ: jazz.enter(); break;
            case MORE:
                if (pendingLive) more.focusLiveWhenLoaded();
                pendingLive = false;
                more.enter();
                break;
            case APPS: apps.enter(); break;
            case LIVE: live.enter(); break;
            case HOME:
            default:
                refresh();
                if (returning) post(this::focusReturn);
                else post(this::focusFirst);
                break;
        }
    }

    /** Back from Jazz, More or All apps: the focus goes back to the button that opened it. */
    private void focusReturn() {
        if (screen != Screen.HOME) return;
        View target = returnFocus == null ? null : findByDescription(homeContent, returnFocus);
        if (target != null) target.requestFocus();
        else if (firstTile != null) firstTile.requestFocus();
    }

    private static View findByDescription(ViewGroup group, String desc) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View c = group.getChildAt(i);
            if (c.isFocusable() && c.getVisibility() == VISIBLE && desc.contentEquals(String.valueOf(c.getContentDescription()))) return c;
            if (c instanceof ViewGroup) {
                View found = findByDescription((ViewGroup) c, desc);
                if (found != null) return found;
            }
        }
        return null;
    }

    // ---- content ---------------------------------------------------------------------------------------------

    private void refresh() {
        updateDay();
        // Continue: the most recently used app, whether or not it is a favorite.
        List<NeonRecents.App> recent = NeonRecents.recent(getContext(), 1);
        continueApp = !recent.isEmpty() && recent.get(0).lastUsed > 0 ? recent.get(0) : null;
        buildChips();
        buildDock(NeonRecents.favorites(getContext()));
        loadTonight();
        long now = System.currentTimeMillis();
        if (now - scoresAt > SCORES_EVERY) loadScores();
        if (now - weatherAt > WEATHER_EVERY) loadWeather();
    }

    /** Once a minute while Home is on screen: day and date, sky, night, and the slower refreshes when due. */
    private void tick() {
        if (applyNightDefault()) {
            rebuildForPalette();
            return;
        }
        updateDay();
        updateSky();
        long now = System.currentTimeMillis();
        if (now - weatherAt > WEATHER_EVERY) loadWeather();
        if (screen == Screen.HOME && now - scoresAt > (scoresLive ? 60_000L : SCORES_EVERY)) loadScores();
        if (screen == Screen.HOME && !LocalDate.now().toString().equals(tonightDay)) loadTonight();
        if (screen == Screen.LIVE && live != null) live.tick();
    }

    private void updateDay() {
        Date now = new Date();
        String dayName = new SimpleDateFormat("EEEE", Locale.getDefault()).format(now);
        for (TextView t : new TextView[]{dayGold, dayRed, dayMain}) t.setText(dayName);
        String date = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(now);
        String weather = NeonSky.now(getContext()).weather;
        dateLine.setText(weather == null ? date : date + " · " + weather);
    }

    private void updateSky() {
        NeonSky.Now sky = NeonSky.now(getContext());
        if (screen == Screen.JAZZ) {
            background.setSky(palette.jazzBackground, palette.jazzGlows[0], palette.jazzGlows[1]);
            shownPhase = null;
        } else if (sky.phase != shownPhase) {
            int[] g = NeonSky.glows(sky.phase, palette.light);
            background.setSky(palette.background, g[0], g[1]);
            shownPhase = sky.phase;
        }
        // Night mode dims our own screens only (Home, Jazz, More, All apps), never the apps you open.
        veil.setVisibility(sky.night ? VISIBLE : GONE);
    }

    private void loadWeather() {
        weatherAt = System.currentTimeMillis();
        Context app = getContext().getApplicationContext();
        NeonNet.load(() -> NeonSky.refresh(app), ok -> {
            if (Boolean.TRUE.equals(ok)) {
                updateDay();
                updateSky();
            }
        });
    }

    private void loadTonight() {
        // Read every time Home shows: Pick another in Jazz mode changes tonight's record.
        tonightDay = LocalDate.now().toString();
        Context app = getContext().getApplicationContext();
        NeonNet.load(() -> NeonRecords.tonight(app), r -> {
            boolean same = r != null && tonight != null && r.title.equals(tonight.title) && r.artist.equals(tonight.artist);
            tonight = r;
            if (!same && screen == Screen.HOME) buildChips();
        });
    }

    private void loadScores() {
        scoresAt = System.currentTimeMillis();
        if (NeonConfig.teams().isEmpty() && NeonConfig.players().isEmpty()) return;
        Context app = getContext().getApplicationContext();
        NeonNet.load(() -> {
            // Your team's live game first, then a followed player's live match, then your team's next or last.
            NeonScores.Team team = NeonScores.featured(NeonScores.load(app));
            if (team != null && team.live != null) return new Object[]{NeonScores.chipLine(team), true};
            // Only a followed player's match takes the chip, not every top match of the day.
            for (NeonScores.Game g : NeonScores.playerMatches(app)) {
                if (g.live()) return new Object[]{NeonScores.chipLine(g), true};
            }
            // A live race (or sprint) does too; practice and qualifying don't.
            for (NeonScores.Game g : NeonScores.races(app)) {
                if (g.live() && (g.session.equals("Race") || g.session.equals("Sprint"))) {
                    return new Object[]{NeonScores.chipLine(g), true};
                }
            }
            return new Object[]{NeonScores.chipLine(team), false};
        }, r -> {
            if (r == null) return;
            String line = (String) r[0];
            boolean live = (Boolean) r[1];
            if (line == null ? scoresLine == null : line.equals(scoresLine) && live == scoresLive) return;
            scoresLine = line;
            scoresLive = live;
            if (screen == Screen.HOME) buildChips();
        });
    }

    // ---- chips ---------------------------------------------------------------------------------------------

    private void buildChips() {
        View focused = chipRow.findFocus();
        String focusedTag = focused == null ? null : (String) focused.getTag();
        chipRow.removeAllViews();
        if (tonight != null) {
            TextView kicker = ui.caps("Tonight", 15, palette.gold, true, 0.06f);
            TextView title = ui.text(17, palette.text, ui.serif);
            title.setText(tonight.title);
            title.setMaxWidth(m(300));
            TextView artist = ui.caps("· " + tonight.artist, 15, palette.label, false, 0.06f);
            artist.setMaxWidth(m(220));
            addChip("tonight", "Tonight: " + tonight.title + " by " + tonight.artist, v -> go(Screen.JAZZ), null, kicker, title, artist);
        }
        if (continueApp != null) {
            TextView kicker = ui.caps("Continue", 15, palette.label, false, 0.06f);
            TextView name = ui.caps(continueApp.label.toString(), 15, palette.text, true, 0.06f);
            name.setMaxWidth(m(260));
            String pkg = continueApp.pkg;
            addChip("continue", "Continue " + continueApp.label, v -> open(pkg), null, kicker, name);
        }
        if (scoresLine != null) {
            int dot = scoresLine.indexOf(" · ");
            TextView score = ui.caps(dot > 0 ? scoresLine.substring(0, dot) : scoresLine, 15, palette.text, true, 0.06f);
            TextView detail = ui.caps(dot > 0 ? scoresLine.substring(dot) : "", 15, palette.label, false, 0.06f);
            View live = null;
            if (scoresLive) {
                live = new View(getContext());
                GradientDrawable d = new GradientDrawable();
                d.setShape(GradientDrawable.OVAL);
                d.setColor(palette.liveDot);
                live.setBackground(d);
            }
            addChip("scores", "Scores: " + scoresLine, v -> go(Screen.MORE), live, score, detail);
        }
        chipRow.setVisibility(chipRow.getChildCount() == 0 ? INVISIBLE : VISIBLE);   // keeps its height
        if (focusedTag != null) {
            View again = chipRow.findViewWithTag(focusedTag);
            if (again != null) again.requestFocus();
            else focusFirst();
        }
    }

    private void addChip(String tag, String description, View.OnClickListener click, View lead, TextView... parts) {
        LinearLayout chip = ui.row();
        chip.setTag(tag);
        chip.setPadding(m(22), 0, m(22), 0);
        chip.setContentDescription(description);
        if (lead != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(m(9), m(9));
            lp.rightMargin = m(10);
            chip.addView(lead, lp);
        }
        for (int i = 0; i < parts.length; i++) chip.addView(parts[i], i == 0 ? NeonUi.wrap() : ui.wrap(10));
        ui.glassButton(chip, 10, palette.glassFill, palette.glassEdge);
        chip.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        if (chipRow.getChildCount() > 0) lp.leftMargin = m(14);
        chipRow.addView(chip, lp);
    }

    // ---- dock ----------------------------------------------------------------------------------------------

    private void buildDock(List<NeonRecents.App> favorites) {
        String focusedDesc = null;
        View f = dock.findFocus();
        if (f != null && f.getContentDescription() != null) focusedDesc = f.getContentDescription().toString();
        dock.removeAllViews();
        firstTile = null;
        for (NeonRecents.App app : favorites) {
            View tile = tile(app);
            tile.setOnLongClickListener(v -> {
                startMove(app);
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(m(132), Math.round(m(132) * 9f / 16f));
            lp.rightMargin = m(14);
            dock.addView(tile, lp);
            if (firstTile == null) firstTile = tile;
        }
        if (favorites.isEmpty()) {
            // No favorites: one tile that says how to add them.
            FrameLayout add = ui.tile(v -> go(Screen.APPS));
            add.setContentDescription("Add favorites");
            TextView t = ui.caps("+ Add favorites", 13, palette.tileText, true, 0.08f);
            t.setGravity(Gravity.CENTER);
            add.addView(t, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(m(132), Math.round(m(132) * 9f / 16f));
            lp.rightMargin = m(14);
            dock.addView(add, lp);
            firstTile = add;
        }

        View divider = new View(getContext());
        divider.setBackgroundColor(NeonUi.withAlpha(palette.text, 38));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ui.hair(), m(52));
        dlp.rightMargin = m(14);
        dock.addView(divider, dlp);

        dock.addView(ui.iconButton(R.drawable.nn_ic_apps, "All apps", v -> go(Screen.APPS)), iconLp());
        dock.addView(ui.iconButton(R.drawable.nn_ic_jazz, "Jazz mode", v -> go(Screen.JAZZ)), iconLp());
        dock.addView(ui.iconButton(R.drawable.nn_ic_art, "Art mode", v -> startArt()), iconLp());
        dock.addView(ui.iconButton(palette.light ? R.drawable.nn_ic_moon : R.drawable.nn_ic_sun,
                palette.light ? "Switch to dark mode" : "Switch to light mode", v -> toggleMode()), iconLp());
        ImageView gear = ui.iconButton(R.drawable.nn_ic_settings, "Settings",
                v -> activity.startActivity(new Intent(activity, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS)));
        gear.setOnLongClickListener(v -> {
            // Hold OK on the gear: the TV's own Settings.
            try {
                activity.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException ignored) {
                // No Settings app answers: nothing to do.
            }
            return true;
        });
        dock.addView(gear, iconLp());

        if (focusedDesc != null) {
            for (int i = 0; i < dock.getChildCount(); i++) {
                View c = dock.getChildAt(i);
                if (c.isFocusable() && focusedDesc.contentEquals(String.valueOf(c.getContentDescription()))) {
                    c.requestFocus();
                    break;
                }
            }
        }
    }

    private View tile(NeonRecents.App app) {
        FrameLayout tile = ui.tile(v -> open(app.pkg));
        tile.setContentDescription(app.label);
        if (app.banner != null) {
            ImageView banner = new ImageView(getContext());
            banner.setImageDrawable(app.banner);
            banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.addView(banner, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        } else {
            LinearLayout inner = ui.column();
            inner.setGravity(Gravity.CENTER);
            ImageView icon = new ImageView(getContext());
            icon.setImageDrawable(app.icon);
            inner.addView(icon, new LinearLayout.LayoutParams(m(30), m(30)));
            TextView name = ui.caps(app.label.toString(), 12, palette.tileText, true, 0.08f);
            name.setPadding(m(6), m(4), m(6), 0);
            inner.addView(name);
            tile.addView(inner, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        if (app.pkg.equals(movingPkg)) {
            // Being moved: arrows over a dark veil.
            TextView arrows = ui.caps("‹   ›", 26, Color.WHITE, true, 0f);
            arrows.setGravity(Gravity.CENTER);
            arrows.setBackgroundColor(0x99000000);
            tile.addView(arrows, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        return tile;
    }

    // ---- moving a favorite: Hold OK on it, ‹ › to move, OK or Back when done ---------------------------------

    private String movingPkg;
    /** The OK that ends a move must be a new press, not the release of the hold that started it. */
    private boolean moveOkArmed;

    private void startMove(NeonRecents.App app) {
        movingPkg = app.pkg;
        moveOkArmed = false;
        buildDock(NeonRecents.favorites(getContext()));
        Toast.makeText(getContext(), "Move " + app.label + " with ‹ ›, then OK. To remove it, Hold OK on it in All apps.",
                Toast.LENGTH_LONG).show();
    }

    private void endMove() {
        movingPkg = null;
        buildDock(NeonRecents.favorites(getContext()));
    }

    /** Keys while a favorite is being moved; true when consumed. */
    private boolean moveKey(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down && NeonRecents.moveFavorite(getContext(), movingPkg, code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)) {
                    buildDock(NeonRecents.favorites(getContext()));
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (down && event.getRepeatCount() == 0) moveOkArmed = true;
                else if (!down && moveOkArmed) endMove();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (!down) endMove();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return true;
            default:
                endMove();
                return false;
        }
    }

    private LinearLayout.LayoutParams iconLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(m(44), m(44));
        lp.leftMargin = m(4);
        lp.rightMargin = m(4);
        return lp;
    }

    private void focusFirst() {
        if (screen == Screen.HOME && firstTile != null && !homeContent.hasFocus()) firstTile.requestFocus();
    }

    private void open(String pkg) {
        if (!NeonRecents.launch(getContext(), pkg)) {
            Toast.makeText(getContext(), "That app can't be opened", Toast.LENGTH_SHORT).show();
        }
    }

    // ---- light / dark ---------------------------------------------------------------------------------------

    private void toggleMode() {
        prefs().edit().putBoolean(KEY_LIGHT, !palette.light).apply();
        rebuildForPalette();
        // Keep the focus on the toggle so pressing OK again switches straight back.
        View toggle = dock.getChildAt(dock.getChildCount() - 2);
        post(toggle::requestFocus);
    }

    private void rebuildForPalette() {
        palette = NeonPalette.of(prefs().getBoolean(KEY_LIGHT, false));
        ui.palette = palette;
        stopMotion();
        Screen was = screen;
        screen = Screen.HOME;
        build();
        go(was);
        startMotion();
    }

    /**
     * Light mode switches to dark when night falls, once per night (so it can still be switched back by hand).
     * True when it changed the setting.
     */
    private boolean applyNightDefault() {
        SharedPreferences p = prefs();
        if (!NeonSky.isNight(getContext())) return false;
        // The night's key: the date it started (after midnight still counts as the evening before).
        String night = NeonSky.minuteOfDay() < 12 * 60 ? LocalDate.now().minusDays(1).toString() : LocalDate.now().toString();
        if (night.equals(p.getString(KEY_AUTO_DARK, ""))) return false;
        boolean wasLight = p.getBoolean(KEY_LIGHT, false);
        p.edit().putString(KEY_AUTO_DARK, night).putBoolean(KEY_LIGHT, false).apply();
        return wasLight && palette != null;
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- motion ---------------------------------------------------------------------------------------------

    private void startMotion() {
        removeCallbacks(minuteTick);
        postDelayed(minuteTick, 60_000 - (System.currentTimeMillis() % 60_000) + 50);
        if (flicker == null) {
            // The day name blinks briefly once every 9 s, as in the mockup.
            Keyframe[] k = {
                    Keyframe.ofFloat(0f, 1f), Keyframe.ofFloat(0.92f, 1f), Keyframe.ofFloat(0.93f, 0.55f),
                    Keyframe.ofFloat(0.95f, 1f), Keyframe.ofFloat(0.97f, 0.7f), Keyframe.ofFloat(1f, 1f)};
            flicker = ObjectAnimator.ofPropertyValuesHolder((View) dayMain.getParent(), PropertyValuesHolder.ofKeyframe(View.ALPHA, k));
            flicker.setDuration(9000);
            flicker.setRepeatCount(ValueAnimator.INFINITE);
        }
        if (!flicker.isStarted()) flicker.start();
        background.start();
    }

    private void stopMotion() {
        removeCallbacks(minuteTick);
        if (background != null) background.stop();
        if (flicker != null) {
            flicker.cancel();
            flicker = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopMotion();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (movingPkg != null && screen == Screen.HOME && moveKey(event)) return true;
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            // The All apps menu closes itself first.
            if (screen == Screen.APPS && apps != null && apps.menuOpen()) return super.dispatchKeyEvent(event);
            // Back from the full guide returns to More; from Jazz, More or All apps to Home; on Home it goes nowhere.
            if (screen != Screen.HOME && event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                if (screen == Screen.LIVE) backToMore();
                else go(Screen.HOME);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /** From the full guide back to More, with the focus on its Full guide card. */
    private void backToMore() {
        go(Screen.MORE);
        post(() -> {
            if (screen == Screen.MORE) more.focusGuideCard();
        });
    }
}
