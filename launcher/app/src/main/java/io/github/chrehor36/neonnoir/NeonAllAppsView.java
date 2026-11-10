package io.github.chrehor36.neonnoir;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * All apps: a 6-column grid of every TV app, A–Z or most used, with a Hold OK menu (Add to favorites, App info,
 * Uninstall). Laid out from the All apps board.
 */
final class NeonAllAppsView extends FrameLayout {
    private static final int COLUMNS = 6;

    private final NeonUi ui;
    private final NeonPalette p;
    private final Runnable onBack;
    private final Runnable onDockChanged;
    private TextView count;
    private GridLayout grid;
    private ScrollView scroll;
    private LinearLayout sortAz, sortUsed, menu;
    private View homeButton;
    private boolean byUse;
    private int generation;
    private String menuPkg;
    private View menuReturnFocus;

    NeonAllAppsView(Context ctx, NeonUi ui, Runnable onBack, Runnable onDockChanged) {
        super(ctx);
        this.ui = ui;
        this.p = ui.palette;
        this.onBack = onBack;
        this.onDockChanged = onDockChanged;
        byUse = prefs().getBoolean("by_use", false);
        build();
    }

    private void build() {
        LinearLayout col = ui.column();
        col.setPadding(ui.m(64), ui.m(34), ui.m(64), ui.m(26));
        addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout head = ui.row();
        LinearLayout home = ui.row();
        home.setPadding(ui.m(14), 0, ui.m(20), 0);
        TextView chevron = ui.text(20, p.text, ui.bold);
        chevron.setText("‹");
        home.addView(chevron);
        TextView homeText = ui.caps("Home", 15, p.text, true, 0.14f);
        home.addView(homeText, ui.wrap(8));
        ui.glassButton(home, 10, p.glassFill, p.glassEdge);
        home.setOnClickListener(v -> onBack.run());
        homeButton = home;
        head.addView(home, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ui.m(50)));
        head.addView(ui.edgeTitle("All apps", 38, 0.32f), ui.wrap(22));
        count = ui.caps("", 15, p.label, false, 0.14f);
        head.addView(count, ui.wrap(22));
        head.addView(ui.spacer());

