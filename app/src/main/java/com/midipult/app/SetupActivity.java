package com.midipult.app;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Экран станций: поиск, добавление по IP, переименование и удаление. */
public class SetupActivity extends Activity {
    public static final String EXTRA_MANAGE = "manage";

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout contentArea;
    private LinearLayout bottomNav;
    private LinearLayout list;
    private TextView status;
    private ProgressBar scanSpinner;
    private Button scanButton;
    private boolean scanning;
    private boolean manage;
    private final List<Station> shown = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Ui.BG);
        window.setNavigationBarColor(Ui.BG);
        manage = getIntent().getBooleanExtra(EXTRA_MANAGE, false);
        Station active = StationStore.active(this);
        if (!manage && active != null) {
            open(active);
            return;
        }
        View root = build();
        setContentView(root);
        Ui.screenInsets(this, root, contentArea, bottomNav, 20, 18, 14);
        List<Station> saved = StationStore.load(this);
        render(saved);
        if (saved.isEmpty()) {
            scan();
        } else {
            status.setText(getString(R.string.setup_status_saved, saved.size()));
        }
        animateContentIn();
    }

    /** Анимируется только контент: нижнее меню остаётся статичным. */
    private void animateContentIn() {
        contentArea.setAlpha(0f);
        contentArea.setTranslationY(Ui.dp(this, 12));
        contentArea.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    @SuppressWarnings("deprecation")
    private void noTransition() {
        overridePendingTransition(0, 0);
    }

    @Override
    public void finish() {
        super.finish();
        noTransition();
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        contentArea = new LinearLayout(this);
        contentArea.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.addView(Ui.title(this, getString(R.string.app_name), 26));
        TextView subtitle = Ui.muted(this, getString(R.string.setup_subtitle), 13);
        subtitle.setPadding(0, Ui.dp(this, 4), 0, 0);
        header.addView(subtitle);
        contentArea.addView(header);

        LinearLayout statusCard = Ui.card(this);
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        scanSpinner = new ProgressBar(this);
        scanSpinner.setIndeterminate(true);
        scanSpinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.ACCENT));
        scanSpinner.setVisibility(View.GONE);
        statusRow.addView(scanSpinner, new LinearLayout.LayoutParams(
                Ui.dp(this, 20), Ui.dp(this, 20)));
        status = Ui.muted(this, getString(R.string.setup_scanning), 13);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        statusParams.leftMargin = Ui.dp(this, 10);
        statusRow.addView(status, statusParams);
        statusCard.addView(statusRow);
        contentArea.addView(statusCard, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 18, this));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = Ui.dp(this, 16);
        contentArea.addView(scroll, scrollParams);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundColor(Color.TRANSPARENT);
        scroll.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        buttons.setPadding(0, Ui.dp(this, 12), 0, 0);

        scanButton = Ui.primary(this, getString(R.string.action_scan));
        scanButton.setMinHeight(Ui.dp(this, 48));
        scanButton.setOnClickListener(v -> scan());
        buttons.addView(scanButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button manual = Ui.ghost(this, getString(R.string.action_add_column));
        manual.setMinHeight(Ui.dp(this, 48));
        manual.setOnClickListener(v -> manual());
        LinearLayout.LayoutParams manualParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        manualParams.setMargins(Ui.dp(this, 8), 0, 0, 0);
        buttons.addView(manual, manualParams);
        contentArea.addView(buttons);

        TextView hint = Ui.muted(this, getString(R.string.setup_hint), 12);
        hint.setPadding(0, Ui.dp(this, 12), 0, 0);
        contentArea.addView(hint);

        root.addView(contentArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        bottomNav = Ui.navBar(this, 1, this::onNavItem);
        root.addView(bottomNav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private void onNavItem(int index) {
        if (index == 0) {
            goPanel();
        } else if (index == 2) {
            showMenu();
        }
    }

    private void goPanel() {
        Station active = StationStore.active(this);
        if (active == null) {
            Toast.makeText(this, getString(R.string.setup_select_first), Toast.LENGTH_SHORT).show();
            return;
        }
        if (manage) {
            // Экран открыт из нижнего меню — возвращаемся на панель.
            finish();
            return;
        }
        startActivity(new Intent(this, MainActivity.class));
        noTransition();
        finish();
    }

    private int navHeight() {
        return bottomNav == null ? 0 : bottomNav.getHeight();
    }

    private void showMenu() {
        Station active = StationStore.active(this);
        String name = active == null ? getString(R.string.setup_no_active)
                : (active.name == null || active.name.isEmpty() ? active.ip : active.name);
        String url = active == null ? "" : active.url;
        final Station activeStation = active;
        AppMenu.show(this, navHeight(), name, url,
                null,
                () -> {
                    if (activeStation != null) {
                        rename(activeStation);
                    } else {
                        Toast.makeText(this, getString(R.string.setup_select_first),
                                Toast.LENGTH_SHORT).show();
                    }
                },
                () -> UpdateFlow.check(this, navHeight()),
                () -> openExternal(url));
    }

    private void openExternal(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    private void scan() {
        if (scanning) return;
        scanning = true;
        scanSpinner.setVisibility(View.VISIBLE);
        status.setText(getString(R.string.setup_scanning));
        scanButton.setEnabled(false);
        scanButton.setText(getString(R.string.setup_scanning_button));
        list.removeAllViews();
        DiscoveryClient.discover(this, (stations, error) -> {
            scanning = false;
            scanSpinner.setVisibility(View.GONE);
            scanButton.setEnabled(true);
            scanButton.setText(getString(R.string.action_scan));
            if (error != null) {
                status.setText(getString(R.string.setup_scan_error, error));
                render(StationStore.load(this));
                return;
            }

            Map<String, Station> map = new LinkedHashMap<>();
            for (Station station : stations) map.put(station.id, station);

            List<Station> saved = StationStore.load(this);
            Map<String, Integer> savedNames = new LinkedHashMap<>();
            for (Station station : saved) {
                Integer count = savedNames.get(station.name);
                savedNames.put(station.name, count == null ? 1 : count + 1);
            }

            Station active = StationStore.active(this);
            String activeId = active == null ? "" : active.id;

            for (Station item : saved) {
                Station found = null;
                String foundKey = null;

                // 1. Тот же адрес.
                for (Map.Entry<String, Station> entry : map.entrySet()) {
                    if (entry.getValue().url.equals(item.url)) {
                        found = entry.getValue();
                        foundKey = entry.getKey();
                        break;
                    }
                }
                // 2. Тот же стабильный id.
                if (found == null && map.containsKey(item.id)) {
                    found = map.get(item.id);
                    foundKey = item.id;
                }
                // 3. Смена IP без id от панели: ищем по неизменному имени,
                //    только если имя уникально и совпадает порт.
                if (found == null && item.name != null && !item.name.isEmpty()
                        && Integer.valueOf(1).equals(savedNames.get(item.name))) {
                    Station candidate = null;
                    int matches = 0;
                    for (Station discovered : map.values()) {
                        if (item.name.equals(discovered.name)
                                && samePort(item.url, discovered.url)
                                && !item.url.equals(discovered.url)) {
                            candidate = discovered;
                            matches++;
                        }
                    }
                    if (matches == 1 && candidate != null) {
                        found = candidate;
                        foundKey = candidate.id;
                    }
                }

                if (found == null) {
                    map.put(item.id, item);
                    continue;
                }

                Station merged = new Station(item.name, found.url, found.ip,
                        found.model, found.version, found.id);
                if (foundKey != null) map.remove(foundKey);
                map.put(merged.id, merged);
                if (!activeId.isEmpty() && item.id.equals(activeId)
                        && !merged.id.equals(activeId)) {
                    StationStore.setActiveId(this, merged.id);
                }
            }

            List<Station> all = new ArrayList<>(map.values());
            StationStore.save(this, all);
            render(all);
            if (all.isEmpty()) {
                status.setText(getString(R.string.setup_not_found));
            } else {
                // Автооткрытие отключено: найденная по UDP колонка не должна
                // открываться без подтверждения (защита от подмены панели).
                status.setText(getString(R.string.setup_found, all.size()));
            }
        });
    }

    private static boolean samePort(String first, String second) {
        try {
            return URI.create(first).getPort() == URI.create(second).getPort();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void render(List<Station> stations) {
        List<Station> snapshot = new ArrayList<>(stations);
        shown.clear();
        shown.addAll(snapshot);
        list.removeAllViews();
        if (snapshot.isEmpty()) {
            TextView empty = Ui.muted(this, getString(R.string.setup_empty), 13);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 20));
            list.addView(empty);
            return;
        }
        for (Station station : snapshot) list.addView(row(station));
    }

    private View row(Station station) {
        LinearLayout box = Ui.card(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, Ui.dp(this, 10));
        box.setLayoutParams(params);

        TextView name = Ui.title(this,
                station.name == null || station.name.isEmpty() ? station.ip : station.name, 17);
        box.addView(name);

        List<String> bits = new ArrayList<>();
        if (!station.ip.isEmpty()) bits.add(station.ip);
        if (!station.version.isEmpty()) bits.add(getString(R.string.setup_version, station.version));
        StringBuilder metaText = new StringBuilder();
        for (String bit : bits) {
            if (metaText.length() > 0) metaText.append(" · ");
            metaText.append(bit);
        }
        TextView meta = Ui.muted(this,
                metaText.length() == 0 ? station.url : metaText.toString(), 12);
        meta.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 10));
        box.addView(meta);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button open = Ui.primary(this, getString(R.string.action_open));
        open.setOnClickListener(v -> open(station));
        actions.addView(open, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f));

        Button rename = Ui.icon(this, "✎");
        rename.setContentDescription(getString(R.string.setup_cd_rename));
        rename.setOnClickListener(v -> rename(station));
        LinearLayout.LayoutParams renameParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 46), Ui.dp(this, 44));
        renameParams.setMargins(Ui.dp(this, 8), 0, 0, 0);
        actions.addView(rename, renameParams);

        Button delete = Ui.icon(this, "✕");
        delete.setTextColor(Ui.DANGER);
        delete.setContentDescription(getString(R.string.setup_cd_delete));
        delete.setOnClickListener(v -> delete(station));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 46), Ui.dp(this, 44));
        deleteParams.setMargins(Ui.dp(this, 8), 0, 0, 0);
        actions.addView(delete, deleteParams);
        box.addView(actions);

        box.setOnClickListener(v -> open(station));
        return box;
    }

    private void rename(final Station station) {
        final Ui.Sheet sheet = new Ui.Sheet(this, getString(R.string.rename_title),
                getString(R.string.rename_subtitle), navHeight());
        final EditText input = Ui.field(this, getString(R.string.rename_hint), station.name);
        sheet.add(input);
        sheet.keyboard();
        sheet.negative(getString(R.string.action_cancel));
        sheet.positive(getString(R.string.action_save), () -> {
            String name = input.getText().toString();
            Station renamed = new Station(name, station.url, station.ip,
                    station.model, station.version, station.id);
            StationStore.upsert(this, renamed);
            replaceShown(station, renamed);
            render(shown);
            Toast.makeText(this, getString(R.string.rename_saved), Toast.LENGTH_SHORT).show();
            sheet.dismiss();
        });
        sheet.show();
    }

    private void delete(final Station station) {
        final String label = station.name == null || station.name.trim().isEmpty()
                ? station.ip : station.name;
        Station active = StationStore.active(this);
        boolean isActive = active != null
                && (active.id.equals(station.id) || active.url.equals(station.url));
        final Ui.Sheet sheet = new Ui.Sheet(this, getString(R.string.delete_title),
                "«" + label + "»", navHeight());
        sheet.message(isActive
                ? getString(R.string.delete_message_active)
                : getString(R.string.delete_message));
        sheet.negative(getString(R.string.action_cancel));
        sheet.danger(getString(R.string.action_delete), () -> {
            StationStore.remove(this, station);
            removeShown(station);
            render(shown);
            Toast.makeText(this, getString(R.string.delete_done), Toast.LENGTH_SHORT).show();
            if (shown.isEmpty()) {
                status.setText(getString(R.string.setup_not_found_scan));
            } else {
                status.setText(getString(R.string.setup_count, shown.size()));
            }
            sheet.dismiss();
        });
        sheet.show();
    }

    private void removeShown(Station station) {
        for (int i = 0; i < shown.size(); i++) {
            Station item = shown.get(i);
            if (item.id.equals(station.id) || item.url.equals(station.url)) {
                shown.remove(i);
                break;
            }
        }
    }

    private void replaceShown(Station station, Station replacement) {
        for (int i = 0; i < shown.size(); i++) {
            Station item = shown.get(i);
            if (item.id.equals(station.id) || item.url.equals(station.url)) {
                shown.set(i, replacement);
                break;
            }
        }
    }

    private void open(Station station) {
        StationStore.setActive(this, station);
        if (manage) {
            // Экран открыт из меню — возвращаемся на панель, MainActivity перезагрузится.
            finish();
            return;
        }
        startActivity(new Intent(this, MainActivity.class));
        noTransition();
        finish();
    }

    private void manual() {
        final Ui.Sheet sheet = new Ui.Sheet(this, getString(R.string.add_title),
                getString(R.string.add_subtitle), navHeight());
        final EditText input = Ui.field(this, "http://192.168.1.10:8055", "");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        sheet.add(input);
        sheet.keyboard();
        sheet.negative(getString(R.string.action_cancel));
        sheet.positive(getString(R.string.action_connect), () -> {
            sheet.dismiss();
            probe(normalize(input.getText().toString()));
        });
        sheet.show();
    }

    private void probe(final String address) {
        final Ui.Sheet sheet = new Ui.Sheet(this, getString(R.string.probe_title), address, navHeight());
        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.ACCENT));
        FrameLayout holder = new FrameLayout(this);
        holder.addView(spinner, new FrameLayout.LayoutParams(
                Ui.dp(this, 36), Ui.dp(this, 36), Gravity.CENTER));
        holder.setMinimumHeight(Ui.dp(this, 44));
        sheet.add(holder);
        sheet.cancelable(false);
        sheet.show();
        new Thread(() -> {
            String error = null;
            Station station = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(address + "/version.json");
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                connection.setRequestProperty("User-Agent", "MidiPult-Android/" + BuildConfig.VERSION_NAME);
                if (connection.getResponseCode() != 200) {
                    throw new RuntimeException("HTTP " + connection.getResponseCode());
                }
                String text = new String(read(connection), "UTF-8");
                JSONObject object = new JSONObject(text);
                String service = object.optString("service", "");
                String version = object.optString("version", "");
                // Панель может не отдавать service в /version.json (легаси),
                // но если поле есть и оно чужое — это не MidiPult.
                boolean knownPanel = service.isEmpty() || "midipult".equalsIgnoreCase(service);
                if (!knownPanel || version.isEmpty()) {
                    error = getString(R.string.probe_not_panel);
                } else {
                    String host = host(address);
                    String name = object.optString("name", "").trim();
                    if (name.isEmpty()) name = getString(R.string.column_fallback, host);
                    station = new Station(name, address, host,
                            object.optString("model", ""), version,
                            object.optString("id", ""));
                }
            } catch (Exception exc) {
                error = friendlyError(exc);
            } finally {
                if (connection != null) {
                    try {
                        connection.disconnect();
                    } catch (Exception ignored) {
                    }
                }
            }
            final Station found = station;
            final String message = error;
            main.post(() -> {
                sheet.dismiss();
                if (found != null) open(found);
                else Toast.makeText(this, getString(R.string.probe_error, message),
                        Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private static byte[] read(HttpURLConnection connection) throws Exception {
        try (java.io.InputStream input = connection.getInputStream();
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) > 0) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    /** Понятное сообщение вместо английских исключений. */
    private String friendlyError(Exception exc) {
        String raw = exc.getMessage() == null ? "" : exc.getMessage().trim();
        String lower = raw.toLowerCase(java.util.Locale.US);
        if (lower.startsWith("http ")) {
            return getString(R.string.probe_http_code, raw.substring(5).trim());
        }
        if (lower.contains("timed out") || lower.contains("timeout")) {
            return getString(R.string.probe_timeout);
        }
        if (lower.contains("connection refused") || lower.contains("econnrefused")) {
            return getString(R.string.probe_refused);
        }
        if (lower.contains("unable to resolve host") || lower.contains("unknownhost")) {
            return getString(R.string.probe_host);
        }
        if (lower.contains("network is unreachable") || lower.contains("no route to host")) {
            return getString(R.string.probe_unreachable);
        }
        return getString(R.string.probe_failed);
    }

    private static String normalize(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://" + value;
        }
        try {
            URI uri = URI.create(value);
            if (uri.getPort() < 0) value = value + ":8055";
        } catch (Exception ignored) {
        }
        return value.replaceAll("/+$", "");
    }

    private static String host(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost() == null ? "" : uri.getHost();
        } catch (Exception ignored) {
            return "";
        }
    }
}
