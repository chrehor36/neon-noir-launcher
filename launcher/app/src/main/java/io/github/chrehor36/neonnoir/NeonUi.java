package io.github.chrehor36.neonnoir;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * The Neon Noir building blocks shared by every screen: sizes in mockup pixels (1280x720 boards), the fonts,
 * smoked-glass panels with cut corners, and the focus look (crimson, gold outline, lifted 3px, scaled to 1.04).
 */
final class NeonUi {
    final Context ctx;
    final float unit;
    NeonPalette palette;
    Typeface regular = Typeface.DEFAULT, bold = Typeface.DEFAULT_BOLD, serif = Typeface.SERIF;

    NeonUi(Context ctx, NeonPalette palette) {
        this.ctx = ctx;
        this.palette = palette;
        this.unit = ctx.getResources().getDisplayMetrics().widthPixels / 1280f;
        try {
            regular = ctx.getResources().getFont(R.font.nn_arimo_regular);
            bold = ctx.getResources().getFont(R.font.nn_arimo_bold);
            serif = ctx.getResources().getFont(R.font.nn_fraunces_semibold);
        } catch (RuntimeException ignored) {
            // A missing font falls back to the system's; the layout still works.
        }
    }

    int m(float mockupPx) {
        return Math.round(mockupPx * unit);
    }

    int hair() {
        return Math.max(1, m(1));
    }

    TextView text(float size, int color, Typeface face) {
        TextView t = new TextView(ctx);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, m(size));
        t.setTextColor(color);
        t.setTypeface(face);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    /** Uppercase label with letter spacing, the UI's usual voice. */
    TextView caps(String s, float size, int color, boolean strong, float spacing) {
        TextView t = text(size, color, strong ? bold : regular);
        t.setText(s.toUpperCase(Locale.getDefault()));
        t.setLetterSpacing(spacing);
        return t;
    }

    /** Smoked glass with two cut corners and a hairline gold edge. */
    NeonCutDrawable glass(float cut) {
        return new NeonCutDrawable(palette.glassFill, palette.glassEdge, m(cut), hair());
    }

    NeonCutDrawable glass(float cut, int fill, int edge) {
        return new NeonCutDrawable(fill, edge, m(cut), hair());
    }

    /** A horizontal row, vertically centered. */
    LinearLayout row() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    LinearLayout column() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    LinearLayout.LayoutParams wrap(float leftMargin) {
        LinearLayout.LayoutParams lp = wrap();
        lp.leftMargin = m(leftMargin);
        return lp;
    }

    View spacer() {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return v;
    }

