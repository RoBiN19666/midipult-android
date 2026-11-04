package com.midipult.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
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

/** Основной экран: полноэкранная панель, нижняя навигация и меню приложения. */
public class MainActivity extends Activity {
    private static final int FILE_CHOOSER = 77;

    private FrameLayout content;
    private PullRefresh pull;
    private WebView webView;
    private ProgressBar progress;
    private LinearLayout bottomNav;
    private LinearLayout errorView;
    private TextView errorText;
    private Station station;
    private android.webkit.ValueCallback<Uri[]> fileCallback;
    private ProgressDialog downloadDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        station = StationStore.active(this);
        if (station == null) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        View root = build();
        setContentView(root);
        Ui.edgeInsets(this, root, content, bottomNav);
        load(station);
        UpdateChecker.autoCheck(this, (info, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (info != null) showAppUpdate(info);
        });
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        content = new FrameLayout(this);
        content.setBackgroundColor(Ui.BG);

        pull = new PullRefresh(this);
        webView = new PageWebView(this);
        webView.setBackgroundColor(Ui.BG);
        pull.content(webView);
        setupWebView();
        pull.setListener(this::reload);
        content.addView(pull, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        progress.setVisibility(View.GONE);
        content.addView(progress, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 2), Gravity.TOP));

        errorView = buildError();
        FrameLayout.LayoutParams errorParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        errorParams.gravity = Gravity.CENTER;
        int margin = Ui.dp(this, 18);
        errorParams.setMargins(margin, margin, margin, margin);
        content.addView(errorView, errorParams);

        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        bottomNav = buildNav();
        root.addView(bottomNav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private LinearLayout buildError() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24));
        box.setBackground(Ui.cardBackground());
        box.setVisibility(View.GONE);
        errorText = Ui.title(this, "", 16);
        errorText.setGravity(Gravity.CENTER);
        box.addView(errorText);
        Button retry = Ui.primary(this, "Повторить");
        retry.setOnClickListener(v -> {
            hideError();
            load(station);
        });
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 44));
        retryParams.topMargin = Ui.dp(this, 14);
        box.addView(retry, retryParams);
        return box;
    }

    private LinearLayout buildNav() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(0xFF0C1120);

        View line = new View(this);
        line.setBackgroundColor(Ui.STROKE);
        column.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 1))));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(navItem("▣", "Панель", true, null), weight());
        row.addView(navItem("☰", "Станции", false, v -> openStations()), weight());
        row.addView(navItem("⋯", "Меню", false, v -> showMenu()), weight());
        column.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 58)));
        return column;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    }

    private View navItem(String icon, String label, boolean active, View.OnClickListener click) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        int color = active ? Ui.ACCENT : Ui.MUTED;

        TextView glyph = new TextView(this);
        glyph.setText(icon);
        glyph.setTextSize(17);
        glyph.setTextColor(color);
        glyph.setGravity(Gravity.CENTER);

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(11);
        text.setTextColor(color);
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, Ui.dp(this, 3), 0, 0);

        item.addView(glyph);
        item.addView(text);
        if (click != null) {
            item.setOnClickListener(click);
            android.graphics.drawable.Drawable ripple = Ui.selectable(this);
            if (ripple != null) item.setBackground(ripple);
        }
        return item;
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
                if (pull != null) pull.finishRefresh();
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
        hideError();
        webView.loadUrl(target.url);
    }

    private void reload() {
        if (webView == null) return;
        hideError();
        String current = webView.getUrl();
        if (current == null && station != null) {
            webView.loadUrl(station.url);
        } else {
            webView.reload();
        }
    }

    private void openStations() {
        startActivity(new Intent(this, SetupActivity.class)
                .putExtra(SetupActivity.EXTRA_MANAGE, true));
    }

    private void showMenu() {
        String stationLabel = station == null ? ""
                : (station.name == null || station.name.isEmpty() ? station.ip : station.name);
        String stationUrl = station == null ? "" : station.url;
        final Ui.Sheet sheet = new Ui.Sheet(this, "MidiPult", "Управление станцией");
        sheet.row("Станции и подключение", "Добавить, переименовать или удалить",
                v -> {
                    sheet.dismiss();
                    openStations();
                });
        sheet.row("Переименовать станцию", stationLabel, v -> {
            sheet.dismiss();
            renameCurrent();
        });
        sheet.row("Проверить обновление", "Текущая версия " + BuildConfig.VERSION_NAME, v -> {
            sheet.dismiss();
            checkAppUpdate();
        });
        sheet.row("Открыть в браузере", stationUrl, v -> {
            sheet.dismiss();
            openExternal(stationUrl);
        });
        sheet.negative("Закрыть");
        sheet.show();
    }

    private void openExternal(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    private void renameCurrent() {
        if (station == null) return;
        final Ui.Sheet sheet = new Ui.Sheet(this, "Название станции",
                "Так станция будет видна в списке и в меню");
        final android.widget.EditText input = Ui.field(this, "Например, «Кухня»", station.name);
        sheet.add(input);
        sheet.keyboard();
        sheet.negative("Отмена");
        sheet.positive("Сохранить", () -> {
            StationStore.rename(this, station, input.getText().toString());
            Station updated = StationStore.active(this);
            if (updated != null) station = updated;
            Toast.makeText(this, "Название сохранено", Toast.LENGTH_SHORT).show();
            sheet.dismiss();
        });
        sheet.show();
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
        if (pull != null) pull.finishRefresh();
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
        if (active == null) {
            // Станцию удалили из списка — возвращаемся к выбору.
            if (!isFinishing()) {
                startActivity(new Intent(this, SetupActivity.class));
                finish();
            }
            return;
        }
        if (station == null || !active.url.equals(station.url)) {
            load(active);
        } else {
            // Имя могли изменить в экране «Станции» — обновляем данные.
            station = active;
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
        if (pull != null) pull.finishRefresh();
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    /**
     * WebView по умолчанию запрещает родителю перехватывать жесты, из-за чего
     * перестаёт работать обновление «потянуть вниз».
     */
    private static final class PageWebView extends WebView {
        PageWebView(Context context) {
            super(context);
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            super.requestDisallowInterceptTouchEvent(false);
        }
    }
}
