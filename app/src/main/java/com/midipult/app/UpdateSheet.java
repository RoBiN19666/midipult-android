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
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

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
        card.addView(Ui.title(context, context.getString(R.string.update_title), 20));

        subtitle = Ui.muted(context, "", 12);
        subtitle.setPadding(0, Ui.dp(context, 6), 0, 0);
        card.addView(subtitle);

        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, Ui.dp(context, 12), 0, 0);
        MaxHeightScrollView bodyScroll = new MaxHeightScrollView(context, maxBodyHeight(context));
        bodyScroll.addView(body, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(bodyScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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

    void showChecking() {
        downloading = false;
        setCancelable(true);
        subtitle.setText(context.getString(R.string.update_checking));
        reset();
        body.addView(spinnerRow(context.getString(R.string.update_checking_hint)));
        addButton(Ui.ghost(context, context.getString(R.string.action_close)), this::dismiss);
    }

    void showUpToDate(String version) {
        downloading = false;
        setCancelable(true);
        subtitle.setText(context.getString(R.string.update_up_to_date));
        reset();
        body.addView(message(context.getString(R.string.update_latest, version)));
        body.addView(hint(context.getString(R.string.update_auto_hint)));
        addButton(Ui.ghost(context, context.getString(R.string.action_close)), this::dismiss);
    }

    void showCheckError(String error, Runnable retry) {
        downloading = false;
        setCancelable(true);
        subtitle.setText(context.getString(R.string.update_check_failed));
        reset();
        body.addView(message(context.getString(R.string.update_check_failed_hint)));
        if (!TextUtils.isEmpty(error)) body.addView(hint(error));
        addButton(Ui.primary(context, context.getString(R.string.action_retry)), retry);
        addButton(Ui.ghost(context, context.getString(R.string.action_close)), this::dismiss);
    }

    void showAvailable(final UpdateChecker.UpdateInfo info, Runnable download, Runnable later) {
        downloading = false;
        setCancelable(true);
        subtitle.setText(context.getString(R.string.update_available, info.version));
        reset();

        TextView notes = Ui.muted(context, compactNotes(info.body), 13);
        notes.setMaxLines(7);
        notes.setEllipsize(TextUtils.TruncateAt.END);
        body.addView(notes);

        StringBuilder meta = new StringBuilder();
        if (info.size > 0) meta.append(context.getString(R.string.update_size, size(info.size)));
        if (!info.digest.isEmpty()) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append("SHA-256 ").append(info.digest.substring(0, Math.min(12, info.digest.length())))
                    .append("…");
        }
        body.addView(hint(meta.toString()));

        addButton(Ui.primary(context, context.getString(R.string.update_download_install)), download);
        addButton(Ui.ghost(context, context.getString(R.string.update_later)), later);
    }

    void showDownloading(UpdateChecker.UpdateInfo info, Runnable cancel) {
        downloading = true;
        setCancelable(false);
        subtitle.setText(context.getString(R.string.update_downloading, info.version));
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
        addButton(Ui.ghost(context, context.getString(R.string.action_cancel)), cancel);
    }

    void setProgress(long downloaded, long total) {
        if (!downloading || progressBar == null || progressText == null) return;
        if (total > 0) {
            int percent = (int) Math.max(0, Math.min(100, downloaded * 100 / total));
            progressBar.setIndeterminate(false);
            progressBar.setProgress(percent);
            progressText.setText(context.getString(R.string.update_percent, percent,
                    size(downloaded), size(total)));
        } else {
            progressText.setText(context.getString(R.string.update_downloaded, size(downloaded)));
        }
    }

    void showDownloadError(String error, Runnable retry) {
        downloading = false;
        setCancelable(true);
        subtitle.setText(context.getString(R.string.update_download_failed));
        reset();
        body.addView(message(context.getString(R.string.update_download_failed_message)));
        if (!TextUtils.isEmpty(error)) body.addView(hint(error));
        addButton(Ui.primary(context, context.getString(R.string.action_retry)), retry);
        addButton(Ui.ghost(context, context.getString(R.string.action_close)), this::dismiss);
    }

    void showReady(File file, boolean verified, Runnable install, Runnable later) {
        downloading = false;
        setCancelable(true);
        subtitle.setText(context.getString(R.string.update_ready));
        reset();
        body.addView(message(context.getString(verified
                ? R.string.update_ready_verified
                : R.string.update_ready_unverified)));
        if (file != null) body.addView(hint(file.getName()));
        body.addView(hint(context.getString(R.string.update_installer_hint)));
        addButton(Ui.primary(context, context.getString(R.string.update_install)), install);
        addButton(Ui.ghost(context, context.getString(R.string.update_later)), later);
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
        button.setMinHeight(Ui.dp(context, 48));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (buttons.getChildCount() > 0) params.topMargin = Ui.dp(context, 8);
        buttons.addView(button, params);
    }

    private static int maxBodyHeight(Context context) {
        int screen = context.getResources().getDisplayMetrics().heightPixels;
        return Math.max(Ui.dp(context, 160), (int) (screen * 0.52f));
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

    private String compactNotes(String body) {
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
        return result.length() == 0
                ? context.getString(R.string.update_default_notes) : result.toString();
    }

    private String size(long bytes) {
        if (bytes <= 0) return "";
        if (bytes >= 1024L * 1024L) {
            return context.getString(R.string.size_mb, bytes / (1024.0 * 1024.0));
        }
        return context.getString(R.string.size_kb, bytes / 1024.0);
    }
}
