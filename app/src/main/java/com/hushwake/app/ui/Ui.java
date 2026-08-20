package com.hushwake.app.ui;

import android.app.AlertDialog;
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
import android.widget.ArrayAdapter;
import android.widget.Space;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

public final class Ui {
    public static final int INK_DEEP = Color.rgb(7, 9, 14);
    public static final int INK = Color.rgb(11, 14, 20);
    public static final int PANEL = Color.rgb(18, 23, 33);
    public static final int GLASS = Color.rgb(23, 29, 42);
    public static final int RAISED = Color.rgb(31, 41, 59);
    public static final int LINE = Color.rgb(42, 53, 72);
    public static final int PAPER = Color.rgb(246, 247, 250);
    public static final int MUTED = Color.rgb(142, 155, 174);
    public static final int ACID = Color.rgb(245, 176, 84);
    public static final int ACID_DARK = Color.rgb(235, 137, 8);
    public static final int ACID_SOFT = Color.rgb(49, 38, 23);
    public static final int WARM = Color.rgb(93, 214, 194);
    public static final int BLUE = Color.rgb(56, 189, 248);
    public static final int VIOLET = Color.rgb(139, 133, 255);
    public static final int DANGER = Color.rgb(255, 118, 108);

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
        TextView view = text(context, value, 11, MUTED, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLetterSpacing(.08f);
        return view;
    }

    public static Typeface display() {
        return Typeface.create("sans-serif-light", Typeface.NORMAL);
    }

    public static Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    public static Typeface bold() {
        return Typeface.create("sans-serif", Typeface.BOLD);
    }

    public static LinearLayout card(Context context, int color) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));
        card.setBackground(round(context, color, 24, LINE));
        card.setElevation(dp(context, 1));
        card.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    public static Button button(Context context, String label, boolean primary) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(bold());
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(context, 52));
        button.setTextColor(primary ? INK : PAPER);
        GradientDrawable shape =
                primary
                        ? gradient(context, ACID, ACID_DARK, 18, ACID)
                        : round(context, RAISED, 18, LINE);
        button.setBackground(
                new RippleDrawable(ColorStateList.valueOf(Color.argb(50, 255, 255, 255)), shape, null));
        button.setElevation(primary ? dp(context, 4) : 0f);
        button.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    public static TextView choice(Context context, String label, boolean selected) {
        TextView choice = text(context, label, 13, selected ? ACID : PAPER, medium());
        choice.setGravity(Gravity.CENTER);
        choice.setMinHeight(dp(context, 42));
        choice.setPadding(dp(context, 10), dp(context, 9), dp(context, 10), dp(context, 9));
        choice.setBackground(
                new RippleDrawable(
                        ColorStateList.valueOf(Color.argb(45, 255, 255, 255)),
                        round(
                                context,
                                selected ? ACID_SOFT : RAISED,
                                16,
                                selected ? ACID : LINE),
                        null));
        return choice;
    }

    public static void setChoiceSelected(TextView choice, boolean selected) {
        choice.setTextColor(selected ? ACID : PAPER);
        choice.setBackground(
                new RippleDrawable(
                        ColorStateList.valueOf(Color.argb(45, 255, 255, 255)),
                        round(
                                choice.getContext(),
                                selected ? ACID_SOFT : RAISED,
                                16,
                                selected ? ACID : LINE),
                        null));
    }

    public static void styleSwitch(Context context, Switch toggle) {
        int[][] states = {
            new int[] {android.R.attr.state_checked},
            new int[] {}
        };
        toggle.setThumbTintList(
                new ColorStateList(states, new int[] {PAPER, Color.rgb(126, 141, 162)}));
        toggle.setTrackTintList(new ColorStateList(states, new int[] {ACID, RAISED}));
        toggle.setShowText(false);
        toggle.setMinimumWidth(dp(context, 52));
    }

    public static TextView pill(Context context, String label, boolean selected) {
        TextView pill = text(context, label, 11, selected ? ACID : MUTED, medium());
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5));
        pill.setBackground(
                round(
                        context,
                        selected ? ACID_SOFT : GLASS,
                        999,
                        selected ? ACID : LINE));
        return pill;
    }

    public static Spinner spinner(Context context, String[] values) {
        Spinner spinner = new Spinner(context);
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, values) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        TextView view =
                                (TextView) super.getView(position, convertView, parent);
                        styleSpinnerText(view, PAPER);
                        return view;
                    }

                    @Override
                    public View getDropDownView(
                            int position, View convertView, ViewGroup parent) {
                        TextView view =
                                (TextView) super.getDropDownView(position, convertView, parent);
                        styleSpinnerText(view, PAPER);
                        view.setBackgroundColor(RAISED);
                        return view;
                    }
                };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setPopupBackgroundDrawable(round(context, RAISED, 12, LINE));
        spinner.setBackgroundTintList(ColorStateList.valueOf(ACID));
        spinner.setMinimumHeight(dp(context, 52));
        return spinner;
    }

    private static void styleSpinnerText(TextView view, int color) {
        view.setTextColor(color);
        view.setTextSize(15);
        view.setPadding(dp(view.getContext(), 12), dp(view.getContext(), 12), dp(view.getContext(), 12), dp(view.getContext(), 12));
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

    public static GradientDrawable gradient(
            Context context, int startColor, int endColor, int radiusDp, int strokeColor) {
        GradientDrawable drawable =
                new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[] {startColor, endColor});
        drawable.setCornerRadius(dp(context, radiusDp));
        drawable.setStroke(dp(context, 1), strokeColor);
        return drawable;
    }

    public static GradientDrawable pageBackground(Context context) {
        return gradient(context, INK, Color.rgb(7, 16, 23), 0, INK);
    }

    public static void styleDialog(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow()
                    .setBackgroundDrawable(round(dialog.getContext(), PANEL, 24, LINE));
        }
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ACID);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTypeface(bold());
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(MUTED);
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(MUTED);
        }
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
