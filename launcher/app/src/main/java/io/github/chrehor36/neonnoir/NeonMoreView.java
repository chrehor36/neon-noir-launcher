package io.github.chrehor36.neonnoir;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * More (the link under the dock, or the Scores chip): Picks for you, 7 posters of popular titles on your services
 * (three pages, turned with the arrow),
 * and Scores, a card per conference, tennis tournament and race (NFC, SEC, Paris Masters, São Paulo GP).
 * Below them, scrolled to with Down: Live, what's on the first channels, a Full guide card, and the Antenna, HDMI
 * and live-app buttons. Together with the full guide this replaces Google TV's Live tab.
 */
final class NeonMoreView extends FrameLayout {
    private static final int PICKS = 7;
    /** Picks for you has three pages of seven, turned with the arrow at the end of the row. */
    private static final int PAGES = 3;

    /** Channel cards in the Live section before the Full guide card. */
    private static final int LIVE_CARDS = 4;

    private final NeonUi ui;
    private final NeonPalette p;
    private final android.app.Activity activity;
    private final Runnable onBack, openGuide;
    private LinearLayout picksRow, scoresRow, posters, liveRow, watchRow;
    private View watchScroll;
    private android.widget.ScrollView scroll;
    private List<NeonPicks.Pick> allPicks = new java.util.ArrayList<>();
    private final List<View> pickDots = new java.util.ArrayList<>();
    private int picksPage, posterWidth;
    private View backLink, guideCard;
    private int loadGeneration;

    NeonMoreView(android.app.Activity activity, NeonUi ui, Runnable onBack, Runnable openGuide) {
        super(activity);
        this.activity = activity;
        this.ui = ui;
        this.p = ui.palette;
        this.onBack = onBack;
        this.openGuide = openGuide;
        build();
    }

