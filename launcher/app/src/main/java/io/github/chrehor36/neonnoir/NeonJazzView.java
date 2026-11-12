package io.github.chrehor36.neonnoir;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Jazz mode (the music-note button): a clock chip, "After hours", the turntable in a glass frame, the record from
 * your Discogs collection with Pick another, and a dock with Open Spotify and three jazz playlist cards that turn
 * every 15 seconds or with the arrow. Laid out from the Jazz board, in its dimmer brass-and-oxblood colors.
 */
final class NeonJazzView extends FrameLayout {
    private static final long ROTATE_MS = 15_000L;

    private final NeonUi ui;
    private final NeonPalette p;
    private final Runnable onExit;
    private TurntableView turntable;
    /** The Jazz mode (exit) button, top right; Pick another's Up target. */
    private View exitButton;
    private TextView recordTitle, recordArtist, recordSource, dayName, shortDate;
    private final List<LinearLayout> cards = new ArrayList<>();
    private final List<View> dots = new ArrayList<>();
    private final List<NeonSpotify.Playlist> playlists = NeonSpotify.playlists();
    /** With your own SPOTIFY_PLAYLISTS, the first is the main one, behind Open Spotify; the rest are the cards. */
    private final NeonSpotify.Playlist main = NeonConfig.spotifyItems().isEmpty() ? null : playlists.remove(0);
    private TextView mainName;
    private boolean namesLoaded;
    private int page;
    private NeonRecords.Record record;
    /** The day the record was set, so a new day brings that day's record. */
    private String recordDay;
    private View firstFocus;
    private final List<View> arrows = new ArrayList<>();

    private final Runnable rotate = new Runnable() {
        @Override
        public void run() {
            // Never turn the cards under the viewer's focus, nor while they page with the arrows.
            boolean onCard = false;
            for (View c : cards) onCard |= c.hasFocus();
            for (View a : arrows) onCard |= a.hasFocus();
            if (!onCard) showPage(page + 1);
            postDelayed(this, ROTATE_MS);
        }
    };

    NeonJazzView(Context ctx, NeonUi ui, Runnable onExit) {
        super(ctx);
        this.ui = ui;
        this.p = ui.palette;
        this.onExit = onExit;
        build();
    }

