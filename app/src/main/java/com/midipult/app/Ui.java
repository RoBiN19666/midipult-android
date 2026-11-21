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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Общая визуальная тема приложения в стиле панели MidiPult. */
final class Ui {
    static final int BG = 0xFF07090F;
    static final int BG2 = 0xFF101624;
    static final int CARD = 0xFF151C2B;
    static final int CARD2 = 0xFF101622;
    static final int STROKE = 0x24FFFFFF;
    static final int TEXT = 0xFFF7F9FF;
    static final int MUTED = 0xFF9099AD;
    static final int ACCENT = 0xFF8B5CF6;
    static final int ACCENT2 = 0xFF22D3EE;
    static final int SUCCESS = 0xFF34D399;
    static final int DANGER = 0xFFFB7185;

    private Ui() {
    }

    static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }

    static int dp(int value) {
        return Math.round(android.content.res.Resources.getSystem()
                .getDisplayMetrics().density * value);
    }

    static GradientDrawable rounded(int color, float radius, int stroke, float strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) {
            drawable.setStroke(Math.max(1, Math.round(strokeWidth)), stroke);
        }
        return drawable;
    }

    static GradientDrawable background() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0B1220, 0xFF0E1526, 0xFF121A2A});
        drawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        return drawable;
    }

    static GradientDrawable cardBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{CARD, CARD2});
        drawable.setCornerRadius(dp(24));
        drawable.setStroke(Math.max(1, dp(1)), 0x1FFFFFFF);
        return drawable;
    }

    static GradientDrawable gradient(int start, int end, int radiusPx) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, new int[]{start, end});
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    static ImageView iconView(Context context, int res, int color, int sizeDp) {
        ImageView view = new ImageView(context);
        view.setImageResource(res);
        view.setColorFilter(color);
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int size = dp(context, sizeDp);
        view.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return view;
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

    static FrameLayout iconButton(Context context, int iconRes, int tint) {
        FrameLayout box = new FrameLayout(context);
        box.setBackground(rounded(0x18FFFFFF, dp(context, 14), 0x24FFFFFF, 1));
        box.setClickable(true);
        box.setFocusable(true);
        ImageView icon = iconView(context, iconRes, tint, 22);
        box.addView(icon, new FrameLayout.LayoutParams(
                dp(context, 22), dp(context, 22), Gravity.CENTER));
        Drawable ripple = selectable(context);
        if (ripple != null) box.setForeground(ripple);
        return box;
    }

    private static Button base(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setMinHeight(dp(context, 44));
        button.setMinimumHeight(dp(context, 44));
        button.setPadding(dp(context, 16), dp(context, 7), dp(context, 16), 0);
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

    /**
     * Полноэкранный режим для WebView: контент занимает всю ширину и низ,
     * сверху остаётся только место под статус-бар, снизу — навигационная панель.
     */
    static void edgeInsets(Activity activity, View root, final View content, final View bar) {
        Window window = activity.getWindow();
        if (Build.VERSION.SDK_INT >= 29) {
            window.setNavigationBarContrastEnforced(false);
            window.setNavigationBarDividerColor(Color.TRANSPARENT);
        }
        window.setBackgroundDrawable(background());
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                content.setPadding(0, bars.top, 0, 0);
                bar.setPadding(bar.getPaddingLeft(), bar.getPaddingTop(),
                        bar.getPaddingRight(), bar.getPaddingBottom() + bars.bottom);
                return insets;
            });
        } else {
            window.setStatusBarColor(BG2);
            window.setNavigationBarColor(BG2);
        }
    }

    /**
     * Экран с нижней навигацией: контент получает боковые отступы и учитывает
     * статус-бар, панель навигации — системный низ.
     */
    static void screenInsets(Activity activity, View root, final View content, final View bar,
                             final int sideDp, final int topDp, final int bottomDp) {
        Window window = activity.getWindow();
        if (Build.VERSION.SDK_INT >= 29) {
            window.setNavigationBarContrastEnforced(false);
            window.setNavigationBarDividerColor(Color.TRANSPARENT);
        }
        window.setBackgroundDrawable(background());
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                int side = dp(activity, sideDp);
                content.setPadding(side, dp(activity, topDp) + bars.top,
                        side, dp(activity, bottomDp));
                bar.setPadding(bar.getPaddingLeft(), bar.getPaddingTop(),
                        bar.getPaddingRight(), bar.getPaddingBottom() + bars.bottom);
                return insets;
            });
        } else {
            window.setStatusBarColor(BG2);
            window.setNavigationBarColor(BG2);
            int side = dp(activity, sideDp);
            content.setPadding(side, dp(activity, topDp), side, dp(activity, bottomDp));
        }
        root.setBackground(background());
    }

    /** Нижняя навигация приложения: «Панель · Колонки · Меню». */
    interface Nav {
        void onItem(int index);
    }

    static LinearLayout navBar(Context context, int activeIndex, final Nav nav) {
        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 10));
        outer.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 6), dp(context, 6), dp(context, 6), dp(context, 6));
        row.setBackground(rounded(0xF2101622, dp(context, 26), 0x24FFFFFF, 1));
        row.setElevation(dp(context, 12));
        int[] icons = {R.drawable.ic_nav_panel, R.drawable.ic_nav_stations, R.drawable.ic_nav_menu};
        String[] labels = {context.getString(R.string.nav_panel),
                context.getString(R.string.nav_columns),
                context.getString(R.string.nav_menu)};
        for (int i = 0; i < icons.length; i++) {
            row.addView(navItem(context, icons[i], labels[i], i == activeIndex, i, nav),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        row.setMinimumHeight(dp(context, 60));
        outer.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return outer;
    }

    private static View navItem(Context context, int iconRes, String label, boolean active,
                                final int index, final Nav nav) {
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(context, 6), dp(context, 6), dp(context, 6), dp(context, 5));
        int color = active ? Color.WHITE : MUTED;
        if (active) {
            item.setBackground(rounded(0x338B5CF6, dp(context, 20), 0x558B5CF6, 1));
        }

        ImageView icon = iconView(context, iconRes, color, 22);
        item.addView(icon, new LinearLayout.LayoutParams(dp(context, 22), dp(context, 22)));

        TextView text = new TextView(context);
        text.setText(label);
        text.setTextSize(10);
        text.setTextColor(color);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, dp(context, 3), 0, 0);
        item.addView(text);

        item.setOnClickListener(v -> {
            if (nav != null) nav.onItem(index);
        });
        if (!active) {
            Drawable ripple = selectable(context);
            if (ripple != null) item.setBackground(ripple);
        }
        return item;
    }

    static LinearLayout.LayoutParams lp(int width, int height, int topMarginDp, Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        if (topMarginDp != 0) params.topMargin = dp(context, topMarginDp);
        return params;
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
            card.setPadding(dp(context, 22), dp(context, 12), dp(context, 22), dp(context, 18));
            card.setBackground(cardBackground());

            View handle = new View(context);
            handle.setBackground(rounded(0x44FFFFFF, dp(context, 2), 0, 0));
            LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(
                    dp(context, 36), dp(context, 4));
            handleParams.gravity = Gravity.CENTER_HORIZONTAL;
            handleParams.bottomMargin = dp(context, 14);
            card.addView(handle, handleParams);

            card.addView(Ui.title(context, title, 21));
            if (subtitle != null && !subtitle.isEmpty()) {
                TextView sub = Ui.muted(context, subtitle, 12);
                sub.setPadding(0, dp(context, 5), 0, 0);
                card.addView(sub);
            }

            body = new LinearLayout(context);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(0, dp(context, 14), 0, 0);
            MaxHeightScrollView bodyScroll = new MaxHeightScrollView(context,
                    Math.max(dp(context, 120),
                            (int) (context.getResources().getDisplayMetrics().heightPixels * 0.5f)));
            bodyScroll.addView(body, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            card.addView(bodyScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            buttons = new LinearLayout(context);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setPadding(0, dp(context, 14), 0, 0);
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
                window.setWindowAnimations(R.style.SheetAnimation);
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
            return item(0, rowTitle, subtitle, listener);
        }

        Sheet item(int iconRes, String rowTitle, String subtitle, View.OnClickListener listener) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(context, 10), dp(context, 10), dp(context, 8), dp(context, 10));

            if (iconRes != 0) {
                FrameLayout tile = new FrameLayout(context);
                tile.setBackground(rounded(0x1F8B5CF6, dp(context, 13), 0x338B5CF6, 1));
                ImageView icon = Ui.iconView(context, iconRes, 0xFFC4B5FD, 22);
                FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                        dp(context, 22), dp(context, 22), Gravity.CENTER);
                tile.addView(icon, iconParams);
                LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(
                        dp(context, 42), dp(context, 42));
                tileParams.rightMargin = dp(context, 12);
                row.addView(tile, tileParams);
            }

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.addView(Ui.title(context, rowTitle, 15));
            if (subtitle != null && !subtitle.isEmpty()) {
                TextView sub = Ui.muted(context, subtitle, 12);
                sub.setPadding(0, dp(context, 2), 0, 0);
                texts.addView(sub);
            }
            row.addView(texts, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageView chevron = Ui.iconView(context, R.drawable.ic_chevron, MUTED, 18);
            LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(
                    dp(context, 18), dp(context, 18));
            chevronParams.leftMargin = dp(context, 8);
            row.addView(chevron, chevronParams);

            Drawable ripple = Ui.selectable(context);
            if (ripple != null) row.setBackground(ripple);
            row.setOnClickListener(listener);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(context, 2);
            body.addView(row, params);
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
            button.setMinHeight(dp(context, 46));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
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

    private static final class MaxHeightScrollView extends ScrollView {
        private final int maxHeight;

        MaxHeightScrollView(Context context, int maxHeight) {
            super(context);
            this.maxHeight = maxHeight;
            setFillViewport(false);
            setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (getMeasuredHeight() > maxHeight) {
                setMeasuredDimension(getMeasuredWidth(), maxHeight);
            }
        }
    }
}
