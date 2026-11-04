package com.midipult.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class StationStore {
    private static final String PREFS = "midipult";
    private static final String KEY_STATIONS = "stations";
    /** Стабильный id активной колонки (раньше здесь лежал URL). */
    private static final String KEY_ACTIVE_ID = "active_id";
    /** Устаревший ключ с URL активной колонки — только для миграции. */
    private static final String KEY_ACTIVE_URL = "active";

    private StationStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static List<Station> load(Context context) {
        List<Station> stations = new ArrayList<>();
        boolean migrated = false;
        try {
            String raw = prefs(context).getString(KEY_STATIONS, "[]");
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                Station station = Station.fromJson(array.optJSONObject(i));
                if (station == null) continue;
                // Миграция: id больше не должен быть именем колонки.
                if (station.id.isEmpty() || station.id.equals(station.name)) {
                    station = new Station(station.name, station.url, station.ip,
                            station.model, station.version, station.url);
                    migrated = true;
                }
                stations.add(station);
            }
        } catch (Exception ignored) {
        }
        if (migrated) save(context, stations);
        return stations;
    }

    public static void save(Context context, List<Station> stations) {
        JSONArray array = new JSONArray();
        for (Station station : stations) array.put(station.toJson());
        prefs(context).edit().putString(KEY_STATIONS, array.toString()).apply();
    }

    private static Station findById(List<Station> stations, String id) {
        if (id == null || id.isEmpty()) return null;
        for (Station station : stations) {
            if (station.id.equals(id)) return station;
        }
        return null;
    }

    public static Station active(Context context) {
        List<Station> stations = load(context);
        Station byId = findById(stations, prefs(context).getString(KEY_ACTIVE_ID, ""));
        if (byId != null) return byId;

        // Миграция старого ключа: там хранился URL активной колонки.
        String legacyUrl = prefs(context).getString(KEY_ACTIVE_URL, "");
        if (legacyUrl != null && !legacyUrl.isEmpty()) {
            for (Station station : stations) {
                if (station.url.equals(legacyUrl)) {
                    setActiveId(context, station.id);
                    return station;
                }
            }
            prefs(context).edit().remove(KEY_ACTIVE_URL).apply();
        }
        return null;
    }

    public static void setActiveId(Context context, String id) {
        if (id == null || id.isEmpty()) return;
        prefs(context).edit().putString(KEY_ACTIVE_ID, id).remove(KEY_ACTIVE_URL).apply();
    }

    public static void setActive(Context context, Station station) {
        if (station == null) return;
        List<Station> stations = load(context);
        String activeId = station.id;
        boolean found = false;
        for (int i = 0; i < stations.size(); i++) {
            Station existing = stations.get(i);
            if (existing.id.equals(station.id) || existing.url.equals(station.url)) {
                String name = existing.name == null || existing.name.trim().isEmpty()
                        ? station.name : existing.name;
                Station updated = new Station(name, station.url, station.ip,
                        station.model, station.version, existing.id);
                stations.set(i, updated);
                activeId = updated.id;
                found = true;
                break;
            }
        }
        if (!found) stations.add(station);
        save(context, stations);
        setActiveId(context, activeId);
    }

    public static void rename(Context context, Station station, String name) {
        if (station == null) return;
        String clean = name == null ? "" : name.trim();
        List<Station> stations = load(context);
        for (int i = 0; i < stations.size(); i++) {
            Station item = stations.get(i);
            if (item.id.equals(station.id) || item.url.equals(station.url)) {
                stations.set(i, new Station(clean, item.url, item.ip, item.model, item.version, item.id));
                break;
            }
        }
        save(context, stations);
    }

    /** Сохраняет колонку: обновляет запись с тем же id/адресом или добавляет новую. */
    public static void upsert(Context context, Station station) {
        if (station == null || station.url.isEmpty()) return;
        List<Station> stations = load(context);
        for (int i = 0; i < stations.size(); i++) {
            Station item = stations.get(i);
            if (item.id.equals(station.id) || item.url.equals(station.url)) {
                stations.set(i, station);
                save(context, stations);
                return;
            }
        }
        stations.add(station);
        save(context, stations);
    }

    public static void remove(Context context, Station station) {
        if (station == null) return;
        List<Station> stations = load(context);
        for (int i = 0; i < stations.size(); i++) {
            Station item = stations.get(i);
            if (item.id.equals(station.id) || item.url.equals(station.url)) {
                stations.remove(i);
                break;
            }
        }
        save(context, stations);
        String activeId = prefs(context).getString(KEY_ACTIVE_ID, "");
        String legacyUrl = prefs(context).getString(KEY_ACTIVE_URL, "");
        if (station.id.equals(activeId) || station.url.equals(legacyUrl)) {
            prefs(context).edit().remove(KEY_ACTIVE_ID).remove(KEY_ACTIVE_URL).apply();
        }
    }

    public static long lastApkCheck(Context context) {
        return prefs(context).getLong("last_apk_check", 0L);
    }

    public static void setLastApkCheck(Context context, long value) {
        prefs(context).edit().putLong("last_apk_check", value).apply();
    }
}
