package com.midipult.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Основной экран: WebView с панелью, переключение станций и обновление APK. */
public class MainActivity extends Activity {
    private static final int FILE_CHOOSER = 77;
    private static final int BG = 0xFF0B0E18;
    private static final int BAR = 0xFF111624;
    private static final int TEXT = 0xFFEDF1FA;
    private static final int MUTED = 0xFF8B95AD;
    private static final int ACCENT = 0xFF7C5CFF;

    private WebView webView;
    private TextView title;
    private ProgressBar progress;
    private LinearLayout errorView;
    private TextView errorText;
    private Station station;
    private android.webkit.ValueCallback<Uri[]> fileCallback;
    private ProgressDialog downloadDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        station = StationStore.active(this);
        if (station == null) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        View root = build();
        setContentView(root);
        Ui.applyInsets(this, root, 10);
        load(station);
        UpdateChecker.autoCheck(this, (info, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (info != null) showAppUpdate(info);
        });
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));

        bar.addView(Ui.logo(this, 34), new LinearLayout.LayoutParams(
                Ui.dp(this, 34), Ui.dp(this, 34)));

        title = Ui.title(this, "MidiPult", 16);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = Ui.dp(this, 10);
        bar.addView(title, titleParams);

        Button stations = Ui.icon(this, "☰");
        stations.setTextSize(18);
        stations.setContentDescription("Станции");
        stations.setOnClickListener(v -> chooseStation());
        bar.addView(stations, iconParams(42));

        Button rename = Ui.icon(this, "✎");
        rename.setTextSize(15);
        rename.setContentDescription("Переименовать станцию");
        rename.setOnClickListener(v -> renameCurrent());
        bar.addView(rename, iconParams(42));

        Button refresh = Ui.icon(this, "↻");
        refresh.setTextSize(18);
        refresh.setContentDescription("Обновить страницу");
        refresh.setOnClickListener(v -> {
            hideError();
            webView.reload();
        });
        bar.addView(refresh, iconParams(42));

        Button apk = Ui.icon(this, "APK");
        apk.setTextSize(10);
        apk.setContentDescription("Проверить обновление приложения");
        apk.setOnClickListener(v -> checkAppUpdate());
        bar.addView(apk, iconParams(48));
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 54)));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 2)));

        FrameLayout body = new FrameLayout(this);
        body.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 8));
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        webView = new WebView(this);
        webView.setBackgroundColor(Ui.BG);
        webView.setBackground(Ui.cardBackground());
        webView.setClipToOutline(true);
        body.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setupWebView();

        errorView = new LinearLayout(this);
        errorView.setOrientation(LinearLayout.VERTICAL);
        errorView.setGravity(Gravity.CENTER);
        errorView.setPadding(Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24));
        errorView.setBackground(Ui.cardBackground());
        errorView.setVisibility(View.GONE);
        errorText = Ui.title(this, "", 16);
        errorText.setGravity(Gravity.CENTER);
        errorView.addView(errorText);
        Button retry = Ui.primary(this, "Повторить");
        retry.setOnClickListener(v -> {
            hideError();
            load(station);
        });
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 44));
        retryParams.topMargin = Ui.dp(this, 14);
        errorView.addView(retry, retryParams);
        FrameLayout.LayoutParams errorParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        errorParams.gravity = Gravity.CENTER;
        errorParams.setMargins(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18));
        body.addView(errorView, errorParams);
        return root;
    }

    private LinearLayout.LayoutParams iconParams(int dp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Ui.dp(this, dp), Ui.dp(this, 42));
        params.leftMargin = Ui.dp(this, 4);
        return params;
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString(settings.getUserAgentString() + " MidiPultAndroid/" + BuildConfig.VERSION_NAME);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    showError("Колонка недоступна.\n" + error.getDescription());
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                showError("Колонка недоступна.\n" + description);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, android.webkit.ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(intent, "Выберите файл"), FILE_CHOOSER);
                    return true;
                } catch (Exception exc) {
                    fileCallback = null;
                    return false;
                }
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.addRequestHeader("User-Agent", userAgent);
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null) request.addRequestHeader("Cookie", cookie);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalFilesDir(MainActivity.this,
                        Environment.DIRECTORY_DOWNLOADS, "midipult-" + System.currentTimeMillis());
                DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (manager != null) {
                    manager.enqueue(request);
                    Toast.makeText(this, "Скачивание началось", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception exc) {
                Toast.makeText(this, "Скачивание: " + exc.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean handleUrl(Uri uri) {
        if (uri == null) return false;
        String host = uri.getHost();
        if (host == null) return false;
        if (host.equalsIgnoreCase(station.ip) || host.equalsIgnoreCase("localhost")
                || host.equalsIgnoreCase("127.0.0.1")) {
            return false;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ignored) {
        }
        return true;
    }

    private void load(Station target) {
        station = target;
        title.setText(target.name == null || target.name.isEmpty() ? target.ip : target.name);
        hideError();
        webView.loadUrl(target.url);
    }

    private void chooseStation() {
        List<Station> stations = StationStore.load(this);
        if (stations.isEmpty()) {
            startActivity(new Intent(this, SetupActivity.class).putExtra(SetupActivity.EXTRA_MANAGE, true));
            return;
        }
        final List<String> labels = new ArrayList<>();
        for (Station item : stations) labels.add(item.label());
        new AlertDialog.Builder(this)
                .setTitle("Станции")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    Station picked = stations.get(which);
                    StationStore.setActive(this, picked);
                    load(picked);
                })
                .setNeutralButton("Найти ещё", (dialog, which) ->
                        startActivity(new Intent(this, SetupActivity.class)
                                .putExtra(SetupActivity.EXTRA_MANAGE, true)))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void renameCurrent() {
        if (station == null) return;
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setText(station.name == null ? "" : station.name);
        input.setHint("Например, «Кухня»");
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("Название станции")
                .setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    StationStore.rename(this, station, input.getText().toString());
                    Station updated = StationStore.active(this);
                    if (updated != null) {
                        station = updated;
                        title.setText(updated.name == null || updated.name.isEmpty()
                                ? updated.ip : updated.name);
                    }
                    Toast.makeText(this, "Название сохранено", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void checkAppUpdate() {
        Toast.makeText(this, "Проверяю обновление приложения…", Toast.LENGTH_SHORT).show();
        UpdateChecker.check(this, true, (info, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (error != null) {
                Toast.makeText(this, "Обновление: " + error, Toast.LENGTH_LONG).show();
            } else if (info == null) {
                Toast.makeText(this, "Обновлений приложения нет", Toast.LENGTH_SHORT).show();
            } else {
                showAppUpdate(info);
            }
        });
    }

    private void showAppUpdate(UpdateChecker.UpdateInfo info) {
        if (isFinishing() || isDestroyed()) return;
        String notes = info.body == null ? "" : info.body.trim();
        if (notes.length() > 1200) notes = notes.substring(0, 1200) + "…";
        new AlertDialog.Builder(this)
                .setTitle("Обновление приложения " + info.version)
                .setMessage(notes.isEmpty() ? "Доступна новая версия APK." : notes)
                .setNegativeButton("Позже", null)
                .setPositiveButton("Скачать и установить", (dialog, which) -> downloadApk(info))
                .show();
    }

    private void downloadApk(UpdateChecker.UpdateInfo info) {
        downloadDialog = new ProgressDialog(this);
        downloadDialog.setMessage("Скачиваю APK…");
        downloadDialog.setCancelable(false);
        downloadDialog.show();
        UpdateChecker.downloadAndInstall(this, info, (file, error) -> {
            try {
                if (downloadDialog != null) downloadDialog.dismiss();
            } catch (Exception ignored) {
            }
            downloadDialog = null;
            if (error != null) {
                Toast.makeText(this, "APK: " + error, Toast.LENGTH_LONG).show();
                return;
            }
            UpdateChecker.install(this, file);
        });
    }

    private void showError(String message) {
        progress.setVisibility(View.GONE);
        errorText.setText(message);
        errorView.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        errorView.setVisibility(View.GONE);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        Station active = StationStore.active(this);
        if (active != null && station != null && !active.url.equals(station.url)) {
            load(active);
        }
    }

    @Override
    public void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCallback = null;
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