    private void build() {
        // Picks and Scores fill the first screen; Live is below them, and the page scrolls down to it.
        // Android's own ScrollView jumps just far enough to show the newly focused view, and Live then added a second,
        // smooth scroll: the page moved twice, which looked jittery. This one never moves on its own; glide() moves
        // it once, smoothly, to where the focused section rests (Picks at the top, Scores under its heading, Live
        // at the bottom).
        scroll = new android.widget.ScrollView(getContext()) {
            @Override
            protected int computeScrollDeltaToGetChildRectOnScreen(android.graphics.Rect rect) {
                return 0;
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        LinearLayout col = ui.column();
        col.setPadding(ui.m(64), ui.m(26), ui.m(64), ui.m(34));
        col.setClipChildren(false);
        col.setClipToPadding(false);
        scroll.addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout back = ui.row();
        back.setPadding(ui.m(14), ui.m(6), ui.m(14), ui.m(6));
        TextView up = ui.text(18, p.label, ui.bold);
        up.setText("⌃");
        back.addView(up);
        TextView backText = ui.caps("Back to Home", 15, p.label, false, 0.14f);
        back.addView(backText, ui.wrap(8));
        ui.linkButton(back);
        back.setOnClickListener(v -> onBack.run());
        LinearLayout.LayoutParams blp = NeonUi.wrap();
        blp.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(back, blp);
        backLink = back;

        col.addView(header("Picks for you"), topMargin(18));
        picksRow = ui.row();
        picksRow.setGravity(Gravity.TOP);
        picksRow.setClipChildren(false);
        col.addView(picksRow, matchWidth(14));

        scoresHeader = header("Scores");
        col.addView(scoresHeader, topMargin(18));
        scoresRow = ui.row();
        scoresRow.setGravity(Gravity.TOP);
        col.addView(scoresRow, matchWidth(12));

        col.addView(header("Live"), topMargin(30));
        liveRow = ui.row();
        liveRow.setClipChildren(false);
        col.addView(liveRow, matchWidth(14));
        android.widget.HorizontalScrollView ws = new android.widget.HorizontalScrollView(getContext());
        ws.setHorizontalScrollBarEnabled(false);
        ws.setClipChildren(false);
        ws.setClipToPadding(false);
        ws.setPadding(ui.m(4), ui.m(6), ui.m(4), ui.m(6));
        watchRow = ui.row();
        watchRow.setClipChildren(false);
        ws.addView(watchRow);
        LinearLayout.LayoutParams wlp = matchWidth(10);
        wlp.leftMargin = -ui.m(4);
        col.addView(ws, wlp);
        watchScroll = ws;
    }

    private static boolean scoresConfigured() {
        return !NeonConfig.teams().isEmpty() || !NeonConfig.groups().isEmpty() || !NeonConfig.players().isEmpty()
                || !NeonConfig.racing().isEmpty();
    }

    /** Shown: load (cached answers make this quick) and put the focus on the first poster. */
    void enter() {
        scroll.scrollTo(0, 0);
        load();
        // Always the first poster, even if the focus was left in Live last time. Free TV's focusSoon() runs
        // after this and moves it to Live.
        post(() -> {
            focusFirst();
            scroll.scrollTo(0, 0);   // a focus left in Live last time may have pulled the page down
        });
    }

    private boolean liveHasFocus() {
        return liveRow.hasFocus() || watchRow.hasFocus();
    }

    /** Back from a channel, an input or a pick: refreshed, with the focus where it was. */
    void resume() {
        load();
    }

    private View scoresHeader;
    private android.animation.ObjectAnimator glider;

    /** Where the page rests for the focused view: Picks at the top, Scores under its heading, Live at the bottom. */
    private int restingScroll(View focused) {
        int max = Math.max(0, scroll.getChildAt(0).getHeight() - scroll.getHeight());
        if (isDescendant(liveRow, focused) || isDescendant(watchRow, focused)) return max;
        if (isDescendant(scoresRow, focused)) return Math.max(0, Math.min(max, scoresHeader.getTop() - ui.m(20)));
        return 0;
    }

    /** One smooth move to y (no-op when already there); a new move replaces one still running. */
    private void glide(int y) {
        if (glider != null) glider.cancel();
        if (scroll.getScrollY() == y) return;
        glider = android.animation.ObjectAnimator.ofInt(scroll, "scrollY", y);
        glider.setDuration(320);
        glider.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
        glider.start();
    }

    /** Every focus move inside More glides the page to that section's resting place. */
    private final android.view.ViewTreeObserver.OnGlobalFocusChangeListener liveScroller = (old, now) -> {
        if (now == null || scroll == null || !isDescendant(this, now)) return;
        scroll.post(() -> glide(restingScroll(now)));
    };

    private static boolean isDescendant(View parent, View v) {
        for (android.view.ViewParent x = v.getParent(); x != null; x = x.getParent()) {
            if (x == parent) return true;
        }
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalFocusChangeListener(liveScroller);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnGlobalFocusChangeListener(liveScroller);
        super.onDetachedFromWindow();
    }

    /** Set by the remote's Free TV button: the next channel load puts the focus on the first channel. */
    private boolean focusLive;

    void focusLiveWhenLoaded() {
        focusLive = true;
    }

    /** Back from the full guide: the focus on its card, in the Live section. */
    void focusGuideCard() {
        if (guideCard != null && guideCard.isAttachedToWindow()) guideCard.requestFocus();
    }

    private void load() {
        int gen = ++loadGeneration;
        Context app = getContext().getApplicationContext();
        if (picksRow.getChildCount() == 0) showPicksNote(NeonPicks.configured() ? "Loading picks…" : null);
        if (scoresRow.getChildCount() == 0) showScoresNote(scoresConfigured() ? "Loading scores…" : null);
        NeonNet.load(() -> NeonPicks.load(app, PICKS * PAGES), picks -> {
            if (gen == loadGeneration) showPicks(picks);
        });
        NeonNet.load(() -> NeonScores.cards(app, NeonScores.load(app)), cards -> {
            if (gen == loadGeneration) showCards(cards);
        });
        watchScroll.setVisibility(NeonWatch.fill(activity, ui, watchRow) > 0 ? VISIBLE : GONE);
        NeonNet.load(() -> NeonLive.channels(app), channels -> {
            if (gen == loadGeneration) showLive(channels);
        });
    }

    void leave() {
        loadGeneration++;
    }

    private void focusFirst() {
        View first = posters != null && posters.getParent() == picksRow && posters.getChildCount() > 0
                ? posters.getChildAt(0) : backLink;
        first.requestFocus();
    }

    // ---- Picks ---------------------------------------------------------------------------------------------

    private void showPicks(List<NeonPicks.Pick> picks) {
        if (!NeonPicks.configured()) {
            showPicksNote(null);
            return;
        }
        if (picks == null || picks.isEmpty()) {
            showPicksNote("No picks right now: check the internet connection or the TMDB key.");
            return;
        }
        boolean hadFocus = picksRow.hasFocus();
        picksRow.removeAllViews();
        allPicks = picks;
        picksPage = 0;
        pickDots.clear();
        int pages = Math.min(PAGES, (picks.size() + PICKS - 1) / PICKS);
        int gap = ui.m(18);
        int arrowSpace = pages > 1 ? gap + ui.m(40) : 0;
        posterWidth = (getResources().getDisplayMetrics().widthPixels - ui.m(128) - gap * (PICKS - 1) - arrowSpace) / PICKS;
        posters = ui.row();
        posters.setGravity(Gravity.TOP);
        posters.setClipChildren(false);
        picksRow.addView(posters, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (pages > 1) {
            // The arrow and page dots at the end of the row: Right past the last poster, then OK turns the page.
            LinearLayout arrowCol = ui.column();
            arrowCol.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout arrow = ui.row();
            arrow.setGravity(Gravity.CENTER);
            arrow.setContentDescription("More picks");
            TextView chevron = ui.text(20, p.text, ui.bold);
            chevron.setText("›");
            arrow.addView(chevron);
            ui.glassButton(arrow, 2, p.glassFill, p.glassEdge);
            arrow.setOnClickListener(v -> showPicksPage(picksPage + 1));
            arrowCol.addView(arrow, new LinearLayout.LayoutParams(ui.m(40), ui.m(40)));
            LinearLayout dotRow = ui.row();
            for (int i = 0; i < pages; i++) {
                View d = new View(getContext());
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ui.m(7), ui.m(7));
                dlp.leftMargin = i == 0 ? 0 : ui.m(5);
                dotRow.addView(d, dlp);
                pickDots.add(d);
            }
            LinearLayout.LayoutParams drp = NeonUi.wrap();
            drp.topMargin = ui.m(10);
            arrowCol.addView(dotRow, drp);
            LinearLayout.LayoutParams alp = NeonUi.wrap();
            alp.leftMargin = gap;
            alp.gravity = Gravity.CENTER_VERTICAL;
            picksRow.addView(arrowCol, alp);
        }
        showPicksPage(0);
        if (hadFocus || backLink.hasFocus() || !hasFocus()) posters.getChildAt(0).requestFocus();
    }

    /** One page of seven posters; the arrow wraps from the last page back to the first. */
    private void showPicksPage(int next) {
        int pages = Math.max(1, Math.min(PAGES, (allPicks.size() + PICKS - 1) / PICKS));
        picksPage = ((next % pages) + pages) % pages;
        posters.removeAllViews();
        int gap = ui.m(18);
        int width = posterWidth;
        // Shorter than a full 2:3 poster (cropped at the edges) so Scores and Around fit below on one screen.
        int height = Math.round(width * 1.2f);
        int from = picksPage * PICKS;
        for (int i = 0; i < PICKS && from + i < allPicks.size(); i++) {
            NeonPicks.Pick pick = allPicks.get(from + i);
            FrameLayout tile = ui.tile(v -> {
                if (!NeonPicks.open(getContext(), pick)) {
                    Toast.makeText(getContext(), "Couldn't open " + pick.service, Toast.LENGTH_SHORT).show();
                }
            });
            tile.setContentDescription(pick.title + " on " + pick.service);
            LinearLayout inner = ui.column();
            ImageView poster = new ImageView(getContext());
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable ph = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{NeonUi.withAlpha(0xFF2A0C0A, 255), NeonUi.withAlpha(0xFF120505, 255)});
            poster.setBackground(ph);
            inner.addView(poster, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            LinearLayout info = ui.column();
            info.setPadding(ui.m(12), ui.m(10), ui.m(12), ui.m(10));
            TextView title = ui.caps(pick.title, 15, p.text, true, 0.06f);
            info.addView(title);
            TextView service = ui.caps(pick.service, 13, p.gold, false, 0.12f);
            LinearLayout.LayoutParams slp = NeonUi.wrap();
            slp.topMargin = ui.m(4);
            info.addView(service, slp);
            inner.addView(info);
            tile.addView(inner, 0, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
            if (i > 0) lp.leftMargin = gap;
            posters.addView(tile, lp);
            Context app = getContext().getApplicationContext();
            int gen = loadGeneration;
            NeonNet.load(() -> NeonNet.image(app, pick.posterUrl, width), (Bitmap b) -> {
                if (b != null && gen == loadGeneration) poster.setImageBitmap(b);
            });
        }
        for (int i = 0; i < pickDots.size(); i++) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(i == picksPage ? p.gold : NeonUi.withAlpha(p.gold, 60));
            pickDots.get(i).setBackground(g);
        }
    }

    private void showPicksNote(String text) {
        picksRow.removeAllViews();
        picksRow.addView(note(text != null ? text
                : "Add a free TMDB key (TMDB_API_KEY in secrets.properties) to see what's popular on your services."),
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.m(90)));
    }

    // ---- Scores --------------------------------------------------------------------------------------------

    /** Up to four cards side by side (NFC, SEC, Paris Masters, São Paulo GP); fewer cards get wider. */
    private void showCards(List<NeonScores.Card> cards) {
        if (!scoresConfigured()) {
            showScoresNote(null);
            return;
        }
        if (cards == null || cards.isEmpty()) {
            showScoresNote("Nothing on right now.");
            return;
        }
        scoresRow.removeAllViews();
        int n = Math.min(4, cards.size());
        for (int i = 0; i < n; i++) scoresRow.addView(card(cards.get(i)), weighted(1f, i > 0));
    }

    private void showScoresNote(String text) {
        scoresRow.removeAllViews();
        scoresRow.addView(note(text != null ? text
                : "Add your teams (SCORES_TEAMS in secrets.properties, e.g. basketball/nba/bos) to see live scores."),
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.m(90)));
    }