        LinearLayout sorts = ui.row();
        sorts.setPadding(ui.m(5), ui.m(5), ui.m(5), ui.m(5));
        sorts.setBackground(ui.glass(8));
        sortAz = sortChip("A–Z", false);
        sortUsed = sortChip("Most used", true);
        sorts.addView(sortAz, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ui.m(40)));
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ui.m(40));
        ulp.leftMargin = ui.m(6);
        sorts.addView(sortUsed, ulp);
        head.addView(sorts);
        // Selected, the sort chips and the Home button grow and lift a little: let them draw past their boxes.
        sorts.setClipChildren(false);
        sorts.setClipToPadding(false);
        head.setClipChildren(false);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.addView(head, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.m(54)));

        scroll = new ScrollView(getContext());
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        grid = new GridLayout(getContext());
        grid.setColumnCount(COLUMNS);
        // Room on every side for the focused tile's lift and outline.
        grid.setPadding(ui.m(8), ui.m(12), ui.m(8), ui.m(12));
        grid.setClipChildren(false);
        grid.setClipToPadding(false);   // a selected tile grows into the padding instead of being cut off
        scroll.addView(grid, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        slp.topMargin = ui.m(14);
        col.addView(scroll, slp);

        LinearLayout foot = ui.row();
        TextView hold = ui.caps("Hold OK", 15, p.gold, true, 0.14f);
        hold.setPadding(ui.m(14), ui.m(6), ui.m(14), ui.m(6));
        hold.setBackground(ui.glass(2, 0, NeonUi.withAlpha(p.gold, 115)));
        foot.addView(hold);
        foot.addView(ui.caps("on any app for options: Add to favorites · App info · Uninstall", 15, p.label, false, 0.14f), ui.wrap(14));
        LinearLayout.LayoutParams flp = NeonUi.wrap();
        flp.topMargin = ui.m(14);
        col.addView(foot, flp);

        buildMenu();
        styleSorts();
    }

    private LinearLayout sortChip(String label, boolean use) {
        LinearLayout c = ui.row();
        c.setPadding(ui.m(18), 0, ui.m(18), 0);
        c.setFocusable(true);
        c.addView(ui.caps(label, 14, p.label, true, 0.14f));
        c.setOnClickListener(v -> {
            if (byUse == use) return;
            byUse = use;
            prefs().edit().putBoolean("by_use", use).apply();
            styleSorts();
            reload(false);
        });
        c.setOnFocusChangeListener((v, f) -> {
            styleSorts();
            ui.lift(v, f);
        });
        return c;
    }

    private void styleSorts() {
        for (LinearLayout c : new LinearLayout[]{sortAz, sortUsed}) {
            boolean on = (c == sortUsed) == byUse;
            boolean focused = c.hasFocus();
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(ui.m(2));
            bg.setColor(on || focused ? p.tileSelected : 0);
            if (focused) bg.setStroke(ui.hair(), p.focusOutline);
            c.setBackground(bg);
            ((TextView) c.getChildAt(0)).setTextColor(on || focused ? p.tileSelectedText : p.label);
        }
    }

    void enter() {
        reload(true);
    }

    /** Back from Uninstall or App info, or an app was added or removed: reloaded, near the same place. */
    void resume() {
        if (menuOpen()) closeMenu();
        reload(false);
    }

    private void reload(boolean focusFirst) {
        int gen = ++generation;
        Context app = getContext().getApplicationContext();
        boolean use = byUse;
        NeonNet.load(() -> NeonRecents.all(app, use), apps -> {
            if (gen != generation || apps == null) return;
            show(apps, focusFirst);
        });
    }

    private void show(List<NeonRecents.App> apps, boolean focusFirst) {
        // The focused tile's place, so a reload (an app uninstalled) keeps the focus where it was.
        View had = grid.findFocus();
        int at = -1;
        for (int i = 0; had != null && i < grid.getChildCount(); i++) {
            if (grid.getChildAt(i) == had) at = i;
        }
        grid.removeAllViews();
        count.setText((apps.size() + " installed").toUpperCase(Locale.getDefault()));
        int gap = ui.m(20);
        int width = (getResources().getDisplayMetrics().widthPixels - ui.m(128 + 16) - gap * (COLUMNS - 1)) / COLUMNS;
        int height = Math.round(width * 9f / 16f);
        for (int i = 0; i < apps.size(); i++) {
            NeonRecents.App app = apps.get(i);
            FrameLayout tile = ui.tile(v -> {
                if (!NeonRecents.launch(getContext(), app.pkg)) toast("That app can't be opened");
            });
            tile.setContentDescription(app.label);
            tile.setLongClickable(true);
            tile.setOnLongClickListener(v -> {
                openMenu(app, v);
                return true;
            });
            if (app.banner != null) {
                ImageView banner = new ImageView(getContext());
                banner.setImageDrawable(app.banner);
                banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
                tile.addView(banner, 0, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            } else {
                LinearLayout inner = ui.column();
                inner.setGravity(Gravity.CENTER);
                ImageView icon = new ImageView(getContext());
                icon.setImageDrawable(app.icon);
                inner.addView(icon, new LinearLayout.LayoutParams(ui.m(30), ui.m(30)));
                TextView name = ui.caps(app.label.toString(), 13, p.tileText, true, 0.08f);
                name.setPadding(ui.m(6), ui.m(4), ui.m(6), 0);
                inner.addView(name);
                tile.addView(inner, 0, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            }
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = width;
            lp.height = height;
            lp.leftMargin = i % COLUMNS == 0 ? 0 : gap;
            lp.topMargin = i < COLUMNS ? 0 : gap;
            grid.addView(tile, lp);
        }
        if (!focusFirst && at >= 0 && grid.getChildCount() > 0) {
            grid.getChildAt(Math.min(at, grid.getChildCount() - 1)).requestFocus();
        } else if (focusFirst || !hasFocus()) {
            scroll.scrollTo(0, 0);
            post(() -> {
                if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
                else homeButton.requestFocus();
            });
        }
    }

    // ---- Hold OK menu -------------------------------------------------------------------------------------

    private void buildMenu() {
        menu = ui.column();
        menu.setPadding(ui.m(22), ui.m(18), ui.m(22), ui.m(18));
        menu.setBackground(new NeonCutDrawable(NeonUi.withAlpha(p.background, 235), p.glassEdge, ui.m(14), ui.hair()));
        menu.setVisibility(GONE);
        menu.setElevation(ui.m(20));
        menu.setClipChildren(false);   // the selected row grows a little; don't cut it at the padding
        menu.setClipToPadding(false);
        addView(menu, new LayoutParams(ui.m(340), LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    private void openMenu(NeonRecents.App app, View from) {
        menuPkg = app.pkg;
        menuReturnFocus = from;
        menu.removeAllViews();
        menu.addView(ui.caps(app.label.toString(), 17, p.text, true, 0.08f));
        boolean favorite = NeonRecents.isFavorite(getContext(), app.pkg);
        View first = menuItem(favorite ? "Remove from favorites" : "Add to favorites", () -> {
            if (favorite) {
                NeonRecents.removeFavorite(getContext(), app.pkg);
                toast(app.label + " is off the dock");
            } else if (NeonRecents.addFavorite(getContext(), app.pkg)) {
                toast(app.label + " is in the dock. Hold OK on it there to move it.");
            } else {
                toast("The dock holds " + NeonRecents.MAX_FAVORITES + " favorites. Remove one first.");
                return;
            }
            onDockChanged.run();
        });
        menuItem("App info", () -> start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg))));
        menuItem("Uninstall", () -> start(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg))));
        menu.setVisibility(VISIBLE);
        grid.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        first.requestFocus();
    }

    private View menuItem(String label, Runnable action) {
        LinearLayout item = ui.row();
        item.setPadding(ui.m(16), 0, ui.m(16), 0);
        TextView t = ui.caps(label, 15, p.text, true, 0.1f);
        item.addView(t);
        ui.glassButton(item, 6, p.glassFill, p.glassEdge);
        item.setOnClickListener(v -> {
            closeMenu();
            action.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.m(46));
        lp.topMargin = ui.m(10);
        menu.addView(item, lp);
        return item;
    }

    boolean menuOpen() {
        return menu.getVisibility() == VISIBLE;
    }

    void closeMenu() {
        menu.setVisibility(GONE);
        grid.setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        if (menuReturnFocus != null && menuReturnFocus.isAttachedToWindow()) menuReturnFocus.requestFocus();
        menuPkg = null;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (menuOpen() && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) closeMenu();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void start(Intent i) {
        try {
            getContext().startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            toast("This TV doesn't offer that");
        }
    }

    private void toast(String s) {
        Toast.makeText(getContext(), s, Toast.LENGTH_SHORT).show();
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences("neon_home", Context.MODE_PRIVATE);
    }
}
