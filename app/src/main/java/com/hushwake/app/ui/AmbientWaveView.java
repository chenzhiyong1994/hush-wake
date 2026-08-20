package com.hushwake.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Lightweight ambient waveform used as playback feedback without inspecting audio samples. */
public final class AmbientWaveView extends View {
    private final Paint primary = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint secondary = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private boolean active;
    private float phase;

    public AmbientWaveView(Context context, boolean active) {
        super(context);
        this.active = active;
        primary.setColor(Ui.ACID);
        primary.setStyle(Paint.Style.STROKE);
        primary.setStrokeWidth(Ui.dp(context, 1.7f));
        primary.setStrokeCap(Paint.Cap.ROUND);
        secondary.setColor(Ui.BLUE);
        secondary.setAlpha(95);
        secondary.setStyle(Paint.Style.STROKE);
        secondary.setStrokeWidth(Ui.dp(context, 1f));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setActive(boolean active) {
        this.active = active;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float center = getHeight() / 2f;
        drawWave(canvas, secondary, width, center, active ? 7f : 2f, phase * .72f, .12f);
        drawWave(canvas, primary, width, center, active ? 11f : 3f, phase, .16f);
        if (active && isAttachedToWindow()) {
            phase += .24f;
            postInvalidateOnAnimation();
        }
    }

    private void drawWave(
            Canvas canvas,
            Paint paint,
            float width,
            float center,
            float amplitude,
            float offset,
            float frequency) {
        path.reset();
        for (int x = 0; x <= width; x += 3) {
            float envelope = (float) Math.sin(Math.PI * x / Math.max(1f, width));
            float y = center + (float) Math.sin(x * frequency + offset) * amplitude * envelope;
            if (x == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        canvas.drawPath(path, paint);
    }
}
