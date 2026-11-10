package io.github.chrehor36.neonnoir;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import java.util.Random;

/**
 * The Neon Noir backdrop: the base color, two soft glows that drift slowly (24 s and 30 s, as in the mockups),
 * falling rain, and fine scanlines on top.
 *
 * Kept cheap for a TV: each glow is drawn once into a hardware layer and then only moved; the rain is one small
 * tile repeated by a shader and slid down each frame; the scanlines are a static tile. No layer is ever redrawn
 * from scratch while it moves. start() and stop() follow Home being on screen, so nothing runs behind an app.
 */
final class NeonBackgroundView extends FrameLayout {
    private final Glow glowA, glowB;
    private final Rain rain;
    private final View scan;
    private int base;
    private ObjectAnimator driftA, driftB;

    NeonBackgroundView(Context context, NeonPalette palette) {
        super(context);
        base = palette.background;
        setBackgroundColor(base);
        float unit = context.getResources().getDisplayMetrics().widthPixels / 1280f;
        // Sizes and places from the mockup: 760px glow at right -160/top -300, 680px glow at left -200/top 60.
        glowA = new Glow(context, palette.glowA);
        LayoutParams a = new LayoutParams(Math.round(760 * unit), Math.round(760 * unit));
        a.leftMargin = Math.round((1280 - 760 + 160) * unit);
        a.topMargin = Math.round(-300 * unit);
        addView(glowA, a);
        glowB = new Glow(context, palette.glowB);
        LayoutParams b = new LayoutParams(Math.round(680 * unit), Math.round(680 * unit));
        b.leftMargin = Math.round(-200 * unit);
        b.topMargin = Math.round(60 * unit);
        addView(glowB, b);
        rain = new Rain(context, palette.rain, unit);
        addView(rain, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        scan = new Scanlines(context, palette.scanline, unit);
        addView(scan, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setClipChildren(true);
    }

    /** The sky: base color and the two glows (they follow dawn, day, sunset, night and rain, or Jazz mode). */
    void setSky(int base, int a, int b) {
        if (this.base != base) {
            this.base = base;
            setBackgroundColor(base);
        }
        glowA.setColor(a);
        glowB.setColor(b);
    }

    /** Home is on screen: drift and rain. */
    void start() {
        if (driftA == null) {
            float unit = getResources().getDisplayMetrics().widthPixels / 1280f;
            driftA = drift(glowA, 24_000, 40 * unit, -24 * unit, false);
            driftB = drift(glowB, 30_000, -40 * unit, 24 * unit, true);
        }
        rain.start();
    }

    /** Off screen: everything holds still. */
    void stop() {
        if (driftA != null) {
            driftA.cancel();
            driftB.cancel();
            driftA = driftB = null;
        }
        rain.stop();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    /** translate(40px, -24px) scale(1.08) and back, ease-in-out, as the mockup's drift keyframes. */
    private static ObjectAnimator drift(View v, long ms, float dx, float dy, boolean reverse) {
        ObjectAnimator o = ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0, dx),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0, dy),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f));
        o.setDuration(ms / 2);
        o.setRepeatCount(ValueAnimator.INFINITE);
        o.setRepeatMode(ValueAnimator.REVERSE);
        o.setInterpolator(new AccelerateDecelerateInterpolator());
        o.start();
        if (reverse) o.setCurrentPlayTime(ms / 2);
        return o;
    }

    /** One radial glow, fading to clear at 65% of its radius; drawn into a hardware layer once per color. */
    private static final class Glow extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int color;

        Glow(Context c, int color) {
            super(c);
            this.color = color;
            setLayerType(LAYER_TYPE_HARDWARE, null);
        }

        void setColor(int color) {
            if (this.color == color) return;
            this.color = color;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float r = getWidth() / 2f;
            if (r <= 0) return;
            p.setShader(new RadialGradient(r, r, r * 0.65f, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
            canvas.drawCircle(r, r, r * 0.65f, p);
        }
    }

    /**
     * Rain: thin streaks tilted 15 degrees, broken into dashes, in a tile that repeats seamlessly. Each frame only
     * moves the shader by one step along the streaks (one tile height per cycle), so the GPU draws one textured
     * rectangle per frame.
     */
    private static final class Rain extends View {
        private final Paint paint = new Paint();
        private final Matrix shift = new Matrix();
        private final int tileW, tileH;
        private ValueAnimator fall;
        private float phase;

        Rain(Context c, int color, float unit) {
            super(c);
            // 15 degrees: across the tile's height the streak moves one tile width sideways, so tiles join up.
            tileH = Math.round(340 * unit);
            tileW = Math.max(8, Math.round(tileH * (float) Math.tan(Math.toRadians(15))));
            Bitmap tile = Bitmap.createBitmap(tileW, tileH, Bitmap.Config.ARGB_8888);
            Canvas t = new Canvas(tile);
            Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
            line.setColor(color);
            line.setStrokeWidth(Math.max(1f, unit * 0.9f));
            Random rnd = new Random(7);
            float spacing = 22 * unit;   // the mockup's 22px streak spacing
            float slope = tileW / (float) tileH;
            for (float x0 = 0; x0 < tileW; x0 += spacing / 3f) {
                float y = rnd.nextFloat() * tileH;
                for (int k = 0; k < 3; k++) {
                    float len = (14 + rnd.nextFloat() * 28) * unit;
                    // Draw each dash and its wrapped copies so the tile repeats without seams.
                    for (int wy = -1; wy <= 1; wy++) {
                        for (int wx = -1; wx <= 1; wx++) {
                            float ya = y + wy * tileH, yb = ya + len;
                            float xa = x0 - ya * slope + wx * tileW + (wy * tileH) * slope;
                            float xb = xa - len * slope;
                            t.drawLine(xa, ya, xb, yb, line);
                        }
                    }
                    y = (y + tileH / 3f + rnd.nextFloat() * 40 * unit) % tileH;
                }
            }
            paint.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        }

        void start() {
            if (fall != null) return;
            fall = ValueAnimator.ofFloat(0f, 1f);
            fall.setDuration(1600);
            fall.setRepeatCount(ValueAnimator.INFINITE);
            fall.setInterpolator(new LinearInterpolator());
            fall.addUpdateListener(a -> {
                phase = (float) a.getAnimatedValue();
                invalidate();
            });
            fall.start();
        }

        void stop() {
            if (fall != null) {
                fall.cancel();
                fall = null;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            // Down and slightly left along the streaks: one tile (height, and width sideways) per cycle.
            shift.setTranslate(-phase * tileW, phase * tileH);
            paint.getShader().setLocalMatrix(shift);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }
    }

    /** A faint line every 4 px at 1080p; static. */
    private static final class Scanlines extends View {
        private final Paint paint = new Paint();

        Scanlines(Context c, int color, float unit) {
            super(c);
            int step = Math.max(3, Math.round(4 * unit / 1.5f));
            Bitmap tile = Bitmap.createBitmap(1, step, Bitmap.Config.ARGB_8888);
            tile.setPixel(0, 0, color);
            paint.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }
    }
}
