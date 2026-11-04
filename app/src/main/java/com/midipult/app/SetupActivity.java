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
        Ui.applyInsets(this, root, 20);
        List<Station> saved = StationStore.load(this);
        render(saved);
        if (saved.isEmpty()) {
            scan();
        } else {
            status.setText("Сохранено станций: " + saved.size() + ". Можно сканировать или добавить по IP.");
        }
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout headerText = new LinearLayout(this);
        headerText.setOrientation(LinearLayout.VERTICAL);
        headerText.addView(Ui.title(this, "MidiPult", 26));
        TextView subtitle = Ui.muted(this, "Станции и подключение", 13);
        subtitle.setPadding(0, Ui.dp(this, 4), 0, 0);
        headerText.addView(subtitle);
        header.addView(headerText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (manage) {
            Button done = Ui.ghost(this, "Готово");
            done.setOnClickListener(v -> finish());
            LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 42));
            doneParams.leftMargin = Ui.dp(this, 8);
            header.addView(done, doneParams);
        }
        root.addView(header);

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
        status = Ui.muted(this, "Ищу колонки в сети…", 13);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        statusParams.leftMargin = Ui.dp(this, 10);
        statusRow.addView(status, statusParams);
        statusCard.addView(statusRow);
        root.addView(statusCard, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 18, this));

        LinearLayout listArea = new LinearLayout(this);
        listArea.setOrientation(LinearLayout.VERTICAL);
        listArea.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams areaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        areaParams.topMargin = Ui.dp(this, 16);
        root.addView(listArea, areaParams);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundColor(Color.TRANSPARENT);
        listArea.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        buttons.setPadding(0, Ui.dp(this, 12), 0, 0);

        scanButton = Ui.primary(this, "Сканировать");
        scanButton.setOnClickListener(v -> scan());
        buttons.addView(scanButton, new LinearLayout.LayoutParams(0,
                Ui.dp(this, 48), 1f));

        Button manual = Ui.ghost(this, "Добавить по IP");
        manual.setOnClickListener(v -> manual());
        LinearLayout.LayoutParams manualParams = new LinearLayout.LayoutParams(0,
                Ui.dp(this, 48), 1f);
        manualParams.setMargins(Ui.dp(this, 8), 0, 0, 0);
        buttons.addView(manual, manualParams);
        root.addView(buttons);

        TextView hint = Ui.muted(this,
                "Колонка и телефон должны быть в одной Wi-Fi сети.\n"
                        + "Станцию можно переименовать кнопкой ✎ или удалить кнопкой ✕.", 12);
        hint.setPadding(0, Ui.dp(this, 12), 0, 0);
        root.addView(hint);
        return root;
    }

    private void scan() {
        if (scanning) return;
        scanning = true;
        scanSpinner.setVisibility(View.VISIBLE);
        status.setText("Ищу колонки в сети…");
        scanButton.setEnabled(false);
        scanButton.setText("Сканирую…");
        list.removeAllViews();
        DiscoveryClient.discover(this, (stations, error) -> {
            scanning = false;
            scanSpinner.setVisibility(View.GONE);
            scanButton.setEnabled(true);
            scanButton.setText("Сканировать");
            if (error != null) {
                status.setText("Ошибка поиска: " + error);
                render(StationStore.load(this));
                return;
            }
            List<Station> all = new ArrayList<>(stations);
            Map<String, Station> map = new LinkedHashMap<>();
            for (Station station : all) map.put(stationKey(station), station);
            for (Station saved : StationStore.load(this)) {
                String key = stationKey(saved);
                Station found = map.get(key);
                if (found == null) {
                    // Миграция старых записей без id и поиск по прежнему URL.
                    for (Station candidate : map.values()) {
                        if (candidate.url.equals(saved.url)) {
                            found = candidate;
                            break;
                        }
                    }
                }
                if (found == null) {
                    map.put(key, saved);
                } else {
                    // id/имя станции стабильны, поэтому смена IP не создаёт дубль
                    // и не теряет пользовательское имя.
                    Station merged = new Station(
                            saved.name, found.url, found.ip, found.model, found.version,
                            found.id);
                    map.remove(key);
                    map.put(stationKey(merged), merged);
                }
            }
            all = new ArrayList<>(map.values());
            StationStore.save(this, all);
            render(all);
            if (all.isEmpty()) {
                status.setText("Колонки не найдены. Проверьте Wi-Fi или добавьте по IP.");
            } else {
                status.setText("Найдено: " + all.size() + ". Выберите станцию.");
                if (!manage && all.size() == 1 && !stations.isEmpty()) {
                    Station only = all.get(0);
                    main.postDelayed(() -> open(only), 650);
                }
            }
        });
    }

    private String stationKey(Station station) {
        return station.id == null || station.id.trim().isEmpty() ? station.url : station.id.trim();
    }

    private void render(List<Station> stations) {
        List<Station> snapshot = new ArrayList<>(stations);
        shown.clear();
        shown.addAll(snapshot);
        list.removeAllViews();
        if (snapshot.isEmpty()) {
            TextView empty = Ui.muted(this,
                    "Пока нет станций.\nНажмите «Сканировать» — приложение найдёт панель в этой Wi-Fi сети.",
                    13);
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

        Button delete = Ui.icon(this, "✕");
        delete.setTextColor(Ui.DANGER);
        delete.setContentDescription("Удалить станцию");
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
        final Ui.Sheet sheet = new Ui.Sheet(this, "Название станции",
                "Так станция будет видна в списке и в меню");
        final EditText input = Ui.field(this, "Например, «Кухня»", station.name);
        sheet.add(input);
        sheet.keyboard();
        sheet.negative("Отмена");
        sheet.positive("Сохранить", () -> {
            String name = input.getText().toString();
            Station renamed = new Station(name, station.url, station.ip,
                    station.model, station.version);
            StationStore.upsert(this, renamed);
            replaceShown(station, renamed);
            render(shown);
            Toast.makeText(this, "Название сохранено", Toast.LENGTH_SHORT).show();
            sheet.dismiss();
        });
        sheet.show();
    }

    private void delete(final Station station) {
        final String label = station.name == null || station.name.trim().isEmpty()
                ? station.ip : station.name;
        Station active = StationStore.active(this);
        boolean isActive = active != null && active.url.equals(station.url);
        final Ui.Sheet sheet = new Ui.Sheet(this, "Удалить станцию?", "«" + label + "»");
        sheet.message(isActive
                ? "Это активная станция. После удаления приложение вернётся к выбору станции."
                : "Станция исчезнет из списка.");
        sheet.negative("Отмена");
        sheet.danger("Удалить", () -> {
            StationStore.remove(this, station);
            removeShown(station);
            render(shown);
            Toast.makeText(this, "Станция удалена", Toast.LENGTH_SHORT).show();
            if (shown.isEmpty()) {
                status.setText("Станции не найдены. Нажмите «Сканировать» или добавьте по IP.");
            } else {
                status.setText("Станций в списке: " + shown.size() + ".");
            }
            sheet.dismiss();
        });
        sheet.show();
    }

    private void removeShown(Station station) {
        for (int i = 0; i < shown.size(); i++) {
            if (shown.get(i).url.equals(station.url)) {
                shown.remove(i);
                break;
            }
        }
    }

    private void replaceShown(Station station, Station replacement) {
        for (int i = 0; i < shown.size(); i++) {
            if (shown.get(i).url.equals(station.url)) {
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
        finish();
    }

    private void manual() {
        final Ui.Sheet sheet = new Ui.Sheet(this, "Добавить станцию",
                "IP колонки и порт панели (обычно 8055)");
        final EditText input = Ui.field(this, "http://192.168.1.10:8055", "");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        sheet.add(input);
        sheet.keyboard();
        sheet.negative("Отмена");
        sheet.positive("Подключить", () -> {
            sheet.dismiss();
            probe(normalize(input.getText().toString()));
        });
        sheet.show();
    }

    private void probe(final String address) {
        final Ui.Sheet sheet = new Ui.Sheet(this, "Проверяю панель", address);
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
            try {
                URL url = new URL(address + "/version.json");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                connection.setRequestProperty("User-Agent", "MidiPult-Android/" + BuildConfig.VERSION_NAME);
                if (connection.getResponseCode() != 200) {
                    throw new RuntimeException("HTTP " + connection.getResponseCode());
                }
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
                sheet.dismiss();
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
}
