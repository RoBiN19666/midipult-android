package com.midipult.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
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

    static Drawable selectable(Context context) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true)
                && value.resourceId != 0) {
            try {
                return context.getDrawable(value.resourceId);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    static EditText field(Context context, String hint, String value) {
        EditText edit = new EditText(context);
        edit.setHint(hint);
        edit.setText(value == null ? "" : value);
        edit.setSingleLine(true);
        edit.setTextSize(16);
        edit.setTextColor(TEXT);
        edit.setHintTextColor(MUTED);
        edit.setInputType(InputType.TYPE_CLASS_TEXT);
        edit.setSelectAllOnFocus(true);
        edit.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        edit.setBackground(rounded(0xFF0E1322, dp(context, 12), STROKE, 1));
        return edit;
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

    /**
     * Полноэкранный режим для WebView: контент занимает всю ширину и низ,
     * сверху остаётся только место под статус-бар, снизу — навигационная панель.
     */
    static void edgeInsets(Activity activity, View root, final View content, final View bar) {
        Window window = activity.getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                content.setPadding(0, bars.top, 0, 0);
                bar.setPadding(bar.getPaddingLeft(), bar.getPaddingTop(),
                        bar.getPaddingRight(), bars.bottom);
                return insets;
            });
        } else {
            window.setStatusBarColor(BG);
            window.setNavigationBarColor(BG);
        }
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

    /** Нижняя карточка вместо стандартных системных диалогов. */
    static final class Sheet {
        private final Context context;
        private final Dialog dialog;
        private final LinearLayout body;
        private final LinearLayout buttons;

        Sheet(Context context, String title, String subtitle) {
            this(context, title, subtitle, 0);
        }

        /**
         * @param bottomOffsetPx сдвиг карточки вверх, чтобы она не перекрывала
         *                       нижнюю навигацию приложения
         */
        Sheet(Context context, String title, String subtitle, int bottomOffsetPx) {
            this.context = context;
            dialog = new Dialog(context);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setCanceledOnTouchOutside(true);

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(context, 20), dp(context, 18), dp(context, 20), dp(context, 16));
            card.setBackground(cardBackground());

            card.addView(Ui.title(context, title, 20));
            if (subtitle != null && !subtitle.isEmpty()) {
                TextView sub = Ui.muted(context, subtitle, 12);
                sub.setPadding(0, dp(context, 6), 0, 0);
                card.addView(sub);
            }

            body = new LinearLayout(context);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(0, dp(context, 12), 0, 0);
            card.addView(body);

            buttons = new LinearLayout(context);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setPadding(0, dp(context, 16), 0, 0);
            card.addView(buttons);

            FrameLayout wrapper = new FrameLayout(context);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int margin = dp(context, 10);
            params.setMargins(margin, margin, margin, margin + Math.max(0, bottomOffsetPx));
            wrapper.addView(card, params);
            dialog.setContentView(wrapper);

            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams attrs = window.getAttributes();
                attrs.dimAmount = 0.65f;
                attrs.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                window.setAttributes(attrs);
            }
        }

        Sheet add(View view) {
            body.addView(view, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return this;
        }

        Sheet message(String text) {
            TextView view = Ui.muted(context, text, 13);
            body.addView(view);
            return this;
        }

        Sheet row(String rowTitle, String subtitle, View.OnClickListener listener) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
            row.addView(Ui.title(context, rowTitle, 15));
            if (subtitle != null && !subtitle.isEmpty()) {
                TextView sub = Ui.muted(context, subtitle, 12);
                sub.setPadding(0, dp(context, 3), 0, 0);
                row.addView(sub);
            }
            Drawable ripple = Ui.selectable(context);
            if (ripple != null) row.setBackground(ripple);
            row.setOnClickListener(listener);
            body.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return this;
        }

        Sheet negative(String text) {
            Button button = Ui.ghost(context, text);
            button.setOnClickListener(v -> dismiss());
            place(button);
            return this;
        }

        Sheet positive(String text, final Runnable action) {
            Button button = Ui.primary(context, text);
            button.setOnClickListener(v -> {
                if (action != null) action.run();
            });
            place(button);
            return this;
        }

        Sheet danger(String text, final Runnable action) {
            Button button = Ui.danger(context, text);
            button.setOnClickListener(v -> {
                if (action != null) action.run();
            });
            place(button);
            return this;
        }

        Sheet cancelable(boolean value) {
            dialog.setCancelable(value);
            dialog.setCanceledOnTouchOutside(value);
            return this;
        }

        Sheet keyboard() {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                        | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            return this;
        }

        private void place(Button button) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, dp(context, 46), 1f);
            params.setMargins(dp(context, 4), 0, dp(context, 4), 0);
            buttons.addView(button, params);
        }

        void show() {
            dialog.show();
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        }

        void dismiss() {
            try {
                dialog.dismiss();
            } catch (Exception ignored) {
            }
        }
    }
}
