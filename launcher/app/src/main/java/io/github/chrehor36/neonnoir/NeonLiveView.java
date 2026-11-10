package io.github.chrehor36.neonnoir;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The full Live guide (More → Live → Full guide): every channel in the TV's channel list with what's on now and
 * next, grouped by source. OK tunes it. With More's Live section, this replaces Google TV's Live tab.
 */
final class NeonLiveView extends FrameLayout {
    private final NeonUi ui;
    private final NeonPalette p;
    private final Activity activity;
    private final Runnable onBack;
    private LinearLayout guide;
    private ScrollView guideScroll;
    private View backLink;
    private int loadGeneration;
    private boolean asked;

    NeonLiveView(Activity activity, NeonUi ui, Runnable onBack) {
        super(activity);
        this.activity = activity;
        this.ui = ui;
        this.p = ui.palette;
        this.onBack = onBack;
        build();
    }

    private void build() {
        LinearLayout col = ui.column();
        col.setPadding(ui.m(64), ui.m(26), ui.m(64), ui.m(26));
        // Clipped, so the scrolling guide stays inside its own area instead of drawing over the headings.
        col.setClipChildren(true);
        col.setClipToPadding(false);
        addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout back = ui.row();
        back.setPadding(ui.m(14), ui.m(6), ui.m(14), ui.m(6));
        TextView up = ui.text(18, p.label, ui.bold);
        up.setText("⌃");
        back.addView(up);
        back.addView(ui.caps("Back to More", 15, p.label, false, 0.14f), ui.wrap(8));
        ui.linkButton(back);
        back.setOnClickListener(v -> onBack.run());
        LinearLayout.LayoutParams blp = NeonUi.wrap();
        blp.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(back, blp);
        backLink = back;

        col.addView(ui.edgeTitle("Live guide", 26, 0.32f), topMargin(14));
        guideScroll = new ScrollView(getContext());
        guideScroll.setVerticalScrollBarEnabled(false);
        guideScroll.setClipToPadding(false);
        guideScroll.setPadding(ui.m(4), ui.m(6), ui.m(4), ui.m(6));
        guide = ui.column();
        guide.setClipChildren(false);
        guideScroll.addView(guide);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        glp.topMargin = ui.m(6);
        glp.leftMargin = -ui.m(4);
        glp.rightMargin = -ui.m(4);
        col.addView(guideScroll, glp);
    }

    /** Shown: the guide (read in the background), with the focus on the first channel. */
    void enter() {
        reload(true);
    }

    /** Back from a channel: refreshed, with the focus still on the channel that was opened. */
    void resume() {
        reload(false);
    }

    void leave() {
        loadGeneration++;
    }

    /** Once a minute while shown: what's on moves along; the focused channel keeps the focus. */
    void tick() {
        reload(false);
    }

    private void reload(boolean focusFirst) {
        int gen = ++loadGeneration;
        Context app = getContext().getApplicationContext();
        if (!NeonLive.canReadAll(app) && !asked) {
            // Asked once per visit; the system dialog is answered with the remote. Its answer comes back through
            // onResume, which calls enter() again.
            asked = true;
            activity.requestPermissions(new String[]{NeonLive.PERMISSION}, 41);
        }
        if (guide.getChildCount() == 0) showNote("Loading channels…", null);
        NeonNet.load(() -> NeonLive.channels(app), channels -> {
            if (gen != loadGeneration) return;
            showGuide(channels, focusFirst);
        });
        if (focusFirst) post(this::focusFirst);
    }

    private void focusFirst() {
        View first = firstFocusable(guide);
        (first != null ? first : backLink).requestFocus();
    }