    /** Focus lift: up 3px and to 1.04, as in the mockups. */
    void lift(View v, boolean focused) {
        v.animate().cancel();
        v.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f)
                .translationY(focused ? -m(3) : 0).setDuration(120).start();
    }

    /**
     * Tile look: plain tile, or crimson with a gold outline and the blue bar on the left when focused. The outline
     * is a foreground so a full-bleed banner or poster never hides it.
     */
    void styleTile(FrameLayout tile, View bar, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(m(3));
        bg.setColor(focused ? palette.tileSelected : palette.tile);
        GradientDrawable edge = new GradientDrawable();
        edge.setCornerRadius(m(3));
        edge.setColor(Color.TRANSPARENT);
        edge.setStroke(Math.max(1, m(focused ? 1.5f : 1)), focused ? palette.focusOutline : withAlpha(palette.focusOutline, 46));
        tile.setForeground(edge);
        tile.setBackground(bg);
        if (bar != null) bar.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);
        tile.setElevation(focused ? m(6) : 0);
        // Light mode: everything written on a focused tile turns white on the red. Dark mode keeps its colors.
        if (palette.light) selectContent(tile, focused, palette.tileSelectedText, true);
    }

    /**
     * Recolors what's inside a focused element: every text, and tinted icons when asked, turns the given color,
     * and gets its own color back when focus leaves. Pictures (banners, posters) are left alone.
     */
    void selectContent(View v, boolean selected, int color, boolean icons) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            Object normal = t.getTag(R.id.nn_normal_color);
            if (selected && normal == null) {
                t.setTag(R.id.nn_normal_color, t.getTextColors());
                t.setTextColor(color);
            } else if (!selected && normal != null) {
                t.setTextColor((android.content.res.ColorStateList) normal);
                t.setTag(R.id.nn_normal_color, null);
            }
        } else if (v instanceof ImageView) {
            if (!icons) return;
            ImageView i = (ImageView) v;
            Object normal = i.getTag(R.id.nn_normal_color);
            if (selected && normal == null && i.getColorFilter() != null) {
                i.setTag(R.id.nn_normal_color, i.getColorFilter());
                i.setColorFilter(color);
            } else if (!selected && normal != null) {
                i.setColorFilter((android.graphics.ColorFilter) normal);
                i.setTag(R.id.nn_normal_color, null);
            }
        } else if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int k = 0; k < g.getChildCount(); k++) selectContent(g.getChildAt(k), selected, color, icons);
        }
    }

    /** Light mode's selected look for a custom button's contents: white text and icons on the red. */
    void selectContentLight(View v, boolean selected) {
        if (palette.light) selectContent(v, selected, palette.tileSelectedText, true);
    }

    /** The selected fill: red with the gold edge, with the given cut corners. */
    NeonCutDrawable selectedFill(float cut) {
        return new NeonCutDrawable(palette.tileSelected, palette.focusOutline, m(cut), Math.max(1, m(1.5f)));
    }

    /**
     * A text link ("More", "Back to home"). Focused: in light mode the selected red with white text; in dark
     * mode just gold text.
     */
    void linkButton(LinearLayout b) {
        b.setFocusable(true);
        b.setOnFocusChangeListener((v, f) -> {
            if (palette.light) {
                v.setBackground(f ? selectedFill(6) : null);
                selectContent(v, f, palette.tileSelectedText, true);
            } else {
                selectContent(v, f, palette.gold, false);
            }
            lift(v, f);
        });
    }

    /** A focusable tile shell with the blue focus bar; content goes in at index 0. */
    FrameLayout tile(View.OnClickListener click) {
        FrameLayout tile = new FrameLayout(ctx);
        tile.setFocusable(true);
        tile.setClipToOutline(true);
        View bar = new View(ctx);
        bar.setBackgroundColor(palette.focusBar);
        bar.setVisibility(View.INVISIBLE);
        tile.addView(bar, new FrameLayout.LayoutParams(m(3), FrameLayout.LayoutParams.MATCH_PARENT));
        styleTile(tile, bar, false);
        tile.setOnFocusChangeListener((v, f) -> {
            styleTile(tile, bar, f);
            lift(v, f);
        });
        tile.setOnClickListener(click);
        return tile;
    }

    /** Round icon button. Focused: light mode red with a white icon; dark mode a gold disc and gold icon. */
    ImageView iconButton(int icon, String label, View.OnClickListener click) {
        ImageView b = new ImageView(ctx);
        b.setImageResource(icon);
        b.setContentDescription(label);
        b.setFocusable(true);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = m(12);
        b.setPadding(pad, pad, pad, pad);
        b.setColorFilter(palette.textSecondary);
        b.setOnClickListener(click);
        b.setOnFocusChangeListener((v, focused) -> {
            if (focused) {
                GradientDrawable ring = new GradientDrawable();
                ring.setShape(GradientDrawable.OVAL);
                ring.setColor(palette.iconFocusFill);
                ring.setStroke(palette.light ? Math.max(1, m(1.5f)) : hair(), palette.focusOutline);
                v.setBackground(ring);
            } else {
                v.setBackground(null);
            }
            int on = palette.light ? palette.tileSelectedText : palette.gold;
            ((ImageView) v).setColorFilter(focused ? on : palette.textSecondary);
            lift(v, focused);
        });
        return b;
    }

    /**
     * A cut-corner glass button (chips, "Pick another", menu items). Focus turns it the selected color with a gold
     * edge and its text the selected-text color (light mode: white, icons too).
     */
    void glassButton(LinearLayout b, float cut, int fill, int edge) {
        b.setFocusable(true);
        b.setBackground(glass(cut, fill, edge));
        b.setOnFocusChangeListener((v, f) -> {
            v.setBackground(f ? selectedFill(cut) : glass(cut, fill, edge));
            selectContent(v, f, palette.tileSelectedText, palette.light);
            lift(v, f);
        });
    }

    /** Section title with the red/gold edge: "PICKS FOR YOU". */
    FrameLayout edgeTitle(String s, float size, float spacing) {
        FrameLayout f = new FrameLayout(ctx);
        TextView gold = caps(s, size, palette.edgeGold, false, spacing);
        TextView red = caps(s, size, palette.edgeRed, false, spacing);
        TextView main = caps(s, size, palette.text, false, spacing);
        gold.setTranslationX(m(1));
        red.setTranslationX(-m(1));
        f.addView(gold);
        f.addView(red);
        f.addView(main);
        return f;
    }

    static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
