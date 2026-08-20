package com.hushwake.app.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.PathInterpolator;
import com.hushwake.app.R;

/** Branded cold-start transition that continues seamlessly from Android's system splash. */
public final class BrandLaunchView extends View {
    private static final long INTRO_DURATION_MS = 1_250L;

    private static final int BACKGROUND_TOP = Color.rgb(7, 11, 19);
    private static final int BACKGROUND_BOTTOM = Color.rgb(13, 21, 34);
    private static final int TILE = Color.rgb(16, 24, 42);
    private static final int TILE_LINE = Color.rgb(39, 52, 74);
    private static final int CREAM = Color.rgb(255, 243, 217);
    private static final int AMBER = Color.rgb(245, 176, 84);
    private static final int MINT = Color.rgb(85, 207, 194);
    private static final int COPY = Color.rgb(183, 192, 208);
    private static final int MUTED = Color.rgb(101, 113, 134);

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blueGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint amberGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final RectF bounds = new RectF();
    private final Path wave = new Path();

    private float progress = .10f;
    private ValueAnimator animator;

    public BrandLaunchView(Context context) {
        super(context);
        setBackgroundColor(BACKGROUND_TOP);
        setClickable(true);
        setFocusable(true);
        setContentDescription(context.getString(R.string.launch_accessibility));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void play(Runnable onFinished) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            progress = 1f;
            invalidate();
            postDelayed(onFinished, 420L);
            return;
        }
        animator = ValueAnimator.ofFloat(progress, 1f);
        animator.setDuration(INTRO_DURATION_MS);
        animator.setInterpolator(new PathInterpolator(.18f, .78f, .24f, 1f));
        animator.addUpdateListener(
                animation -> {
                    progress = (float) animation.getAnimatedValue();
                    invalidate();
                });
        animator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (isAttachedToWindow()) onFinished.run();
                    }
                });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        backgroundPaint.setShader(
                new LinearGradient(
                        0f,
                        0f,
                        width,
                        height,
                        new int[] {BACKGROUND_TOP, Color.rgb(11, 16, 27), BACKGROUND_BOTTOM},
                        new float[] {0f, .48f, 1f},
                        Shader.TileMode.CLAMP));
        blueGlowPaint.setShader(
                new RadialGradient(
                        width * .22f,
                        height * .21f,
                        width * .58f,
                        new int[] {
                            Color.argb(92, 38, 77, 105),
                            Color.argb(38, 24, 49, 73),
                            Color.TRANSPARENT
                        },
                        new float[] {0f, .46f, 1f},
                        Shader.TileMode.CLAMP));
        amberGlowPaint.setShader(
                new RadialGradient(
                        width * .88f,
                        height * .69f,
                        width * .60f,
                        new int[] {
                            Color.argb(57, 245, 176, 84),
                            Color.argb(24, 186, 106, 47),
                            Color.TRANSPARENT
                        },
                        new float[] {0f, .48f, 1f},
                        Shader.TileMode.CLAMP));
        ringPaint.setShader(
                new LinearGradient(
                        0f,
                        0f,
                        width,
                        0f,
                        new int[] {
                            Color.TRANSPARENT,
                            Color.argb(112, 85, 207, 194),
                            Color.argb(86, 255, 243, 217),
                            Color.TRANSPARENT
                        },
                        new float[] {0f, .25f, .70f, 1f},
                        Shader.TileMode.CLAMP));
        wavePaint.setShader(
                new LinearGradient(
                        0f,
                        0f,
                        width,
                        0f,
                        new int[] {
                            Color.TRANSPARENT,
                            Color.argb(104, 85, 207, 194),
                            Color.argb(118, 245, 176, 84),
                            Color.TRANSPARENT
                        },
                        new float[] {0f, .42f, .58f, 1f},
                        Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        canvas.drawRect(0f, 0f, width, height, backgroundPaint);

        float atmosphere = segment(progress, 0f, .54f);
        blueGlowPaint.setAlpha(Math.round(255f * atmosphere));
        amberGlowPaint.setAlpha(Math.round(255f * atmosphere));
        canvas.drawRect(0f, 0f, width, height, blueGlowPaint);
        canvas.drawRect(0f, 0f, width, height, amberGlowPaint);

        float centerX = width / 2f;
        float centerY = height * .385f;
        drawRings(canvas, centerX, centerY, width, atmosphere);
        drawAmbientWave(canvas, width, height, atmosphere);

        float tileReveal = segment(progress, .04f, .34f);
        float tileSize = Math.min(width * .29f, dp(118f));
        float tileScale = .91f + .09f * tileReveal;
        canvas.save();
        canvas.scale(tileScale, tileScale, centerX, centerY);
        drawTile(canvas, centerX, centerY, tileSize, tileReveal);
        canvas.restore();

        drawWordmark(canvas, centerX, centerY, tileSize);
        drawLoadingCue(canvas, centerX, height);

        if (progress < 1f && isAttachedToWindow()) postInvalidateOnAnimation();
    }

    private void drawRings(
            Canvas canvas, float centerX, float centerY, float width, float reveal) {
        float pulse = (float) Math.sin(progress * Math.PI) * dp(7f);
        float[] widths = {width * .76f, width * .90f, width * 1.04f};
        float[] heights = {width * .61f, width * .73f, width * .84f};
        int[] alphas = {118, 62, 34};
        float[] strokes = {1.25f, .8f, .55f};
        for (int i = 0; i < widths.length; i++) {
            float ringWidth = widths[i] + pulse * (i + 1) * .45f;
            float ringHeight = heights[i] + pulse * (i + 1) * .34f;
            bounds.set(
                    centerX - ringWidth / 2f,
                    centerY - ringHeight / 2f,
                    centerX + ringWidth / 2f,
                    centerY + ringHeight / 2f);
            ringPaint.setStrokeWidth(dp(strokes[i]));
            ringPaint.setAlpha(Math.round(alphas[i] * reveal));
            canvas.drawOval(bounds, ringPaint);
        }
    }

    private void drawAmbientWave(Canvas canvas, float width, float height, float reveal) {
        float baseY = height * .665f;
        wave.reset();
        wave.moveTo(-width * .10f, baseY);
        wave.cubicTo(
                width * .14f,
                baseY - height * .05f,
                width * .30f,
                baseY + height * .055f,
                width * .50f,
                baseY);
        wave.cubicTo(
                width * .72f,
                baseY - height * .055f,
                width * .86f,
                baseY + height * .035f,
                width * 1.10f,
                baseY - height * .045f);
        wavePaint.setStrokeWidth(dp(1.1f));
        wavePaint.setAlpha(Math.round(92f * reveal));
        canvas.drawPath(wave, wavePaint);

        canvas.save();
        canvas.translate(0f, dp(18f));
        wavePaint.setStrokeWidth(dp(.6f));
        wavePaint.setAlpha(Math.round(42f * reveal));
        canvas.drawPath(wave, wavePaint);
        canvas.restore();
    }

    private void drawTile(
            Canvas canvas, float centerX, float centerY, float tileSize, float reveal) {
        float half = tileSize / 2f;
        float radius = tileSize * .27f;
        for (int i = 4; i >= 1; i--) {
            float spread = dp(i * 4f);
            bounds.set(
                    centerX - half - spread,
                    centerY - half - spread * .35f,
                    centerX + half + spread,
                    centerY + half + spread);
            shapePaint.setColor(Color.argb(Math.round((5 - i) * 7f * reveal), 245, 176, 84));
            canvas.drawRoundRect(bounds, radius + spread, radius + spread, shapePaint);
        }
        bounds.set(centerX - half, centerY - half, centerX + half, centerY + half);
        shapePaint.setColor(withAlpha(TILE, reveal));
        canvas.drawRoundRect(bounds, radius, radius, shapePaint);
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(dp(1f));
        shapePaint.setColor(withAlpha(TILE_LINE, reveal));
        canvas.drawRoundRect(bounds, radius, radius, shapePaint);
        shapePaint.setStyle(Paint.Style.FILL);

        float scale = tileSize / 108f;
        float left = centerX - half;
        float top = centerY - half;
        drawMark(canvas, left, top, scale, reveal);
    }

    private void drawMark(Canvas canvas, float left, float top, float scale, float reveal) {
        shapePaint.setColor(withAlpha(CREAM, reveal));
        bounds.set(
                left + 19.8f * scale,
                top + 30.6f * scale,
                left + 88.2f * scale,
                top + 77.4f * scale);
        canvas.drawRoundRect(bounds, 23.4f * scale, 23.4f * scale, shapePaint);

        shapePaint.setColor(withAlpha(TILE, reveal));
        bounds.set(
                left + 28.35f * scale,
                top + 38.7f * scale,
                left + 79.65f * scale,
                top + 69.3f * scale);
        canvas.drawRoundRect(bounds, 15.3f * scale, 15.3f * scale, shapePaint);

        float[] barLeft = {36.9f, 49.95f, 63f};
        float[] barTop = {51.75f, 40.95f, 46.35f};
        float[] delays = {.10f, .18f, .26f};
        float bottom = top + 64.8f * scale;
        shapePaint.setColor(withAlpha(AMBER, reveal));
        for (int i = 0; i < barLeft.length; i++) {
            float barReveal = segment(progress, delays[i], delays[i] + .26f);
            float targetTop = top + barTop[i] * scale;
            float animatedTop = bottom - (bottom - targetTop) * barReveal;
            bounds.set(
                    left + barLeft[i] * scale,
                    animatedTop,
                    left + (barLeft[i] + 8.1f) * scale,
                    bottom);
            canvas.drawRoundRect(bounds, 4.05f * scale, 4.05f * scale, shapePaint);
        }

        float dotReveal = segment(progress, .30f, .54f);
        float pulse = .90f + .10f * (float) Math.sin(progress * Math.PI * 2f);
        shapePaint.setColor(withAlpha(MINT, dotReveal));
        canvas.drawCircle(
                left + 80.55f * scale,
                top + 40.05f * scale,
                4.05f * scale * pulse,
                shapePaint);
    }

    private void drawWordmark(Canvas canvas, float centerX, float centerY, float tileSize) {
        float titleReveal = segment(progress, .30f, .58f);
        float titleY = centerY + tileSize / 2f + dp(68f);
        textPaint.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        textPaint.setTextSize(dp(39f));
        textPaint.setColor(withAlpha(CREAM, titleReveal));
        drawTextCentered(canvas, "悄醒", centerX, titleY - dp(7f) * (1f - titleReveal), textPaint);

        float englishReveal = segment(progress, .40f, .66f);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        textPaint.setTextSize(dp(9.5f));
        textPaint.setColor(withAlpha(Color.rgb(131, 145, 168), englishReveal));
        drawTextCentered(canvas, "H U S H W A K E", centerX, titleY + dp(35f), textPaint);

        float taglineReveal = segment(progress, .50f, .76f);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        textPaint.setTextSize(dp(13f));
        textPaint.setColor(withAlpha(COPY, taglineReveal));
        drawTextCentered(
                canvas,
                getContext().getString(R.string.launch_tagline),
                centerX,
                titleY + dp(94f),
                textPaint);
    }

    private void drawLoadingCue(Canvas canvas, float centerX, float height) {
        float reveal = segment(progress, .58f, .82f);
        float dotsY = height - dp(76f);
        int[] colors = {MINT, CREAM, AMBER};
        for (int i = 0; i < colors.length; i++) {
            float wave = .62f + .38f * (float) Math.sin(progress * Math.PI * 4f - i * .85f);
            shapePaint.setColor(withAlpha(colors[i], reveal * wave));
            canvas.drawCircle(centerX + dp((i - 1) * 15f), dotsY, dp(2.7f), shapePaint);
        }
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        textPaint.setTextSize(dp(9.5f));
        textPaint.setColor(withAlpha(MUTED, reveal));
        drawTextCentered(
                canvas,
                getContext().getString(R.string.launch_loading),
                centerX,
                dotsY + dp(30f),
                textPaint);
    }

    private static void drawTextCentered(
            Canvas canvas, String text, float centerX, float baseline, Paint paint) {
        canvas.drawText(text, centerX - paint.measureText(text) / 2f, baseline, paint);
    }

    private static int withAlpha(int color, float alpha) {
        return Color.argb(
                Math.round(255f * clamp(alpha)),
                Color.red(color),
                Color.green(color),
                Color.blue(color));
    }

    private static float segment(float value, float start, float end) {
        float normalized = clamp((value - start) / Math.max(.001f, end - start));
        return 1f - (float) Math.pow(1f - normalized, 3f);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
