package com.midipult.app;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;

/** Кастомный экран обновления APK: проверка, загрузка с прогрессом, ошибки и установка. */
final class UpdateSheet {
    private final Context context;
    private final Dialog dialog;
    private final TextView subtitle;
    private final LinearLayout body;
    private final LinearLayout buttons;

    private ProgressBar progressBar;
    private TextView progressText;
    private boolean downloading;
    private boolean dismissed;

    UpdateSheet(Context context, int bottomOffsetPx) {
        this.context = context;
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(context, 20), Ui.dp(context, 18),
                Ui.dp(context, 20), Ui.dp(context, 16));
        card.setBackground(Ui.cardBackground());
        card.addView(Ui.title(context, "Обновление MidiPult", 20));

        subtitle = Ui.muted(context, "", 12);
        subtitle.setPadding(0, Ui.dp(context, 6), 0, 0);
        card.addView(subtitle);

        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, Ui.dp(context, 12), 0, 0);
        card.addView(body);

        buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.VERTICAL);
        buttons.setPadding(0, Ui.dp(context, 16), 0, 0);
        card.addView(buttons);

        FrameLayout wrapper = new FrameLayout(context);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int margin = Ui.dp(context, 10);
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

    void show() {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    void dismiss() {
        dismissed = true;
        try {
            dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    boolean isDismissed() {
        return dismissed;
    }

    boolean isShowing() {
        return dialog.isShowing();
    }

    void showChecking() {
        downloading = false;
        setCancelable(true);
        subtitle.setText("Проверяю обновление…");
        reset();
        body.addView(spinnerRow("Смотрю новые версии на GitHub…"));
        addButton(Ui.ghost(context, "Закрыть"), this::dismiss);
    }

    void showUpToDate(String version) {
        downloading = false;
        setCancelable(true);
        subtitle.setText("Обновлений нет");
        reset();
        body.addView(message("Установлена последняя версия — " + version + "."));
        body.addView(hint("Новые версии проверяются автоматически раз в сутки."));
        addButton(Ui.ghost(context, "Закрыть"), this::dismiss);
    }

    void showCheckError(String error, Runnable retry) {
        downloading = false;
        setCancelable(true);
        subtitle.setText("Не удалось проверить обновление");
        reset();
        body.addView(message("Проверьте подключение к интернету и попробуйте ещё раз."));
        if (!TextUtils.isEmpty(error)) body.addView(hint(error));
        addButton(Ui.primary(context, "Повторить"), retry);
        addButton(Ui.ghost(context, "Закрыть"), this::dismiss);
    }

    void showAvailable(final UpdateChecker.UpdateInfo info, Runnable download, Runnable later) {
        downloading = false;
        setCancelable(true);
        subtitle.setText("Доступна версия " + info.version);
        reset();

        TextView notes = Ui.muted(context, compactNotes(info.body), 13);
        notes.setMaxLines(7);
        notes.setEllipsize(TextUtils.TruncateAt.END);
        body.addView(notes);

        StringBuilder meta = new StringBuilder();
        if (info.size > 0) meta.append("Размер ").append(size(info.size));
        if (!info.digest.isEmpty()) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append("SHA-256 ").append(info.digest.substring(0, Math.min(12, info.digest.length())))
                    .append("…");
        }
        body.addView(hint(meta.toString()));

        addButton(Ui.primary(context, "Скачать и установить"), download);
        addButton(Ui.ghost(context, "Позже"), later);
    }

    void showDownloading(UpdateChecker.UpdateInfo info) {
        downloading = true;
        setCancelable(false);
        subtitle.setText("Скачиваю версию " + info.version);
        reset();

        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(Ui.ACCENT));
        progressBar.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        progressBar.setMax(100);
        body.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 6)));

        progressText = Ui.muted(context, "0%", 12);
        progressText.setPadding(0, Ui.dp(context, 10), 0, 0);
        body.addView(progressText);
    }

    void setProgress(long downloaded, long total) {
        if (!downloading || progressBar == null || progressText == null) return;
        if (total > 0) {
            int percent = (int) Math.max(0, Math.min(100, downloaded * 100 / total));
            progressBar.setIndeterminate(false);
            progressBar.setProgress(percent);
            progressText.setText(percent + "% · " + size(downloaded) + " из " + size(total));
        } else {
            progressText.setText("Скачано " + size(downloaded));
        }
    }

    void showDownloadError(String error, Runnable retry) {
        downloading = false;
        setCancelable(true);
        subtitle.setText("Загрузка не удалась");
        reset();
        body.addView(message("Не удалось скачать обновление."));
        if (!TextUtils.isEmpty(error)) body.addView(hint(error));
        addButton(Ui.primary(context, "Повторить"), retry);
        addButton(Ui.ghost(context, "Закрыть"), this::dismiss);
    }

    void showReady(File file, Runnable install, Runnable later) {
        downloading = false;
        setCancelable(true);
        subtitle.setText("Готово к установке");
        reset();
        body.addView(message("APK скачан и проверен по SHA-256."));
        if (file != null) body.addView(hint(file.getName()));
        body.addView(hint("Откроется системный установщик — подтвердите установку."));
        addButton(Ui.primary(context, "Установить"), install);
        addButton(Ui.ghost(context, "Позже"), later);
    }

    private void setCancelable(boolean value) {
        dialog.setCancelable(value);
        dialog.setCanceledOnTouchOutside(value);
    }

    private void reset() {
        body.removeAllViews();
        buttons.removeAllViews();
        progressBar = null;
        progressText = null;
    }

    private View spinnerRow(String text) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ProgressBar spinner = new ProgressBar(context);
        spinner.setIndeterminate(true);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.ACCENT));
        row.addView(spinner, new LinearLayout.LayoutParams(
                Ui.dp(context, 22), Ui.dp(context, 22)));
        TextView label = Ui.muted(context, text, 13);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.leftMargin = Ui.dp(context, 10);
        row.addView(label, params);
        return row;
    }

    private View message(String text) {
        TextView view = Ui.muted(context, text, 13);
        view.setLineSpacing(Ui.dp(context, 3), 1f);
        return view;
    }

    private View hint(String text) {
        if (TextUtils.isEmpty(text)) {
            View spacer = new View(context);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(0, Ui.dp(context, 2)));
            return spacer;
        }
        TextView view = Ui.muted(context, text, 12);
        view.setTextColor(0xFF6F7A93);
        view.setPadding(0, Ui.dp(context, 8), 0, 0);
        return view;
    }

    private void addButton(Button button, final Runnable action) {
        button.setOnClickListener(v -> {
            if (action != null) action.run();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 48));
        if (buttons.getChildCount() > 0) params.topMargin = Ui.dp(context, 8);
        buttons.addView(button, params);
    }

    private static String compactNotes(String body) {
        String notes = body == null ? "" : body.trim();
        String[] lines = notes.split("\n");
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (result.length() > 0) result.append('\n');
            result.append(trimmed);
            if (result.length() > 420) break;
        }
        if (result.length() > 420) result.setLength(420);
        return result.length() == 0 ? "Доступна новая версия приложения." : result.toString();
    }

    private static String size(long bytes) {
        if (bytes <= 0) return "";
        if (bytes >= 1024L * 1024L) {
            return String.format(Locale.US, "%.1f МБ", bytes / (1024.0 * 1024.0));
        }
        return String.format(Locale.US, "%.0f КБ", bytes / 1024.0);
    }
}
