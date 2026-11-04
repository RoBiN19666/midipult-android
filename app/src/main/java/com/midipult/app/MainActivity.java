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
import android.view.ActionMode;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.RenderProcessGoneDetail;
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

import java.net.URLDecoder;
import java.util.Locale;

/** Основной экран: полноэкранная панель, нижняя навигация и меню приложения. */
public class MainActivity extends Activity {
    private static final int FILE_CHOOSER = 77;
    private static final long LOAD_TIMEOUT_MS = 25000L;

    /**
     * Запрещает выделение текста и контекстное меню по долгому нажатию вне полей ввода.
     * В input/textarea/contenteditable выделение и меню Copy/Paste остаются как обычно.
     */
    private static final String SELECTION_GUARD_JS =
            "(function(){"
                    + "if(window.__midipultSelectionGuard)return;"
                    + "window.__midipultSelectionGuard=true;"
                    + "var css='html,body,*{-webkit-user-select:none !important;-webkit-touch-callout:none !important;}'"
                    + "+'input,textarea,[contenteditable]:not([contenteditable=\"false\"]),"
                    + "[contenteditable]:not([contenteditable=\"false\"]) *{"
                    + "-webkit-user-select:text !important;-webkit-touch-callout:default !important;}';"
                    + "var style=document.createElement('style');"
                    + "style.type='text/css';"
                    + "style.appendChild(document.createTextNode(css));"
                    + "(document.head||document.documentElement).appendChild(style);"
                    + "function isEditable(node){"
                    + "while(node&&node.nodeType!==1)node=node.parentNode;"
                    + "if(!node||!node.closest)return false;"
                    + "var hit=node.closest('input,textarea,[contenteditable]');"
                    + "if(!hit)return false;"
                    + "var tag=hit.tagName?hit.tagName.toUpperCase():'';"
                    + "if(tag==='INPUT'){var type=(hit.getAttribute('type')||'text').toLowerCase();"
                    + "return ['checkbox','radio','button','submit','reset','range','color','file','image']"
                    + ".indexOf(type)<0;}"
                    + "if(tag==='TEXTAREA')return true;"
                    + "var ce=hit.getAttribute('contenteditable');"
                    + "return ce!==null&&ce.toLowerCase()!=='false';"
                    + "}"
                    + "function report(v){try{if(window.MidiPultSelection)window.MidiPultSelection.setEditable(!!v);}catch(e){}}"
                    + "document.addEventListener('touchstart',function(e){report(isEditable(e.target));},true);"
                    + "document.addEventListener('mousedown',function(e){report(isEditable(e.target));},true);"
                    + "document.addEventListener('focusin',function(e){report(isEditable(e.target));},true);"
                    + "document.addEventListener('selectstart',function(e){"
                    + "if(!isEditable(e.target))e.preventDefault();},true);"
                    + "document.addEventListener('contextmenu',function(e){"
                    + "if(!isEditable(e.target))e.preventDefault();},true);"
                    + "})();";

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
    /** Процесс-рендерер WebView упал: WebView больше нельзя использовать, нужен recreate. */
    private boolean rendererGone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        station = StationStore.active(this);
        if (station == null) {
            startActivity(new Intent(this, SetupActivity.class));
            overridePendingTransition(0, 0);
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
            UpdateFlow.showAvailable(this, navHeight(), info);
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

        bottomNav = Ui.navBar(this, 0, this::onNavItem);
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

        errorTitle = Ui.title(this, getString(R.string.error_panel_unavailable), 21);
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

        Button retry = Ui.primary(this, getString(R.string.action_retry));
        retry.setOnClickListener(v -> {
            if (rendererGone) {
                // WebView после падения рендерера пересоздаётся только вместе с Activity.
                recreate();
                return;
            }
            hideError();
            load(station);
        });
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 214), Ui.dp(this, 48));
        retryParams.gravity = Gravity.CENTER_HORIZONTAL;
        retryParams.topMargin = Ui.dp(this, 24);
        box.addView(retry, retryParams);

        Button stations = Ui.ghost(this, getString(R.string.nav_columns));
        stations.setOnClickListener(v -> openStations());
        LinearLayout.LayoutParams stationsParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 214), Ui.dp(this, 46));
        stationsParams.gravity = Gravity.CENTER_HORIZONTAL;
        stationsParams.topMargin = Ui.dp(this, 10);
        box.addView(stations, stationsParams);
        return box;
    }

    private void onNavItem(int index) {
        if (index == 1) {
            openStations();
        } else if (index == 2) {
            showMenu();
        }
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        // content:// нужен WebView для файлов, выбранных через file chooser.
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setUserAgentString(settings.getUserAgentString() + " MidiPultAndroid/" + BuildConfig.VERSION_NAME);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.addJavascriptInterface(new SelectionBridge((PageWebView) webView), "MidiPultSelection");

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
                if (!errorShown && !isInternalUrl(url)) {
                    view.evaluateJavascript(SELECTION_GUARD_JS, null);
                }
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
                // 401/403/407 обычно означают страницу входа/авторизации панели:
                // её нужно показать, а не закрывать заглушкой.
                if (status < 400 || status == 401 || status == 403 || status == 407) return;
                String reason = response == null ? "" : response.getReasonPhrase();
                showError(getString(R.string.error_panel_http_title),
                        getString(R.string.error_panel_http_message, status,
                                reason == null || reason.isEmpty() ? "" : " (" + reason + ")"),
                        url);
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // Возвращаем true: иначе система убьёт приложение вместе с рендерером.
                rendererGone = true;
                showError(getString(R.string.error_renderer_title),
                        getString(R.string.error_renderer_message),
                        station == null ? "" : station.url);
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, android.webkit.ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = null;
                try {
                    intent = params == null ? null : params.createIntent();
                } catch (Exception ignored) {
                }
                if (intent == null) {
                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                }
                if (params != null && params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
                try {
                    startActivityForResult(Intent.createChooser(intent,
                            getString(R.string.file_chooser_title)), FILE_CHOOSER);
                    return true;
                } catch (Exception exc) {
                    fileCallback = null;
                    return false;
                }
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                String fileName = downloadFileName(contentDisposition, mimeType, url);
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.addRequestHeader("User-Agent", userAgent);
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null) request.addRequestHeader("Cookie", cookie);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalFilesDir(MainActivity.this,
                        Environment.DIRECTORY_DOWNLOADS, fileName);
                DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (manager != null) {
                    manager.enqueue(request);
                    Toast.makeText(this, getString(R.string.download_started, fileName),
                            Toast.LENGTH_SHORT).show();
                }
            } catch (Exception exc) {
                Toast.makeText(this, getString(R.string.download_start_failed),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Имя файла из Content-Disposition (включая filename*), иначе из URL + расширение по mime. */
    private static String downloadFileName(String contentDisposition, String mimeType, String url) {
        String name = null;
        if (contentDisposition != null) {
            String lower = contentDisposition.toLowerCase(Locale.US);
            int star = lower.indexOf("filename*=");
            if (star >= 0) {
                name = dispositionValue(contentDisposition, star + "filename*=".length());
                int encoded = name == null ? -1 : name.indexOf("''");
                if (encoded >= 0) name = name.substring(encoded + 2);
                name = decode(name);
            }
            if (name == null || name.trim().isEmpty()) {
                int plain = lower.indexOf("filename=");
                if (plain >= 0) {
                    name = decode(dispositionValue(contentDisposition, plain + "filename=".length()));
                }
            }
        }
        if (name == null || name.trim().isEmpty()) {
            String last = Uri.parse(url == null ? "" : url).getLastPathSegment();
            name = decode(last);
        }
        name = sanitizeFileName(name);
        if (name.isEmpty()) name = "midipult-" + System.currentTimeMillis();
        if (!hasExtension(name) && mimeType != null) {
            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (extension != null && !extension.isEmpty()) name = name + "." + extension;
        }
        return name;
    }

    private static String dispositionValue(String header, int from) {
        String value = header.substring(from).trim();
        int semicolon = value.indexOf(';');
        if (semicolon >= 0) value = value.substring(0, semicolon);
        value = value.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String decode(String value) {
        if (value == null) return "";
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String sanitizeFileName(String value) {
        if (value == null) return "";
        String name = value.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.equals(".") || name.equals("..")) name = "";
        if (name.length() > 96) {
            int dot = name.lastIndexOf('.');
            String extension = dot > 0 && name.length() - dot <= 10 ? name.substring(dot) : "";
            name = name.substring(0, Math.max(1, 96 - extension.length())) + extension;
        }
        return name;
    }

    private static boolean hasExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1 && name.length() - dot <= 10
                && name.substring(dot + 1).matches("[A-Za-z0-9]+");
    }

    /** Человеческое описание сетевой ошибки вместо net::ERR_... */
    private String friendlyNetworkDescription(String description) {
        if (description == null) return "";
        String value = description.trim();
        String lower = value.toLowerCase(Locale.US);
        if (lower.contains("err_connection_refused") || lower.contains("connection refused")) {
            return getString(R.string.network_connection_refused);
        }
        if (lower.contains("err_name_not_resolved") || lower.contains("err_host_lookup")) {
            return getString(R.string.network_name_not_found);
        }
        if (lower.contains("err_timed_out") || lower.contains("timed out")) {
            return getString(R.string.network_timeout);
        }
        if (lower.contains("err_connection_reset") || lower.contains("err_connection_closed")) {
            return getString(R.string.network_connection_reset);
        }
        if (lower.contains("err_address_unreachable") || lower.contains("err_network_changed")) {
            return getString(R.string.network_address_unreachable);
        }
        if (lower.contains("err_internet_disconnected")) {
            return getString(R.string.network_no_internet);
        }
        if (lower.contains("err_ssl") || lower.contains("certificate")) {
            return getString(R.string.network_ssl_error);
        }
        if (lower.startsWith("net::")) return "";
        return value;
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
            title = getString(R.string.error_column_not_found_title);
            message = getString(R.string.error_column_not_found_message, host);
        } else if (code == WebViewClient.ERROR_TIMEOUT) {
            title = getString(R.string.error_panel_timeout_title);
            message = getString(R.string.error_panel_timeout_message);
        } else {
            title = getString(R.string.error_panel_unavailable);
            message = getString(R.string.error_connect_message, host);
        }
        StringBuilder details = new StringBuilder();
        if (url != null && !url.isEmpty()) details.append(url);
        String readable = friendlyNetworkDescription(description);
        if (!readable.isEmpty()) {
            if (details.length() > 0) details.append(" · ");
            details.append(readable);
        }
        showError(title, message, details.toString());
    }

    private void onLoadTimeout() {
        if (isFinishing() || isDestroyed() || errorShown) return;
        if (webView != null) webView.stopLoading();
        showError(getString(R.string.error_panel_timeout_title),
                getString(R.string.error_panel_timeout_seconds_message, LOAD_TIMEOUT_MS / 1000),
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
        if (rendererGone) {
            recreate();
            return;
        }
        station = target;
        hideError();
        webView.loadUrl(target.url);
        startLoadTimeout();
    }

    private void reload() {
        if (webView == null) return;
        if (rendererGone) {
            recreate();
            return;
        }
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
        // Нижнее меню в новом экране должно появиться сразу, без window-анимации.
        overridePendingTransition(0, 0);
    }

    private void showMenu() {
        String stationLabel = station == null ? ""
                : (station.name == null || station.name.isEmpty() ? station.ip : station.name);
        String stationUrl = station == null ? "" : station.url;
        AppMenu.show(this, navHeight(), stationLabel, stationUrl,
                this::openStations,
                this::renameCurrent,
                () -> UpdateFlow.check(this, navHeight()),
                () -> openExternal(stationUrl));
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
        final Ui.Sheet sheet = new Ui.Sheet(this, getString(R.string.rename_title),
                getString(R.string.rename_subtitle), navHeight());
        final android.widget.EditText input = Ui.field(this, getString(R.string.rename_hint), station.name);
        sheet.add(input);
        sheet.keyboard();
        sheet.negative(getString(R.string.action_cancel));
        sheet.positive(getString(R.string.action_save), () -> {
            StationStore.rename(this, station, input.getText().toString());
            Station updated = StationStore.active(this);
            if (updated != null) station = updated;
            Toast.makeText(this, getString(R.string.rename_saved), Toast.LENGTH_SHORT).show();
            sheet.dismiss();
        });
        sheet.show();
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
        UpdateChecker.cleanupAfterInstall(this);
        if (rendererGone) {
            recreate();
            return;
        }
        if (webView != null) webView.onResume();
        Station active = StationStore.active(this);
        if (active == null) {
            // Станцию удалили из списка — возвращаемся к выбору.
            if (!isFinishing()) {
                startActivity(new Intent(this, SetupActivity.class));
                overridePendingTransition(0, 0);
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
            try {
                webView.removeJavascriptInterface("MidiPultSelection");
                webView.destroy();
            } catch (Exception ignored) {
                // Рендерер мог уже упасть — уничтожение мёртвого WebView не критично.
            }
            webView = null;
        }
        super.onDestroy();
    }

    /**
     * WebView по умолчанию запрещает родителю перехватывать жесты, из-за чего
     * перестаёт работать обновление «потянуть вниз». Заодно не даём открывать
     * системное меню выделения, если долгое нажатие было вне поля ввода.
     */
    private static final class PageWebView extends WebView {
        private boolean editableTouched;

        PageWebView(Context context) {
            super(context);
        }

        void setEditableTouched(boolean value) {
            editableTouched = value;
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            super.requestDisallowInterceptTouchEvent(false);
        }

        @Override
        public ActionMode startActionMode(ActionMode.Callback callback) {
            return editableTouched ? super.startActionMode(callback) : null;
        }

        @Override
        public ActionMode startActionMode(ActionMode.Callback callback, int type) {
            return editableTouched ? super.startActionMode(callback, type) : null;
        }
    }

    /** Мост из страницы: сообщает, пришлось ли касание на поле ввода. */
    private static final class SelectionBridge {
        private final PageWebView view;

        SelectionBridge(PageWebView view) {
            this.view = view;
        }

        @JavascriptInterface
        public void setEditable(boolean editable) {
            view.setEditableTouched(editable);
        }
    }
}
