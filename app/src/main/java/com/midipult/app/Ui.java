package com.midipult.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Общая визуальная тема приложения в стиле панели MidiPult. */
final class Ui {
    static final int BG = 0xFF0B0E18;
    static final int BG2 = 0xFF11162A;
    static final int CARD = 0xFF151A2C;
    static final int CARD2 = 0xFF111526;
    static final int STROKE = 0xFF2A3146;
    static final int TEXT = 0xFFF1F4FF;
    static final int MUTED = 0xFF8C97B2;
    static final int ACCENT = 0xFF7C5CFF;
    static final int CYAN = 0xFF22D3EE;
    static final int DANGER = 0xFFFF5C7A;

    private Ui() {
    }

    static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }

    static GradientDrawable rounded(int color, float radius, int stroke, float strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(1, stroke);
        return drawable;
    }

    static GradientDrawable background() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{BG, BG2, 0xFF0D1020});
        drawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        return drawable;
    }

    static GradientDrawable cardBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{CARD, CARD2});
        drawable.setCornerRadius(18f * 3);
        drawable.setStroke(1, STROKE);
        return drawable;
    }

    static LinearLayout card(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
        box.setBackground(cardBackground());
        return box;
    }

    static TextView title(Context context, String text, int sizeSp) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(TEXT);
        view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        view.setIncludeFontPadding(false);
        return view;
    }

    static TextView muted(Context context, String text, int sizeSp) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(MUTED);
        view.setLineSpacing(dp(context, 2), 1f);
        return view;
    }

    static Button primary(Context context, String text) {
        Button button = base(context, text);
        button.setTextColor(Color.WHITE);
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ACCENT, 0xFF5B8CFF});
        drawable.setCornerRadius(dp(context, 14));
        button.setBackground(drawable);
        return button;
    }

    static Button ghost(Context context, String text) {
        Button button = base(context, text);
        button.setTextColor(TEXT);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0x22FFFFFF);
        drawable.setCornerRadius(dp(context, 14));
        drawable.setStroke(1, 0x44FFFFFF);
        button.setBackground(drawable);
        return button;
    }

    static Button icon(Context context, String text) {
        Button button = base(context, text);
        button.setTextColor(TEXT);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0x18FFFFFF);
        drawable.setCornerRadius(dp(context, 12));
        drawable.setStroke(1, 0x33FFFFFF);
        button.setBackground(drawable);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(context, 10), 0, dp(context, 10), 0);
        return button;
    }

    static Button danger(Context context, String text) {
        Button button = ghost(context, text);
        button.setTextColor(0xFFFF8AA0);
        return button;
    }

    private static Button base(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setMinHeight(dp(context, 44));
        button.setMinimumHeight(dp(context, 44));
        button.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        button.setStateListAnimator(null);
        return button;
    }

    static ImageView logo(Context context, int sizeDp) {
        ImageView image = new ImageView(context);
        int id = context.getResources().getIdentifier("ic_launcher", "drawable", context.getPackageName());
        if (id != 0) image.setImageResource(id);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(context, sizeDp), dp(context, sizeDp));
        image.setLayoutParams(params);
        GradientDrawable clip = new GradientDrawable();
        clip.setColor(0x22FFFFFF);
        clip.setCornerRadius(dp(context, 18));
        clip.setStroke(1, 0x33FFFFFF);
        image.setBackground(clip);
        image.setClipToOutline(true);
        return image;
    }

    static void applyInsets(Activity activity, View root, int horizontalDp) {
        Window window = activity.getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            final int side = dp(activity, horizontalDp);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(side, side + bars.top, side, dp(activity, 14) + bars.bottom);
                return insets;
            });
        } else {
            window.setStatusBarColor(BG);
            window.setNavigationBarColor(BG);
            root.setPadding(dp(activity, horizontalDp), dp(activity, 18),
                    dp(activity, horizontalDp), dp(activity, 14));
            root.setFitsSystemWindows(true);
        }
        root.setBackground(background());
    }

    static LinearLayout.LayoutParams lp(int width, int height, int topMarginDp, Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        if (topMarginDp != 0) params.topMargin = dp(context, topMarginDp);
        return params;
    }

    static View spacer(Context context, int heightDp) {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, heightDp)));
        return view;
    }
}
