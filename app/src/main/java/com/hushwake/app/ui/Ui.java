package com.hushwake.app.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

public final class Ui {
    public static final int INK = Color.rgb(8, 15, 13);
    public static final int PANEL = Color.rgb(16, 27, 24);
    public static final int RAISED = Color.rgb(22, 36, 31);
    public static final int LINE = Color.rgb(49, 69, 61);
    public static final int PAPER = Color.rgb(239, 246, 236);
    public static final int MUTED = Color.rgb(157, 176, 166);
    public static final int ACID = Color.rgb(233, 255, 112);
    public static final int WARM = Color.rgb(255, 184, 107);
    public static final int DANGER = Color.rgb(255, 126, 112);

    private Ui() {}

    public static TextView text(Context context, String value, int sp, int color, Typeface face) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(face);
        view.setLineSpacing(dp(context, 3), 1f);
        return view;
    }

    public static TextView eyebrow(Context context, String value) {
        TextView view = text(context, value, 11, WARM, Typeface.MONOSPACE);
        view.setLetterSpacing(.12f);
        return view;
    }

    public static LinearLayout card(Context context, int color) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 18));
        card.setBackground(round(context, color, 20, LINE));
        card.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    public static Button button(Context context, String label, boolean primary) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(context, 52));
        button.setTextColor(primary ? INK : PAPER);
        GradientDrawable shape = round(context, primary ? ACID : Color.TRANSPARENT, 16, primary ? ACID : LINE);
        button.setBackground(
                new RippleDrawable(ColorStateList.valueOf(Color.argb(50, 255, 255, 255)), shape, null));
        button.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    public static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(LINE);
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(context, 1)));
        return view;
    }

    public static Space space(Context context, int dp) {
        Space space = new Space(context);
        space.setLayoutParams(new LinearLayout.LayoutParams(1, dp(context, dp)));
        return space;
    }

    public static GradientDrawable round(
            Context context, int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        drawable.setStroke(dp(context, 1), strokeColor);
        return drawable;
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static void marginTop(View view, int dp) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        LinearLayout.LayoutParams params =
                raw instanceof LinearLayout.LayoutParams
                        ? (LinearLayout.LayoutParams) raw
                        : new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(view.getContext(), dp);
        view.setLayoutParams(params);
    }
}