    private void build() {
        LinearLayout col = ui.column();
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(ui.m(64), ui.m(28), ui.m(64), ui.m(28));
        addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Top bar: clock chip on the left, Jazz mode (exit) on the right.
        LinearLayout top = ui.row();
        col.addView(top, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.m(54)));
        LinearLayout clockChip = ui.row();
        clockChip.setPadding(ui.m(8), 0, ui.m(20), 0);
        clockChip.setBackground(ui.glass(10, p.jazzGlassFill, p.jazzGlassEdge));
        NeonClockView mini = new NeonClockView(getContext(), p);
        mini.setWeight(2.2f);
        mini.setAlpha(0.78f);
        clockChip.addView(mini, new LinearLayout.LayoutParams(ui.m(42), ui.m(42)));
        dayName = ui.caps("", 16, p.jazzText, true, 0.24f);
        clockChip.addView(dayName, ui.wrap(12));
        shortDate = ui.caps("", 15, p.jazzAccent, false, 0.14f);
        clockChip.addView(shortDate, ui.wrap(12));
        top.addView(clockChip, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));
        top.addView(ui.spacer());
        LinearLayout exit = ui.row();
        exit.setPadding(ui.m(20), 0, ui.m(20), 0);
        exit.setContentDescription("Leave Jazz mode");
        ImageView note = new ImageView(getContext());
        note.setImageResource(R.drawable.nn_ic_jazz);
        note.setColorFilter(p.jazzAccent);
        exit.addView(note, new LinearLayout.LayoutParams(ui.m(18), ui.m(18)));
        TextView exitText = ui.caps("Jazz mode", 15, p.jazzAccent, true, 0.14f);
        exit.addView(exitText, ui.wrap(8));
        ui.glassButton(exit, 8, p.jazzGlassFill, NeonUi.withAlpha(p.jazzAccent, 102));
        exit.setOnClickListener(v -> onExit.run());
        exit.setId(View.generateViewId());
        exitButton = exit;
        top.addView(exit, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ui.m(48)));
        // Selected, the button grows and lifts a little: let it draw past the top bar instead of being cut off.
        top.setClipChildren(false);
        col.setClipChildren(false);
        col.setClipToPadding(false);

        // Center: After hours, the turntable, the record.
        LinearLayout center = ui.column();
        center.setGravity(Gravity.CENTER);
        center.setClipChildren(false);
        col.addView(center, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout kicker = ui.row();
        kicker.addView(rule());
        TextView after = ui.caps("After hours", 14, p.jazzLabel, false, 0.5f);
        after.setPadding(ui.m(18) + Math.round(ui.m(14) * 0.5f), 0, ui.m(18), 0);
        kicker.addView(after);
        kicker.addView(rule());
        center.addView(kicker, NeonUi.wrap());

        FrameLayout frame = new FrameLayout(getContext());
        frame.setPadding(ui.m(18), ui.m(18), ui.m(18), ui.m(18));
        frame.setBackground(ui.glass(18, p.jazzGlassFill, p.jazzGlassEdge));
        frame.setFocusable(true);
        frame.setContentDescription("Play this record on Spotify");
        frame.setClipChildren(false);
        turntable = new TurntableView(getContext(), ui.m(240));
        frame.addView(turntable, new FrameLayout.LayoutParams(ui.m(280), ui.m(240)));
        frame.setOnFocusChangeListener((v, f) -> {
            // Light mode: the selected red behind the record; dark mode: just the gold edge.
            v.setBackground(f ? (p.light ? ui.selectedFill(18) : ui.glass(18, p.jazzGlassFill, p.focusOutline))
                    : ui.glass(18, p.jazzGlassFill, p.jazzGlassEdge));
            ui.lift(v, f);
        });
        frame.setOnClickListener(v -> playRecord());
        LinearLayout.LayoutParams flp = NeonUi.wrap();
        flp.topMargin = ui.m(12);
        center.addView(frame, flp);

        recordTitle = ui.text(40, p.jazzText, ui.serif);
        recordTitle.setShadowLayer(1, -ui.m(1), 0, NeonUi.withAlpha(0xFFA01E24, 77));
        LinearLayout.LayoutParams tlp = NeonUi.wrap();
        tlp.topMargin = ui.m(16);
        center.addView(recordTitle, tlp);

        LinearLayout byline = ui.row();
        recordArtist = ui.caps("", 15, p.jazzAccent, true, 0.16f);
        byline.addView(recordArtist);
        recordSource = ui.caps("", 15, p.jazzLabel, false, 0.16f);
        byline.addView(recordSource);
        LinearLayout another = ui.row();
        another.setPadding(ui.m(16), 0, ui.m(16), 0);
        TextView anotherText = ui.caps("Pick another", 14, p.jazzText, true, 0.12f);
        another.addView(anotherText);
        ui.glassButton(another, 7, p.jazzGlassFill, NeonUi.withAlpha(p.jazzAccent, 102));
        another.setOnClickListener(v -> pickAnother());
        // Up from Pick another reaches the Jazz mode button: it sits far to the right, so Android's own search finds
        // nothing above. Down from Jazz mode comes back.
        another.setId(View.generateViewId());
        another.setNextFocusUpId(exitButton.getId());
        exitButton.setNextFocusDownId(another.getId());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ui.m(40));
        alp.leftMargin = ui.m(16);
        byline.addView(another, alp);
        LinearLayout.LayoutParams blp = NeonUi.wrap();
        blp.topMargin = ui.m(12);
        center.addView(byline, blp);

        // The dock: Open Spotify, a divider, three playlist cards, and the arrow with page dots.
        LinearLayout dock = ui.row();
        dock.setPadding(ui.m(18), ui.m(14), ui.m(18), ui.m(14));
        dock.setBackground(ui.glass(16, p.jazzGlassFill, p.jazzGlassEdge));
        dock.setClipChildren(false);
        dock.setClipToPadding(false);
        col.setClipChildren(false);
        col.addView(dock, NeonUi.wrap());

        LinearLayout spotify = ui.row();
        spotify.setPadding(ui.m(26), 0, ui.m(26), 0);
        ImageView sIcon = new ImageView(getContext());
        sIcon.setImageResource(R.drawable.nn_ic_jazz);
        sIcon.setColorFilter(p.jazzButtonText);
        spotify.addView(sIcon, new LinearLayout.LayoutParams(ui.m(22), ui.m(22)));
        LinearLayout sLabels = ui.column();
        TextView sText = ui.caps("Open Spotify", 16, p.jazzButtonText, true, 0.1f);
        sLabels.addView(sText);
        // Always room for the whole label, however short the playlist name under it.
        sText.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        sLabels.setMinimumWidth(sText.getMeasuredWidth() + ui.m(2));
        if (main != null) {
            // Your main playlist, under the label: Open Spotify goes straight to it.
            mainName = ui.caps(main.name, 12, NeonUi.withAlpha(p.jazzButtonText, 190), false, 0.12f);
            mainName.setMaxWidth(ui.m(220));
            LinearLayout.LayoutParams mlp = NeonUi.wrap();
            mlp.topMargin = ui.m(4);
            sLabels.addView(mainName, mlp);
        }
        spotify.addView(sLabels, ui.wrap(10));
        spotify.setFocusable(true);
        spotify.setBackground(button(false));
        spotify.setOnFocusChangeListener((v, f) -> {
            v.setBackground(button(f));
            ui.selectContentLight(v, f);
            ui.lift(v, f);
        });
        spotify.setOnClickListener(v -> {
            boolean ok = main != null ? NeonSpotify.openPlaylist(getContext(), main) : NeonSpotify.open(getContext());
            if (!ok) toast("Spotify isn't installed on this TV");
        });
        dock.addView(spotify, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ui.m(74)));
        firstFocus = spotify;

        View divider = new View(getContext());
        divider.setBackgroundColor(NeonUi.withAlpha(p.jazzText, 46));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ui.hair(), ui.m(52));
        dlp.leftMargin = ui.m(14);
        dlp.rightMargin = ui.m(14);
        dock.addView(divider, dlp);

        // The back arrow, mirroring the next arrow after the cards. A blank row under it, the height of the page
        // dots under the next arrow, keeps the two arrows level.
        LinearLayout backCol = ui.column();
        backCol.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout back = ui.row();
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Show previous playlists");
        TextView backChevron = ui.text(20, p.text, ui.bold);
        backChevron.setText("‹");
        back.addView(backChevron);
        ui.glassButton(back, 2, p.jazzCard, NeonUi.withAlpha(p.jazzAccent, 71));
        back.setOnClickListener(v -> showPage(page - 1));
        arrows.add(back);
        backCol.addView(back, new LinearLayout.LayoutParams(ui.m(40), ui.m(40)));
        LinearLayout.LayoutParams bsp = new LinearLayout.LayoutParams(ui.m(7), ui.m(7));
        bsp.topMargin = ui.m(8);
        backCol.addView(new View(getContext()), bsp);
        LinearLayout.LayoutParams bcp = NeonUi.wrap();
        bcp.rightMargin = ui.m(14);
        dock.addView(backCol, bcp);

        for (int i = 0; i < 3; i++) {
            LinearLayout card = ui.column();
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(ui.m(16), 0, ui.m(16), 0);
            TextView name = ui.caps("", 15, p.jazzCardText, true, 0.08f);
            TextView sub = ui.caps("", 13, p.jazzAccentDeep, false, 0.12f);
            card.addView(name);
            LinearLayout.LayoutParams slp = NeonUi.wrap();
            slp.topMargin = ui.m(6);
            card.addView(sub, slp);
            sub.setVisibility(GONE); // Just the name; the dots show where you are.
            ui.glassButton(card, 3, p.jazzCard, NeonUi.withAlpha(p.jazzAccent, 36));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ui.m(200), ui.m(74));
            clp.rightMargin = ui.m(14);
            dock.addView(card, clp);
            cards.add(card);
        }

        LinearLayout arrowCol = ui.column();
        arrowCol.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout arrow = ui.row();
        arrow.setGravity(Gravity.CENTER);
        arrow.setContentDescription("Show next playlists");
        TextView chevron = ui.text(20, p.text, ui.bold);
        chevron.setText("›");
        arrow.addView(chevron);
        ui.glassButton(arrow, 2, p.jazzCard, NeonUi.withAlpha(p.jazzAccent, 71));
        arrow.setOnClickListener(v -> showPage(page + 1));
        arrows.add(arrow);
        arrowCol.addView(arrow, new LinearLayout.LayoutParams(ui.m(40), ui.m(40)));
        LinearLayout dotRow = ui.row();
        int pages = Math.max(1, (playlists.size() + 2) / 3);
        for (int i = 0; i < pages; i++) {
            View d = new View(getContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.m(7), ui.m(7));
            lp.leftMargin = i == 0 ? 0 : ui.m(4);
            dotRow.addView(d, lp);
            dots.add(d);
        }
        LinearLayout.LayoutParams drp = NeonUi.wrap();
        drp.topMargin = ui.m(8);
        arrowCol.addView(dotRow, drp);
        dock.addView(arrowCol, NeonUi.wrap());
        showPage(0);
    }

    private View rule() {
        View v = new View(getContext());
        v.setBackgroundColor(NeonUi.withAlpha(p.jazzAccent, 89));
        v.setLayoutParams(new LinearLayout.LayoutParams(ui.m(60), ui.hair()));
        return v;
    }

    private GradientDrawable button(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(ui.m(3));
        g.setColor(focused ? p.tileSelected : p.jazzButton);
        g.setStroke(Math.max(1, ui.m(focused ? 1.5f : 1)), focused ? p.focusOutline : p.jazzAccentDeep);
        return g;
    }

    private void showPage(int next) {
        int pages = Math.max(1, (playlists.size() + 2) / 3);
        page = ((next % pages) + pages) % pages;
        for (int k = 0; k < 3; k++) {
            LinearLayout card = cards.get(k);
            int n = (page * 3 + k) % Math.max(1, playlists.size());
            if (playlists.isEmpty()) {
                card.setVisibility(INVISIBLE);
                continue;
            }
            NeonSpotify.Playlist pl = playlists.get(n);
            ((TextView) card.getChildAt(0)).setText(pl.name.toUpperCase(Locale.getDefault()));
            card.setContentDescription(pl.name);
            card.setOnClickListener(v -> {
                if (!NeonSpotify.openPlaylist(getContext(), pl)) toast("Spotify isn't installed on this TV");
            });
        }
        for (int i = 0; i < dots.size(); i++) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(i == page ? p.jazzAccentDeep : NeonUi.withAlpha(p.jazzAccent, 51));
            dots.get(i).setBackground(g);
        }
    }

    /** Jazz mode opens: today's record spins for 20 s. */
    void enter() {
        Date now = new Date();
        dayName.setText(new SimpleDateFormat("EEEE", Locale.getDefault()).format(now).toUpperCase(Locale.getDefault()));
        shortDate.setText(new SimpleDateFormat("MMM d", Locale.getDefault()).format(now).toUpperCase(Locale.getDefault()));
        // The record stays put between visits, like the one on the turntable; only Pick another (or a new day)
        // changes it. Tonight's record is the one shared with Home's chip.
        String today = java.time.LocalDate.now().toString();
        if (record == null || !today.equals(recordDay) || !NeonRecords.isTonight(getContext(), record)) {
            recordDay = today;
            Context app = getContext().getApplicationContext();
            NeonNet.load(() -> NeonRecords.tonight(app), r -> {
                if (r != null) setRecord(r);
            });
        } else {
            turntable.spinFor20s();
        }
        // A fresh order of the cards every time Jazz mode opens (the main playlist stays on Open Spotify).
        java.util.Collections.shuffle(playlists);
        showPage(0);
        if (!namesLoaded && main != null) {
            namesLoaded = true;
            Context app = getContext().getApplicationContext();
            List<NeonSpotify.Playlist> all = new ArrayList<>(playlists);
            all.add(0, main);
            NeonNet.load(() -> NeonSpotify.loadNames(app, all), changed -> {
                if (changed) {
                    mainName.setText(main.name.toUpperCase(Locale.getDefault()));
                    showPage(page);
                }
            });
        }
        removeCallbacks(rotate);
        postDelayed(rotate, ROTATE_MS);
        post(() -> firstFocus.requestFocus());
    }

    void leave() {
        removeCallbacks(rotate);
        turntable.stopSpinning();
    }

    private void pickAnother() {
        Context app = getContext().getApplicationContext();
        NeonRecords.Record current = record;
        NeonNet.load(() -> NeonRecords.another(app, current), r -> {
            if (r == null) return;
            NeonRecords.choose(app, r);   // Home's Tonight chip shows it too
            setRecord(r);
        });
    }

    private void setRecord(NeonRecords.Record r) {
        record = r;
        recordTitle.setText(r.title);
        recordArtist.setText(r.byline().toUpperCase(Locale.getDefault()));
        recordSource.setText(r.fromDiscogs ? "  ·  From your Discogs".toUpperCase(Locale.getDefault())
                : "  ·  From the house crate".toUpperCase(Locale.getDefault()));
        turntable.spinFor20s();
    }

    private void playRecord() {
        if (record == null) return;
        if (!NeonSpotify.search(getContext(), record.title + " " + record.artist)) toast("Spotify isn't installed on this TV");
    }

    private void toast(String s) {
        Toast.makeText(getContext(), s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDetachedFromWindow() {
        leave();
        super.onDetachedFromWindow();
    }
}
