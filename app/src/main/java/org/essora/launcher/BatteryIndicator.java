package org.essora.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Clock-matched battery outline. Updated only when Android reports a battery change. */
public final class BatteryIndicator extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int level = 0;
    private int fillColor = Color.WHITE;
    private int outlineColor = Color.WHITE;
    private int labelColor = Color.WHITE;
    public BatteryIndicator(Context ctx) { super(ctx); }
    public void setLevel(int value) { level = Math.max(0, Math.min(100, value)); invalidate(); }
    public void setFillColor(int color) { fillColor = color; invalidate(); }
    public void setOutlineColor(int color) { outlineColor = color; invalidate(); }
    public void setLabelColor(int color) { labelColor = color; invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = getResources().getDisplayMetrics().density;
        float left = 3f * scale, top = 9f * scale;
        float right = getWidth() - 11f * scale, bottom = getHeight() - 9f * scale;
        float radius = 12f * scale;
        paint.setColor(outlineColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.3f * scale);
        canvas.drawRoundRect(new RectF(left, top, right, bottom), radius, radius, paint);
        canvas.drawRoundRect(new RectF(right + 2 * scale, top + 9 * scale,
                right + 6 * scale, bottom - 9 * scale), 2 * scale, 2 * scale, paint);
        paint.setStyle(Paint.Style.FILL);
        float innerLeft = left + 5f * scale, innerRight = right - 5f * scale;
        float fillWidth = (innerRight - innerLeft) * level / 100f;
        paint.setColor(fillColor);
        if (fillWidth > 0) canvas.drawRoundRect(new RectF(innerLeft, top + 5 * scale,
                innerLeft + fillWidth, bottom - 5 * scale), 7 * scale, 7 * scale, paint);
        // A translucent dark label area keeps percentage readable on top of the white fill.
        paint.setColor(0xe4000000);
        float labelLeft = right - 50f * scale;
        canvas.drawRoundRect(new RectF(labelLeft, top + 2 * scale, right - 2 * scale,
                bottom - 2 * scale), 6 * scale, 6 * scale, paint);
        paint.setColor(labelColor);
        paint.setTextSize(17f * scale);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.RIGHT);
        Paint.FontMetrics f = paint.getFontMetrics();
        float baseline = (top + bottom) / 2f - (f.ascent + f.descent) / 2f;
        canvas.drawText(level + "%", right - 8f * scale, baseline, paint);
    }
}
