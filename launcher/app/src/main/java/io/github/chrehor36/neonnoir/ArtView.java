package io.github.chrehor36.neonnoir;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Art Mode's screen: one painting filling the screen, a new one every 5 minutes with a slow crossfade, and a
 * small caption at the bottom left (Title · Artist · Year), as in the Art board. Shared by ArtActivity (Home left
 * idle, or the frame button) and ArtDream (the system screensaver).
 */
final class ArtView extends FrameLayout {
    private static final long TURN_MS = 5 * 60_000L;
    private static final int MAX_WIDTH = 1920;

    private final float unit;
    private final ImageView[] layers = new ImageView[2];
    private int front = 0;
    private final LinearLayout caption;
    private final TextView title, artist, year;
    private final View veil;
    private boolean running;
    private int generation;

    private final Runnable turn = this::showNext;

    ArtView(Context ctx) {
        super(ctx);
        setBackgroundColor(Color.rgb(10, 6, 6));
        unit = ctx.getResources().getDisplayMetrics().widthPixels / 1280f;
        for (int i = 0; i < 2; i++) {
            ImageView v = new ImageView(ctx);
            v.setScaleType(ImageView.ScaleType.CENTER_CROP);
            v.setAlpha(0f);
            layers[i] = v;
            addView(v, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }

        Typeface arimo = font(R.font.nn_arimo_regular, Typeface.DEFAULT);
        Typeface arimoBold = font(R.font.nn_arimo_bold, Typeface.DEFAULT_BOLD);
        Typeface fraunces = Typeface.create(font(R.font.nn_fraunces_semibold, Typeface.SERIF), Typeface.ITALIC);

        caption = new LinearLayout(ctx);
        caption.setOrientation(LinearLayout.HORIZONTAL);
        caption.setGravity(Gravity.CENTER_VERTICAL);
        caption.setPadding(m(14), m(7), m(16), m(7));
        caption.setBackground(new NeonCutDrawable(Color.argb(140, 16, 6, 6), Color.argb(82, 226, 176, 74), m(6), Math.max(1, m(1))));
        title = text(14, Color.rgb(0xF3, 0xEC, 0xE4), fraunces, 0f);
        artist = text(11, Color.rgb(0xE2, 0xB0, 0x4A), arimoBold, 0.14f);
        year = text(11, Color.rgb(0xBB, 0xA3, 0x8C), arimo, 0.14f);
        View bar = new View(ctx);
        bar.setBackgroundColor(Color.rgb(0xE2, 0xB0, 0x4A));
        caption.addView(bar, new LinearLayout.LayoutParams(Math.max(2, m(2)), m(18)));
        ((LinearLayout.LayoutParams) bar.getLayoutParams()).rightMargin = m(10);
        caption.addView(title);
        caption.addView(dot(arimo));
        caption.addView(artist);
        caption.addView(dot(arimo));
        caption.addView(year);
        caption.setAlpha(0f);
        LayoutParams clp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        clp.leftMargin = m(32);
        clp.bottomMargin = m(28);
        addView(caption, clp);

        // At night, when the system-wide night layer is not running, Art Mode dims itself.
        veil = new View(ctx);
        veil.setBackground(new ColorDrawable(Color.argb(64, 26, 12, 0)));
        veil.setVisibility(GONE);
        addView(veil, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    void start() {
        if (running) return;
        running = true;
        showNext();
    }

    void stop() {
        running = false;
        generation++;
        removeCallbacks(turn);
    }

    private void showNext() {
        removeCallbacks(turn);
        if (!running) return;
        int gen = ++generation;
        boolean night = NeonSky.isNight(getContext());
        veil.setVisibility(night ? VISIBLE : GONE);
        Context app = getContext().getApplicationContext();
        NeonNet.load(() -> {
            for (int tries = 0; tries < 3; tries++) {
                NeonArt.Work w = NeonArt.next(app, night);
                if (w == null) return null;
                Bitmap b = NeonNet.image(app, w.imageUrl(), MAX_WIDTH);
                if (b != null) return new Object[]{w, b};
            }
            return null;
        }, result -> {
            if (gen != generation || !running) {
                if (result != null) ((Bitmap) result[1]).recycle();
                return;
            }
            if (result == null) {
                // Offline with nothing cached: try again in a minute.
                postDelayed(turn, 60_000L);
                return;
            }
            show((NeonArt.Work) result[0], (Bitmap) result[1]);
            postDelayed(turn, TURN_MS);
        });
    }

    private void show(NeonArt.Work w, Bitmap b) {
        ImageView next = layers[1 - front], old = layers[front];
        next.setImageBitmap(b);
        next.animate().alpha(1f).setDuration(2500).start();
        old.animate().alpha(0f).setDuration(2500).withEndAction(() -> {
            android.graphics.drawable.Drawable d = old.getDrawable();
            old.setImageDrawable(null);
            if (d instanceof android.graphics.drawable.BitmapDrawable) {
                Bitmap ob = ((android.graphics.drawable.BitmapDrawable) d).getBitmap();
                if (ob != null && ob != b) ob.recycle();
            }
        }).start();
        front = 1 - front;
        caption.animate().alpha(0f).setDuration(600).withEndAction(() -> {
            title.setText(w.title);
            artist.setText(w.artist.toUpperCase(java.util.Locale.getDefault()));
            year.setText(w.year.toUpperCase(java.util.Locale.getDefault()));
            caption.animate().alpha(1f).setDuration(1200).start();
        }).start();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private TextView dot(Typeface face) {
        TextView t = text(11, Color.rgb(0xBB, 0xA3, 0x8C), face, 0f);
        t.setText("·");
        t.setPadding(m(8), 0, m(8), 0);
        return t;
    }

    private TextView text(float size, int color, Typeface face, float spacing) {
        TextView t = new TextView(getContext());
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, m(size));
        t.setTextColor(color);
        t.setTypeface(face);
        t.setLetterSpacing(spacing);
        t.setSingleLine(true);
        t.setMaxWidth(m(520));
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    private Typeface font(int res, Typeface fallback) {
        try {
            return getResources().getFont(res);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private int m(float px) {
        return Math.round(px * unit);
    }
}