    private static View firstFocusable(LinearLayout row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            if (row.getChildAt(i).isFocusable()) return row.getChildAt(i);
        }
        return null;
    }

    // ---- On now: the guide ---------------------------------------------------------------------------------

    private void showGuide(List<NeonLive.Channel> channels, boolean focusFirst) {
        if (channels == null || channels.isEmpty()) {
            boolean all = NeonLive.canReadAll(getContext());
            boolean antenna = activity instanceof MainActivity && ((MainActivity) activity).liveTvAppIntent() != null;
            showNote(all
                            ? antenna
                                    ? "No channels yet. Open Antenna in More and scan for channels, or open a live app once so it adds its channels."
                                    : "No channels yet. Open a live app once so it adds its channels here."
                            : "The guide needs permission to read the TV's channels.",
                    all ? null : "Allow");
            return;
        }
        View f = guide.findFocus();
        Object focusedId = f == null ? null : f.getTag();
        boolean hadFocus = guide.hasFocus();
        guide.removeAllViews();
        int logoH = ui.m(26);
        Context app = getContext().getApplicationContext();
        int gen = loadGeneration;
        View refocus = null;
        String lastSource = null;
        for (NeonLive.Channel ch : channels) {
            if (!ch.source.equals(lastSource) && !ch.source.isEmpty()) {
                // A quiet label where each source starts: "ANTENNA", "PLUTO TV".
                TextView src = ui.caps(ch.source, 12, p.gold, true, 0.16f);
                src.setPadding(ui.m(4), guide.getChildCount() == 0 ? 0 : ui.m(10), 0, ui.m(6));
                guide.addView(src);
            }
            lastSource = ch.source;
            LinearLayout row = channelRow(ch);
            guide.addView(row, rowLp());
            if (focusedId != null && focusedId.equals(ch.id)) refocus = row;
            ImageView logo = row.findViewWithTag("logo");
            NeonNet.load(() -> NeonLive.logo(app, ch.id, logoH), (Bitmap b) -> {
                if (b != null && gen == loadGeneration) {
                    logo.setImageBitmap(b);
                    logo.setVisibility(VISIBLE);
                }
            });
        }
        if (refocus != null && hadFocus) refocus.requestFocus();
        else if (focusFirst || backLink.hasFocus() || !hasFocus()) focusFirst();
    }

    /** "5.1 · [logo] NBC · The Evening News ▬▬▬── until 7:00 PM · Next 7:00 PM Game Night". */
    private LinearLayout channelRow(NeonLive.Channel ch) {
        LinearLayout row = ui.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.m(18), ui.m(8), ui.m(20), ui.m(8));
        row.setTag(ch.id);
        String nowTitle = ch.now == null ? "" : ch.now.title;
        row.setContentDescription(ch.number + " " + ch.name + (nowTitle.isEmpty() ? "" : ", " + nowTitle));

        TextView num = ui.text(16, p.gold, ui.bold);
        num.setText(ch.number);
        num.setSingleLine(true);
        row.addView(num, new LinearLayout.LayoutParams(ui.m(64), LinearLayout.LayoutParams.WRAP_CONTENT));

        FrameLayout logoBox = new FrameLayout(getContext());
        ImageView logo = new ImageView(getContext());
        logo.setTag("logo");
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setVisibility(INVISIBLE);
        logoBox.addView(logo, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ui.m(52), ui.m(26));
        llp.rightMargin = ui.m(14);
        row.addView(logoBox, llp);

        TextView name = ui.text(16, p.text, ui.bold);
        name.setText(ch.name);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(ui.m(190), LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout now = ui.column();
        TextView title = ui.text(16, p.text, ui.regular);
        title.setText(nowTitle.isEmpty() ? "No guide information" : nowTitle);
        if (nowTitle.isEmpty()) title.setTextColor(p.label);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        now.addView(title);
        if (ch.now != null) {
            LinearLayout bar = ui.row();
            bar.setGravity(Gravity.CENTER_VERTICAL);
            long t = System.currentTimeMillis();
            float done = Math.max(0f, Math.min(1f, (t - ch.now.start) / (float) Math.max(1, ch.now.end - ch.now.start)));
            LinearLayout track = ui.row();
            View fill = new View(getContext());
            fill.setBackgroundColor(p.gold);
            View rest = new View(getContext());
            rest.setBackgroundColor(NeonUi.withAlpha(p.label, 60));
            track.addView(fill, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, Math.max(0.001f, done)));
            track.addView(rest, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, Math.max(0.001f, 1f - done)));
            bar.addView(track, new LinearLayout.LayoutParams(ui.m(120), Math.max(2, ui.m(2.5f))));
            bar.addView(ui.caps("Until " + NeonLive.time(ch.now.end), 11, p.label, false, 0.1f), ui.wrap(10));
            LinearLayout.LayoutParams barLp = NeonUi.wrap();
            barLp.topMargin = ui.m(5);
            now.addView(bar, barLp);
        }
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nlp.rightMargin = ui.m(16);
        row.addView(now, nlp);

        LinearLayout next = ui.column();
        if (ch.next != null) {
            next.addView(ui.caps("Next · " + NeonLive.time(ch.next.start), 11, p.label, false, 0.12f));
            TextView nt = ui.text(14, p.textSecondary, ui.regular);
            nt.setText(ch.next.title);
            nt.setSingleLine(true);
            nt.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams ntl = NeonUi.wrap();
            ntl.topMargin = ui.m(3);
            next.addView(nt, ntl);
        }
        row.addView(next, new LinearLayout.LayoutParams(ui.m(260), LinearLayout.LayoutParams.WRAP_CONTENT));

        // The selected look without the lift: a full-width row that grew would be cut off at the sides.
        row.setFocusable(true);
        row.setBackground(ui.glass(8, p.glassFill, p.glassEdge));
        row.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? ui.selectedFill(8) : ui.glass(8, p.glassFill, p.glassEdge));
            ui.selectContent(v, has, p.tileSelectedText, p.light);
            if (has) guideScroll.post(() -> scrollIntoView(v));
        });
        row.setOnClickListener(v -> {
            if (activity instanceof MainActivity) ((MainActivity) activity).tuneChannel(ch.id);
        });
        return row;
    }

    /** Keeps the focused row a little away from the edges, so the rows around it stay in sight. */
    private void scrollIntoView(View v) {
        int pad = ui.m(60);
        int top = v.getTop() - pad, bottom = v.getBottom() + pad;
        int y = guideScroll.getScrollY(), h = guideScroll.getHeight();
        if (top < y) guideScroll.smoothScrollTo(0, Math.max(0, top));
        else if (bottom > y + h) guideScroll.smoothScrollTo(0, bottom - h);
    }

    private void showNote(String text, String button) {
        guide.removeAllViews();
        LinearLayout panel = ui.column();
        panel.setPadding(ui.m(22), ui.m(18), ui.m(22), ui.m(18));
        panel.setBackground(ui.glass(14));
        TextView t = ui.text(16, p.textSecondary, ui.regular);
        t.setSingleLine(false);
        t.setMaxLines(3);
        t.setText(text);
        panel.addView(t);
        guide.addView(panel, rowLp());
        if (button != null) {
            LinearLayout b = ui.row();
            b.setGravity(Gravity.CENTER);
            b.setPadding(ui.m(22), 0, ui.m(22), 0);
            b.setContentDescription(button);
            b.addView(ui.caps(button, 14, p.text, true, 0.12f));
            ui.glassButton(b, 8, p.glassFill, p.glassEdge);
            b.setOnClickListener(v -> activity.requestPermissions(new String[]{NeonLive.PERMISSION}, 41));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ui.m(42));
            lp.topMargin = ui.m(12);
            guide.addView(b, lp);
        }
    }

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = ui.m(8);
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
