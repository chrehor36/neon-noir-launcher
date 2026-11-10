package io.github.chrehor36.neonnoir;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The live buttons, in More's Live section and on the full guide: Antenna (the TV's Live TV app), the HDMI
 * inputs (off: SHOW_INPUTS), and the installed apps with live guides of their own.
 */
final class NeonWatch {
    private static final boolean SHOW_INPUTS = false;

    private NeonWatch() {
    }

    /** Fills the row with the buttons; returns how many there are. */
    static int fill(Activity activity, NeonUi ui, LinearLayout row) {
        View f = row.findFocus();
        String focused = f == null ? null : String.valueOf(f.getContentDescription());
        row.removeAllViews();
        MainActivity main = activity instanceof MainActivity ? (MainActivity) activity : null;

        Intent liveTv = main == null ? null : main.liveTvAppIntent();
        if (liveTv != null) {
            add(ui, row, "Antenna", "Live TV", "Antenna channels in the Live TV app", null, v -> {
                try {
                    activity.startActivity(liveTv.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (RuntimeException e) {
                    Toast.makeText(activity, "Live TV isn't available", Toast.LENGTH_SHORT).show();
                }
            });
        }
        // The HDMI inputs are left out on the owner's TV (2026-11-09: "don't need HDMI inputs"); the remote's Input
        // button switches them. Set SHOW_INPUTS to bring the buttons back.
        if (SHOW_INPUTS) for (Input in : inputs(activity)) {
            add(ui, row, in.label, in.detail, in.label + (in.connected ? ", connected" : ""),
                    in.connected ? ui.palette.liveDot : null, v -> {
                        if (main != null) main.openInputFromHome(in.id);
                    });
        }
        for (String[] app : NeonLive.LIVE_APPS) {
            String pkg = NeonLive.installed(activity, app);
            if (pkg == null) continue;
            add(ui, row, app[0], "Live guide", app[0] + " live guide", null, v -> {
                if (!NeonRecents.launch(activity, pkg)) {
                    Toast.makeText(activity, "Couldn't open " + app[0], Toast.LENGTH_SHORT).show();
                }
            });
        }
        int count = row.getChildCount();
        if (focused != null) {
            for (int i = 0; i < row.getChildCount(); i++) {
                View c = row.getChildAt(i);
                if (focused.equals(String.valueOf(c.getContentDescription()))) c.requestFocus();
            }
        }
        return count;
    }

    private static void add(NeonUi ui, LinearLayout row, String title, String detail, String description, Integer dot,
                            View.OnClickListener click) {
        NeonPalette p = ui.palette;
        Context ctx = row.getContext();
        LinearLayout b = ui.row();
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(ui.m(18), 0, ui.m(20), 0);
        b.setContentDescription(description);
        if (dot != null) {
            View d = new View(ctx);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(dot);
            d.setBackground(g);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ui.m(8), ui.m(8));
            dl.rightMargin = ui.m(9);
            b.addView(d, dl);
        }
        b.addView(ui.caps(title, 15, p.text, true, 0.08f));
        if (detail != null && !detail.isEmpty()) b.addView(ui.caps(detail, 12, p.label, false, 0.1f), ui.wrap(10));
        ui.glassButton(b, 8, p.glassFill, p.glassEdge);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ui.m(44));
        if (row.getChildCount() > 0) lp.leftMargin = ui.m(12);
        row.addView(b, lp);
    }

    private static final class Input {
        String id, label, detail;
        boolean connected;
    }

    /** HDMI (and AV) inputs in port order. A device that names itself over HDMI-CEC ("Xbox") takes its port's place. */
    private static List<Input> inputs(Context ctx) {
        List<Input> list = new ArrayList<>();
        TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return list;
        List<TvInputInfo> all;
        try {
            all = new ArrayList<>(tim.getTvInputList());
        } catch (RuntimeException e) {
            return list;
        }
        Set<String> ids = new HashSet<>();
        for (TvInputInfo i : all) ids.add(i.getId());
        Set<String> portsWithDevice = new HashSet<>();
        for (TvInputInfo i : all) {
            if (i.getParentId() != null && ids.contains(i.getParentId()) && i.isPassthroughInput()) portsWithDevice.add(i.getParentId());
        }
        Collections.sort(all, (a, b) -> {
            boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
            if (ha != hb) return ha ? -1 : 1;
            return String.valueOf(a.loadLabel(ctx)).compareToIgnoreCase(String.valueOf(b.loadLabel(ctx)));
        });
        for (TvInputInfo info : all) {
            if (!info.isPassthroughInput() || info.isHidden(ctx)) continue;
            if (portsWithDevice.contains(info.getId())) continue;
            Input in = new Input();
            in.id = info.getId();
            boolean device = info.getParentId() != null && ids.contains(info.getParentId());
            TvInputInfo port = device ? tim.getTvInputInfo(info.getParentId()) : info;
            String portLabel = String.valueOf(port == null ? info.loadLabel(ctx) : port.loadLabel(ctx));
            if (info.getType() == TvInputInfo.TYPE_HDMI && !portLabel.toUpperCase(Locale.ROOT).contains("HDMI")) {
                portLabel = "HDMI " + portLabel;
            }
            CharSequence custom = InputNames.tidy(info.loadCustomLabel(ctx));
            String name = custom != null && custom.length() > 0 && !custom.toString().equals(portLabel) ? custom.toString()
                    : device ? String.valueOf(info.loadLabel(ctx)) : null;
            in.label = name != null ? name : portLabel;
            in.detail = name != null ? portLabel : "";
            in.connected = info.getType() == TvInputInfo.TYPE_HDMI
                    && tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED;
            list.add(in);
        }
        return list;
    }
}