    private LinearLayout panel() {
        LinearLayout panel = ui.column();
        panel.setPadding(ui.m(22), ui.m(18), ui.m(22), ui.m(18));
        panel.setBackground(ui.glass(14));
        panel.setMinimumHeight(ui.m(150));
        return panel;
    }

    /** The title, then each game, with a faint rule between them. */
    private LinearLayout card(NeonScores.Card c) {
        LinearLayout panel = ui.column();
        panel.setPadding(ui.m(20), ui.m(16), ui.m(20), ui.m(16));
        panel.setBackground(ui.glass(12));
        panel.addView(ui.caps(c.title, 17, p.text, true, 0.16f));
        for (int i = 0; i < c.games.size(); i++) {
            if (i > 0) {
                View rule = new View(getContext());
                rule.setBackgroundColor(NeonUi.withAlpha(p.label, 45));
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.hair());
                rlp.topMargin = ui.m(12);
                panel.addView(rule, rlp);
            }
            panel.addView(entry(c.games.get(i)), matchWidth(12));
        }
        return panel;
    }

    /** "SUN · 8:20 PM" (a red dot when live), then the two sides; an upcoming race is just its line. */
    private LinearLayout entry(NeonScores.Game g) {
        LinearLayout e = ui.column();
        LinearLayout top = ui.row();
        if (g.live()) {
            View dot = new View(getContext());
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(p.liveDot);
            dot.setBackground(d);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.m(7), ui.m(7));
            lp.rightMargin = ui.m(7);
            top.addView(dot, lp);
        }
        top.addView(ui.caps(status(g), 12, g.live() ? p.gold : p.label, g.live(), 0.12f));
        e.addView(top);
        if (g.racing && !g.live() && !g.done()) return e;
        boolean scored = g.live() || g.done();
        boolean prefix = g.racing || g.away.rank > 0 || g.home.rank > 0;
        e.addView(sideLine(g, g.away, scored, prefix, false), matchWidth(7));
        e.addView(sideLine(g, g.home, scored, prefix, !scored && !g.tennis && !g.racing), matchWidth(4));
        return e;
    }

    /** "Live · Q3 4:12", "Round 2 · Final", "Mon · Final", "Qualifying · Sat · 3:00 PM", "Sun · 8:20 PM". */
    private static String status(NeonScores.Game g) {
        String tag = g.tag;
        if (g.live()) {
            if (g.tennis) return (tag.isEmpty() ? "" : tag + " · ") + "Live";
            return (tag.isEmpty() ? "Live" : tag) + (g.detail.isEmpty() ? "" : " · " + g.detail);
        }
        if (g.done()) {
            return (tag.isEmpty() ? NeonScores.when(g.startMs).replaceFirst(" · .*", "").replace("Tonight", "Today") : tag) + " · Final";
        }
        String when = NeonScores.when(g.startMs);
        if (g.racing || g.tennis) {
            // "Practice 1 · Tomorrow · 10:30 AM" doesn't fit a card: the weekday instead.
            when = when.replace("Tomorrow", java.time.Instant.ofEpochMilli(g.startMs).atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("EEE", java.util.Locale.US)));
        }
        return (tag.isEmpty() ? "" : tag + " · ") + when;
    }

    /**
     * One side: "#7 Texas" (a poll or tour ranking, or a driver's place) and the score, or the record before the
     * game. The winner is bold; the loser dims.
     */
    private LinearLayout sideLine(NeonScores.Game g, NeonScores.Side s, boolean scored, boolean prefix, boolean at) {
        LinearLayout r = ui.row();
        boolean decided = g.done() && (g.away.winner || g.home.winner);
        boolean lost = decided && !s.winner;
        int color = lost ? p.label : p.text;
        if (prefix) {
            String mark = g.racing ? s.score.replace("P", "") : s.rank > 0 ? "#" + s.rank : "";
            TextView rk = ui.text(14, p.label, ui.regular);
            rk.setText(mark);
            r.addView(rk, new LinearLayout.LayoutParams(ui.m(g.tennis ? 44 : 34), LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        TextView name = ui.text(17, color, decided && s.winner ? ui.bold : ui.regular);
        name.setText((at ? "at " : "") + s.name);
        r.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        String right = g.racing ? "" : scored ? s.score : s.record;
        TextView sc = ui.text(17, scored ? color : p.label, scored && !lost ? ui.bold : ui.regular);
        sc.setText(right);
        LinearLayout.LayoutParams slp = NeonUi.wrap();
        slp.leftMargin = ui.m(10);
        r.addView(sc, slp);
        return r;
    }

    // ---- Live ----------------------------------------------------------------------------------------------

    /** What's on: the first channels as cards (antenna first), then the Full guide card. */
    private void showLive(List<NeonLive.Channel> channels) {
        View f = liveRow.findFocus();
        Object focusedTag = f == null ? null : f.getTag();
        liveRow.removeAllViews();
        guideCard = null;
        if (channels == null || channels.isEmpty()) {
            boolean antenna = activity instanceof MainActivity && ((MainActivity) activity).liveTvAppIntent() != null;
            liveRow.addView(note(antenna
                            ? "No channels yet. Open Antenna below and scan for channels, or open a live app once so it adds its channels."
                            : "No channels yet. Open a live app once so it adds its channels here."),
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.m(90)));
            if (focusLive) {
                if (watchScroll.getVisibility() == VISIBLE && watchRow.getChildCount() > 0) focusSoon(watchRow);
                else focusFirst();
            }
            focusLive = false;
            return;
        }
        int n = Math.min(LIVE_CARDS, channels.size());
        Context app = getContext().getApplicationContext();
        int gen = loadGeneration;
        int logoH = ui.m(22);
        for (int i = 0; i < n; i++) {
            NeonLive.Channel ch = channels.get(i);
            LinearLayout card = channelCard(ch);
            liveRow.addView(card, weighted(1f, i > 0));
            if (focusedTag != null && focusedTag.equals(ch.id)) card.requestFocus();
            ImageView logo = card.findViewWithTag("logo");
            NeonNet.load(() -> NeonLive.logo(app, ch.id, logoH), (Bitmap b) -> {
                if (b != null && gen == loadGeneration) {
                    logo.setImageBitmap(b);
                    logo.setVisibility(VISIBLE);
                }
            });
        }

        LinearLayout guide = ui.column();
        guide.setGravity(Gravity.CENTER);
        guide.setPadding(ui.m(16), ui.m(14), ui.m(16), ui.m(14));
        guide.setTag("guide");
        guide.setContentDescription("Full guide, " + channels.size() + " channels");
        guide.addView(ui.caps("Full guide ›", 15, p.text, true, 0.1f));
        TextView count = ui.caps(channels.size() + (channels.size() == 1 ? " channel" : " channels"), 12, p.label, false, 0.12f);
        LinearLayout.LayoutParams clp = NeonUi.wrap();
        clp.topMargin = ui.m(4);
        guide.addView(count, clp);
        ui.glassButton(guide, 10, p.glassFill, p.glassEdge);
        guide.setOnClickListener(v -> openGuide.run());
        liveRow.addView(guide, weighted(0.7f, true));
        guideCard = guide;
        if ("guide".equals(focusedTag)) guide.requestFocus();
        if (focusLive) focusSoon(liveRow);
        focusLive = false;
    }

    /** Free TV: the first item of the row, once the screen has finished resuming (which moves the focus). */
    private void focusSoon(LinearLayout row) {
        postDelayed(() -> {
            if (row.getChildCount() == 0 || getVisibility() != VISIBLE) return;
            row.getChildAt(0).requestFocus();
            // The screen is still settling (picks and scores loading, the resume's layout passes), which can put the
            // page back at the top: keep it at the bottom for the first moments, as long as Live has the focus.
            for (int delay : new int[]{0, 150, 400, 800, 1500, 2500}) {
                scroll.postDelayed(() -> {
                    if (liveHasFocus() && getVisibility() == VISIBLE) {
                        scroll.scrollTo(0, scroll.getChildAt(0).getHeight() - scroll.getHeight());
                    }
                }, delay);
            }
        }, 250);
    }

    /** "5.1 · logo", the channel's name, what's on, and a bar to its end. OK tunes it. */
    private LinearLayout channelCard(NeonLive.Channel ch) {
        LinearLayout card = ui.column();
        card.setPadding(ui.m(16), ui.m(12), ui.m(16), ui.m(14));
        card.setTag(ch.id);
        String nowTitle = ch.now == null ? "" : ch.now.title;
        card.setContentDescription(ch.number + " " + ch.name + (nowTitle.isEmpty() ? "" : ", " + nowTitle));

        LinearLayout top = ui.row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView num = ui.text(15, p.gold, ui.bold);
        num.setText(ch.number);
        top.addView(num, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ImageView logo = new ImageView(getContext());
        logo.setTag("logo");
        logo.setScaleType(ImageView.ScaleType.FIT_END);
        logo.setVisibility(INVISIBLE);
        top.addView(logo, new LinearLayout.LayoutParams(ui.m(48), ui.m(22)));
        card.addView(top);

        TextView name = ui.text(16, p.text, ui.bold);
        name.setText(ch.name);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(name, matchWidth(8));

        TextView title = ui.text(15, nowTitle.isEmpty() ? p.label : p.textSecondary, ui.regular);
        title.setText(nowTitle.isEmpty() ? "No guide information" : nowTitle);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(title, matchWidth(3));

        if (ch.now != null) {
            long t = System.currentTimeMillis();
            float done = Math.max(0f, Math.min(1f, (t - ch.now.start) / (float) Math.max(1, ch.now.end - ch.now.start)));
            LinearLayout track = ui.row();
            View fill = new View(getContext());
            fill.setBackgroundColor(p.gold);
            View rest = new View(getContext());
            rest.setBackgroundColor(NeonUi.withAlpha(p.label, 60));
            track.addView(fill, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, Math.max(0.001f, done)));
            track.addView(rest, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, Math.max(0.001f, 1f - done)));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(2, ui.m(2.5f)));
            tlp.topMargin = ui.m(10);
            card.addView(track, tlp);
            card.addView(ui.caps("Until " + NeonLive.time(ch.now.end), 11, p.label, false, 0.1f), matchWidth(6));
        }
        ui.glassButton(card, 10, p.glassFill, p.glassEdge);
        card.setOnClickListener(v -> {
            if (activity instanceof MainActivity) ((MainActivity) activity).tuneChannel(ch.id);
        });
        return card;
    }

    // ---- bits ----------------------------------------------------------------------------------------------

    private LinearLayout header(String title) {
        LinearLayout r = ui.row();
        r.setGravity(Gravity.BOTTOM);
        r.addView(ui.edgeTitle(title, 26, 0.32f));
        return r;
    }

    private View note(String text) {
        LinearLayout panel = panel();
        panel.setMinimumHeight(0);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = ui.text(16, p.textSecondary, ui.regular);
        t.setSingleLine(false);
        t.setMaxLines(3);
        t.setText(text);
        panel.addView(t);
        return panel;
    }

    private LinearLayout.LayoutParams weighted(float w, boolean gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, w);
        if (gap) lp.leftMargin = ui.m(18);
        return lp;
    }

    private LinearLayout.LayoutParams topMargin(float mockup) {
        LinearLayout.LayoutParams lp = NeonUi.wrap();
        lp.topMargin = ui.m(mockup);
        return lp;
    }

    private LinearLayout.LayoutParams matchWidth(float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = ui.m(top);
        return lp;
    }
}
