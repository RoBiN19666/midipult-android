package com.midipult.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
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
    private static final long LOAD_TIMEOUT_MS = 25000L;

    private FrameLayout content;
    private PullRefresh pull;
    private WebView webView;
    private ProgressBar progress;
    private LinearLayout bottomNav;
    private LinearLayout errorView;
    private TextView errorTitle;
    private TextView errorMessage;
    private TextView errorDetails;
    private Station station;
    private android.webkit.ValueCallback<Uri[]> fileCallback;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable loadTimeout = this::onLoadTimeout;
    private boolean errorShown;

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
        bottomNav.post(() -> UpdateChecker.autoCheck(this, (info, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (info == null) return;
            showUpdateAvailable(new UpdateSheet(this, navHeight()), info);
        }));
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
        errorView.setClickable(true);
        content.addView(errorView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        bottomNav = buildNav();
        root.addView(bottomNav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    /** Полноэкранная заглушка вместо стандартной страницы ошибки WebView. */
    private LinearLayout buildError() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Ui.BG);
        box.setPadding(Ui.dp(this, 28), Ui.dp(this, 28), Ui.dp(this, 28), Ui.dp(this, 28));
        box.setVisibility(View.GONE);

        TextView badge = new TextView(this);
        badge.setText("!");
        badge.setTextSize(30);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setTextColor(0xFFFFFFFF);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Ui.ACCENT);
        circle.setStroke(Math.max(1, Ui.dp(this, 1)), 0x55FFFFFF);
        badge.setBackground(circle);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 76), Ui.dp(this, 76));
        badgeParams.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(badge, badgeParams);

        errorTitle = Ui.title(this, "Панель недоступна", 21);
        errorTitle.setGravity(Gravity.CENTER);
        errorTitle.setPadding(0, Ui.dp(this, 18), 0, 0);
        box.addView(errorTitle);

        errorMessage = Ui.muted(this, "", 14);
        errorMessage.setGravity(Gravity.CENTER);
        errorMessage.setLineSpacing(Ui.dp(this, 3), 1f);
        errorMessage.setPadding(0, Ui.dp(this, 10), 0, 0);
        box.addView(errorMessage);

        errorDetails = Ui.muted(this, "", 12);
        errorDetails.setGravity(Gravity.CENTER);
        errorDetails.setTextColor(0xFF6F7A93);
        errorDetails.setPadding(0, Ui.dp(this, 12), 0, 0);
        box.addView(errorDetails);

        Button retry = Ui.primary(this, "Повторить");
        retry.setOnClickListener(v -> {
            hideError();
            load(station);
        });
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 214), Ui.dp(this, 48));
        retryParams.gravity = Gravity.CENTER_HORIZONTAL;
        retryParams.topMargin = Ui.dp(this, 24);
        box.addView(retry, retryParams);

        Button stations = Ui.ghost(this, "Станции");
        stations.setOnClickListener(v -> openStations());
        LinearLayout.LayoutParams stationsParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 214), Ui.dp(this, 46));
        stationsParams.gravity = Gravity.CENTER_HORIZONTAL;
        stationsParams.topMargin = Ui.dp(this, 10);
        box.addView(stations, stationsParams);
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
                if (isInternalUrl(url)) return;
                progress.setVisibility(View.VISIBLE);
                startLoadTimeout();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                if (pull != null) pull.finishRefresh();
                if (errorShown) return;
                cancelLoadTimeout();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request == null || !request.isForMainFrame()) return;
                String url = request.getUrl() == null ? "" : request.getUrl().toString();
                if (isInternalUrl(url)) return;
                int code = error == null ? WebViewClient.ERROR_UNKNOWN : error.getErrorCode();
                CharSequence description = error == null ? "" : error.getDescription();
                handleLoadError(code, description == null ? "" : description.toString(), url);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                // Устаревший колбэк (API < 23); на новых WebView не вызывается для подресурсов.
                if (isInternalUrl(failingUrl)) return;
                String main = view.getUrl();
                if (main != null && failingUrl != null && !failingUrl.equals(main)) return;
                handleLoadError(errorCode, description == null ? "" : description,
                        failingUrl == null ? "" : failingUrl);
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                            WebResourceResponse response) {
                if (request == null || !request.isForMainFrame()) return;
                String url = request.getUrl() == null ? "" : request.getUrl().toString();
                if (isInternalUrl(url)) return;
                int status = response == null ? 0 : response.getStatusCode();
                if (status < 400) return;
                String reason = response == null ? "" : response.getReasonPhrase();
                showError("Панель ответила ошибкой",
                        "Станция вернула код " + status
                                + (reason == null || reason.isEmpty() ? "" : " (" + reason + ")")
                                + ". Попробуйте ещё раз или выберите другую станцию.",
                        url);
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

    private static boolean isInternalUrl(String url) {
        return url == null || url.isEmpty() || url.startsWith("about:") || url.startsWith("data:");
    }

    private void handleLoadError(int code, String description, String url) {
        if (errorShown) return;
        String host = station == null ? "" : (station.ip.isEmpty() ? station.url : station.ip);
        String title;
        String message;
        if (code == WebViewClient.ERROR_HOST_LOOKUP) {
            title = "Станция не найдена";
            message = "Не удалось найти «" + host + "» в сети. Проверьте, что колонка включена и подключена к этой же Wi-Fi сети.";
        } else if (code == WebViewClient.ERROR_TIMEOUT) {
            title = "Панель не отвечает";
            message = "Станция не ответила вовремя. Проверьте Wi-Fi и попробуйте ещё раз.";
        } else {
            title = "Панель недоступна";
            message = "Не удалось подключиться к «" + host + "». Проверьте питание колонки и адрес станции.";
        }
        StringBuilder details = new StringBuilder();
        if (url != null && !url.isEmpty()) details.append(url);
        if (description != null && !description.isEmpty()) {
            if (details.length() > 0) details.append(" · ");
            details.append(description);
        }
        showError(title, message, details.toString());
    }

    private void onLoadTimeout() {
        if (isFinishing() || isDestroyed() || errorShown) return;
        if (webView != null) webView.stopLoading();
        showError("Панель не отвечает",
                "Станция не ответила за " + (LOAD_TIMEOUT_MS / 1000)
                        + " секунд. Проверьте, что колонка включена и в той же Wi-Fi сети.",
                station == null ? "" : station.url);
    }

    private void startLoadTimeout() {
        ui.removeCallbacks(loadTimeout);
        ui.postDelayed(loadTimeout, LOAD_TIMEOUT_MS);
    }

    private void cancelLoadTimeout() {
        ui.removeCallbacks(loadTimeout);
    }

    private void showError(String title, String message, String details) {
        if (isFinishing() || isDestroyed()) return;
        cancelLoadTimeout();
        progress.setVisibility(View.GONE);
        if (pull != null) pull.finishRefresh();
        errorTitle.setText(title);
        errorMessage.setText(message);
        if (details == null || details.isEmpty()) {
            errorDetails.setVisibility(View.GONE);
        } else {
            errorDetails.setText(details);
            errorDetails.setVisibility(View.VISIBLE);
        }
        errorView.setVisibility(View.VISIBLE);
        if (webView != null) webView.setVisibility(View.INVISIBLE);
        errorShown = true;
    }

    private void hideError() {
        cancelLoadTimeout();
        errorShown = false;
        errorView.setVisibility(View.GONE);
        if (webView != null) webView.setVisibility(View.VISIBLE);
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
        startLoadTimeout();
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
        startLoadTimeout();
    }

    private int navHeight() {
        return bottomNav == null ? 0 : bottomNav.getHeight();
    }

    private void openStations() {
        startActivity(new Intent(this, SetupActivity.class)
                .putExtra(SetupActivity.EXTRA_MANAGE, true));
    }

    private void showMenu() {
        String stationLabel = station == null ? ""
                : (station.name == null || station.name.isEmpty() ? station.ip : station.name);
        String stationUrl = station == null ? "" : station.url;
        final Ui.Sheet sheet = new Ui.Sheet(this, "MidiPult", "Управление станцией", navHeight());
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
                "Так станция будет видна в списке и в меню", navHeight());
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
        final UpdateSheet sheet = new UpdateSheet(this, navHeight());
        sheet.showChecking();
        sheet.show();
        UpdateChecker.check(this, true, (info, error) -> {
            if (isFinishing() || isDestroyed() || sheet.isDismissed()) {
                sheet.dismiss();
                return;
            }
            if (error != null) {
                sheet.showCheckError(error, () -> {
                    sheet.dismiss();
                    checkAppUpdate();
                });
            } else if (info == null) {
                sheet.showUpToDate(BuildConfig.VERSION_NAME);
            } else {
                showUpdateAvailable(sheet, info);
            }
        });
    }

    private void showUpdateAvailable(final UpdateSheet sheet, final UpdateChecker.UpdateInfo info) {
        if (sheet.isDismissed()) return;
        sheet.showAvailable(info,
                () -> startDownload(sheet, info),
                sheet::dismiss);
        sheet.show();
    }

    private void startDownload(final UpdateSheet sheet, final UpdateChecker.UpdateInfo info) {
        sheet.showDownloading(info);
        UpdateChecker.downloadAndInstall(this, info,
                (downloaded, total) -> sheet.setProgress(downloaded, total),
                (file, error) -> {
                    if (isFinishing() || isDestroyed()) {
                        sheet.dismiss();
                        return;
                    }
                    if (error != null) {
                        sheet.showDownloadError(error, () -> startDownload(sheet, info));
                        return;
                    }
                    sheet.showReady(file, () -> {
                        sheet.dismiss();
                        UpdateChecker.install(this, file);
                    }, sheet::dismiss);
                });
    }

    @Override
    public void onBackPressed() {
        if (errorShown) {
            super.onBackPressed();
            return;
        }
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
        if (station == null || !active.url.equals(station.url) || errorShown) {
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
        cancelLoadTimeout();
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
