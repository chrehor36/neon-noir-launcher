package io.github.chrehor36.neonnoir;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

/**
 * Jazz mode's turntable: a grooved record with an oxblood label and brass ring, and the tonearm, as in the Jazz
 * board. The record spins (33 rpm-ish, one turn per 1.8 s) for 20 seconds after each pick, then rests. The record is
 * drawn once into a hardware layer, so spinning only rotates a texture.
 */
final class TurntableView extends FrameLayout {
    private static final long SPIN_MS = 20_000L;
    private static final int BRASS = Color.rgb(0x9C, 0x7A, 0x3E);
    private static final int OXBLOOD = Color.rgb(0x5E, 0x14, 0x14);

    private final Disc disc;
    private ObjectAnimator spin;
    private final Runnable stopSpin = this::stopSpinning;

    /** size: the record's diameter in pixels. The view is 7/6 of it wide, for the tonearm. */
    TurntableView(Context ctx, int size) {
        super(ctx);
        disc = new Disc(ctx);
        addView(disc, new LayoutParams(size, size));
        Arm arm = new Arm(ctx);
        LayoutParams alp = new LayoutParams(Math.round(size * 110f / 240f), Math.round(size * 190f / 240f));
        alp.leftMargin = Math.round(size * (240f + 50f - 110f) / 240f);
        alp.topMargin = 0;
        addView(arm, alp);
        setClipChildren(false);
        setMinimumWidth(Math.round(size * 280f / 240f));
    }

    void spinFor20s() {
        removeCallbacks(stopSpin);
        if (spin == null) {
            disc.setLayerType(LAYER_TYPE_HARDWARE, null);
            spin = ObjectAnimator.ofFloat(disc, View.ROTATION, disc.getRotation(), disc.getRotation() + 360f);
            spin.setDuration(1800);
            spin.setInterpolator(new LinearInterpolator());
            spin.setRepeatCount(ValueAnimator.INFINITE);
            spin.start();
        }
        postDelayed(stopSpin, SPIN_MS);
    }

    void stopSpinning() {
        removeCallbacks(stopSpin);
        if (spin != null) {
            spin.cancel();
            spin = null;
        }
        disc.setLayerType(LAYER_TYPE_NONE, null);
    }

    @Override
    protected void onDetachedFromWindow() {
        stopSpinning();
        super.onDetachedFromWindow();
    }

    /** The record, drawn in a 240-unit box like the mockup's CSS. */
    private static final class Disc extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Disc(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float s = Math.min(getWidth(), getHeight());
            if (s <= 0) return;
            float k = s / 240f, cx = getWidth() / 2f, cy = getHeight() / 2f;
            // Vinyl with grooves: a faint warm ring every 4 units.
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            p.setColor(Color.rgb(0x07, 0x02, 0x02));
            canvas.drawCircle(cx, cy, 120 * k, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(0.6f, k));
            p.setColor(Color.rgb(0x1A, 0x0A, 0x07));
            for (float r = 44; r < 118; r += 4) canvas.drawCircle(cx, cy, r * k, p);
            // A soft sheen across the grooves, so the spin reads.
            p.setStyle(Paint.Style.FILL);
            p.setShader(new android.graphics.SweepGradient(cx, cy,
                    new int[]{Color.argb(0, 255, 230, 200), Color.argb(26, 255, 230, 200), Color.argb(0, 255, 230, 200),
                            Color.argb(0, 255, 230, 200), Color.argb(20, 255, 230, 200), Color.argb(0, 255, 230, 200)},
                    new float[]{0f, 0.08f, 0.18f, 0.5f, 0.58f, 0.68f}));
            canvas.drawCircle(cx, cy, 118 * k, p);
            p.setShader(null);
            // Edge hairline.
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, k));
            p.setColor(Color.argb(56, 176, 138, 69));
            canvas.drawCircle(cx, cy, 119.5f * k, p);
            // Label: oxblood, a brass ring at 60-63%, then oxblood again.
            p.setStyle(Paint.Style.FILL);
            p.setColor(OXBLOOD);
            canvas.drawCircle(cx, cy, 41 * k, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(41 * 0.03f * k);
            p.setColor(BRASS);
            canvas.drawCircle(cx, cy, 41 * 0.615f * k, p);
            // A small printed line on the label, so the label visibly turns.
            p.setStrokeWidth(Math.max(1f, 1.2f * k));
            p.setColor(Color.argb(140, 0x9C, 0x7A, 0x3E));
            canvas.drawLine(cx - 14 * k, cy - 30 * k, cx + 14 * k, cy - 30 * k, p);
            // Spindle.
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(0x16, 0x05, 0x04));
            canvas.drawCircle(cx, cy, 5 * k, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * k);
            p.setColor(BRASS);
            canvas.drawCircle(cx, cy, 6 * k, p);
        }
    }

    /** The tonearm, from the mockup's SVG (viewBox 120x210). */
    private static final class Arm extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        Arm(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (getWidth() <= 0) return;
            canvas.save();
            canvas.scale(getWidth() / 120f, getHeight() / 210f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(4);
            p.setColor(Color.rgb(0xA8, 0x97, 0x7E));
            path.reset();
            path.moveTo(92, 22);
            path.lineTo(98, 128);
            path.lineTo(66, 176);
            canvas.drawPath(path, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(0x16, 0x05, 0x04));
            canvas.drawCircle(92, 22, 15, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2);
            p.setColor(BRASS);
            canvas.drawCircle(92, 22, 15, p);
            p.setStyle(Paint.Style.FILL);
            canvas.drawCircle(92, 22, 4, p);
            canvas.save();
            canvas.rotate(-35, 65, 175);
            canvas.drawRoundRect(new RectF(54, 168, 76, 182), 2, 2, p);
            canvas.restore();
            canvas.restore();
        }
    }
}
