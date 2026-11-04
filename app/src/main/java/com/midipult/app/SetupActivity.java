package com.midipult.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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

/** Первый экран: поиск колонок, ручной IP и список сохранённых станций. */
public class SetupActivity extends Activity {
    public static final String EXTRA_MANAGE = "manage";

    private static final int BG = 0xFF0B0E18;
    private static final int TEXT = 0xFFEDF1FA;
    private static final int MUTED = 0xFF8B95AD;
    private static final int CARD = 0xFF151A2A;
    private static final int ACCENT = 0xFF7C5CFF;

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout list;
    private TextView status;
    private ProgressBar progress;
    private Button scanButton;
    private boolean scanning;
    private boolean manage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        manage = getIntent().getBooleanExtra(EXTRA_MANAGE, false);
        Station active = StationStore.active(this);
        if (!manage && active != null) {
            open(active);
            return;
        }
        View root = build();
        setContentView(root);
        Ui.applyInsets(this, root, 20);
        render(StationStore.load(this));
        if (savedInstanceState == null) scan();
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.addView(Ui.logo(this, 56), new LinearLayout.LayoutParams(
                Ui.dp(this, 56), Ui.dp(this, 56)));

        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        brandText.setPadding(Ui.dp(this, 14), 0, 0, 0);
        TextView title = Ui.title(this, "MidiPult", 28);
        brandText.addView(title);
        brandText.addView(Ui.muted(this, "Пульт для Яндекс Станции Миди", 13));
        brand.addView(brandText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(brand);

        LinearLayout statusCard = Ui.card(this);
        status = Ui.muted(this, "Ищу колонки в сети…", 13);
        statusCard.addView(status);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        progress.setVisibility(View.GONE);
        statusCard.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 3)));
        root.addView(statusCard, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 18, this));

        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = Ui.dp(this, 16);
        root.addView(scroll, scrollParams);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        buttons.setPadding(0, Ui.dp(this, 12), 0, 0);

        scanButton = Ui.primary(this, "Сканировать");
        scanButton.setOnClickListener(v -> scan());
        buttons.addView(scanButton, new LinearLayout.LayoutParams(0,
                Ui.dp(this, 48), 1f));

        Button manual = Ui.ghost(this, "IP вручную");
        manual.setOnClickListener(v -> manual());
        LinearLayout.LayoutParams manualParams = new LinearLayout.LayoutParams(0,
                Ui.dp(this, 48), 1f);
        manualParams.setMargins(Ui.dp(this, 8), 0, 0, 0);
        buttons.addView(manual, manualParams);
        root.addView(buttons);

        TextView hint = Ui.muted(this,
                "Колонка и телефон должны быть в одной Wi-Fi сети.\nМожно переименовать станцию кнопкой ✎.", 12);
        hint.setPadding(0, Ui.dp(this, 12), 0, 0);
        root.addView(hint);
        return root;
    }

    private void scan() {
        if (scanning) return;
        scanning = true;
        progress.setVisibility(View.VISIBLE);
        status.setText("Ищу колонки в сети…");
        scanButton.setEnabled(false);
        scanButton.setText("Сканирую…");
        list.removeAllViews();
        DiscoveryClient.discover(this, (stations, error) -> {
            scanning = false;
            progress.setVisibility(View.GONE);
            scanButton.setEnabled(true);
            scanButton.setText("Сканировать");
            if (error != null) {
                status.setText("Ошибка поиска: " + error);
                render(StationStore.load(this));
                return;
            }
            List<Station> all = new ArrayList<>(stations);
            Map<String, Station> map = new LinkedHashMap<>();
            for (Station station : all) map.put(station.url, station);
            for (Station station : StationStore.load(this)) {
                Station found = map.get(station.url);
                if (found == null) {
                    map.put(station.url, station);
                } else {
                    map.put(station.url, new Station(
                            station.name, station.url, found.ip, found.model, found.version));
                }
            }
            all = new ArrayList<>(map.values());
            render(all);
            if (all.isEmpty()) {
                status.setText("Колонки не найдены. Проверьте Wi-Fi или введите IP вручную.");
            } else {
                status.setText("Найдено: " + all.size() + ". Выберите станцию.");
                if (!manage && all.size() == 1 && !stations.isEmpty()) {
                    Station only = all.get(0);
                    main.postDelayed(() -> open(only), 650);
                }
            }
        });
    }

    private void render(List<Station> stations) {
        list.removeAllViews();
        if (stations.isEmpty()) {
            LinearLayout empty = Ui.card(this);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView logo = Ui.logo(this, 64);
            LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(
                    Ui.dp(this, 64), Ui.dp(this, 64));
            logoParams.gravity = Gravity.CENTER_HORIZONTAL;
            empty.addView(logo, logoParams);
            TextView title = Ui.title(this, "Пока нет станций", 18);
            title.setGravity(Gravity.CENTER_HORIZONTAL);
            title.setPadding(0, Ui.dp(this, 12), 0, 0);
            empty.addView(title);
            TextView text = Ui.muted(this,
                    "Нажмите «Сканировать» — приложение найдёт панель в этой Wi-Fi сети.",
                    13);
            text.setGravity(Gravity.CENTER_HORIZONTAL);
            text.setPadding(0, Ui.dp(this, 6), 0, 0);
            empty.addView(text);
            list.addView(empty);
            return;
        }
        for (Station station : stations) list.addView(row(station));
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
        if (!station.version.isEmpty()) bits.add("панель v" + station.version);
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

        Button open = Ui.primary(this, "Открыть");
        open.setOnClickListener(v -> open(station));
        actions.addView(open, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f));

        Button rename = Ui.icon(this, "✎");
        rename.setContentDescription("Переименовать станцию");
        rename.setOnClickListener(v -> rename(station));
        LinearLayout.LayoutParams renameParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 46), Ui.dp(this, 44));
        renameParams.setMargins(Ui.dp(this, 8), 0, 0, 0);
        actions.addView(rename, renameParams);
        box.addView(actions);

        box.setOnClickListener(v -> open(station));
        return box;
    }

    private void rename(Station station) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(station.name == null ? "" : station.name);
        input.setHint("Например, «Кухня»");
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("Название станции")
                .setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    StationStore.rename(this, station, input.getText().toString());
                    render(StationStore.load(this));
                    Toast.makeText(this, "Название сохранено", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void open(Station station) {
        StationStore.setActive(this, station);
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void manual() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("http://192.168.31.220:8055");
        input.setText("");
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("Адрес панели")
                .setMessage("IP колонки и порт 8055")
                .setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Подключить", (dialog, which) -> probe(normalize(input.getText().toString())))
                .show();
    }

    private void probe(String address) {
        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("Проверяю панель…");
        dialog.setCancelable(false);
        dialog.show();
        new Thread(() -> {
            String error = null;
            Station station = null;
            try {
                URL url = new URL(address + "/version.json");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                connection.setRequestProperty("User-Agent", "MidiPult-Android/" + BuildConfig.VERSION_NAME);
                if (connection.getResponseCode() != 200) throw new RuntimeException("HTTP " + connection.getResponseCode());
                String text = new String(read(connection), "UTF-8");
                JSONObject object = new JSONObject(text);
                String version = object.optString("version", "");
                if (!version.isEmpty()) {
                    station = new Station("Яндекс Станция Миди", address, host(address), "", version);
                } else {
                    error = "Это не панель MidiPult";
                }
            } catch (Exception exc) {
                error = exc.getMessage() == null ? exc.toString() : exc.getMessage();
            }
            final Station found = station;
            final String message = error;
            main.post(() -> {
                try {
                    dialog.dismiss();
                } catch (Exception ignored) {
                }
                if (found != null) open(found);
                else Toast.makeText(this, "Не удалось подключиться: " + message, Toast.LENGTH_LONG).show();
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

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
