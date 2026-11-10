package io.github.chrehor36.neonnoir;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import java.util.Calendar;

/**
 * The hairline analog clock: 60 fine ticks (a longer one every 5 minutes), hour and minute hands, no second hand,
 * and a small colored center dot. Drawn in a 200-unit box like the mockup's SVG, and redrawn once a minute.
 */
final class NeonClockView extends View {
    private NeonPalette palette;
    private final Paint major = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint minor = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hand = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            invalidate();
            updateDescription();
            long now = System.currentTimeMillis();
            postDelayed(this, 60_000 - (now % 60_000) + 50);
        }
    };

    NeonClockView(Context context, NeonPalette palette) {
        super(context);
        major.setStrokeCap(Paint.Cap.ROUND);
        minor.setStrokeCap(Paint.Cap.ROUND);
        hand.setStrokeCap(Paint.Cap.ROUND);
        setPalette(palette);
    }

    private float weight = 1f;

    /** Stroke weight multiplier: the small clock chip in Jazz mode uses about 2.2 so its hairlines stay visible. */
    void setWeight(float weight) {
        this.weight = weight;
        invalidate();
    }

    void setPalette(NeonPalette palette) {
        this.palette = palette;
        major.setColor(palette.text);
        minor.setColor(palette.text);
        minor.setAlpha(72);
        hand.setColor(palette.text);
        dot.setColor(palette.clockDot);
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(tick);
        post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    private void updateDescription() {
        Calendar now = Calendar.getInstance();
        int h = now.get(Calendar.HOUR);
        setContentDescription("Clock, " + (h == 0 ? 12 : h) + ":" + String.format(java.util.Locale.US, "%02d", now.get(Calendar.MINUTE)));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;
        float k = size / 200f;
        canvas.save();
        canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
        canvas.scale(k, k);
        major.setStrokeWidth(1.4f * weight);
        minor.setStrokeWidth(0.6f * weight);
        for (int i = 0; i < 60; i++) {
            boolean big = i % 5 == 0;
            double a = Math.toRadians(i * 6 - 90);
            float len = big ? 9f : 4f;
            float x1 = 100 + (float) (94 * Math.cos(a));
            float y1 = 100 + (float) (94 * Math.sin(a));
            float x2 = 100 + (float) ((94 - len) * Math.cos(a));
            float y2 = 100 + (float) ((94 - len) * Math.sin(a));
            canvas.drawLine(x1, y1, x2, y2, big ? major : minor);
        }
        Calendar now = Calendar.getInstance();
        float m = now.get(Calendar.MINUTE) + now.get(Calendar.SECOND) / 60f;
        float h = now.get(Calendar.HOUR) + m / 60f;
        drawHand(canvas, h * 30f, 48f, 8f, 3.2f * weight);
        drawHand(canvas, m * 6f, 76f, 10f, 2.0f * weight);
        canvas.drawCircle(100, 100, 3.2f * weight, dot);
        canvas.restore();
    }

    private void drawHand(Canvas canvas, float degrees, float length, float tail, float width) {
        hand.setStrokeWidth(width);
        double a = Math.toRadians(degrees - 90);
        float cos = (float) Math.cos(a);
        float sin = (float) Math.sin(a);
        canvas.drawLine(100 - tail * cos, 100 - tail * sin, 100 + length * cos, 100 + length * sin, hand);
    }

    @SuppressWarnings("unused")
    private static int dim(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
