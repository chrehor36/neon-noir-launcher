package io.github.chrehor36.neonnoir;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * A smoked-glass panel with two cut corners (top left and bottom right), a fill and a hairline edge, like the
 * mockup's clip-path panels. No blur: the fill is plain transparency, which costs the GPU nothing extra.
 */
final class NeonCutDrawable extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float cut;

    NeonCutDrawable(int fillColor, int edgeColor, float cutPx, float edgePx) {
        fill.setColor(fillColor);
        edge.setColor(edgeColor);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(edgePx);
        cut = cutPx;
    }

    @Override
    protected void onBoundsChange(Rect b) {
        super.onBoundsChange(b);
        float inset = edge.getStrokeWidth() / 2f;
        float l = b.left + inset, t = b.top + inset, r = b.right - inset, bt = b.bottom - inset;
        path.reset();
        path.moveTo(l + cut, t);
        path.lineTo(r, t);
        path.lineTo(r, bt - cut);
        path.lineTo(r - cut, bt);
        path.lineTo(l, bt);
        path.lineTo(l, t + cut);
        path.close();
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawPath(path, fill);
        if (edge.getStrokeWidth() > 0) canvas.drawPath(path, edge);
    }

    @Override
    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        edge.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        fill.setColorFilter(cf);
        edge.setColorFilter(cf);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
