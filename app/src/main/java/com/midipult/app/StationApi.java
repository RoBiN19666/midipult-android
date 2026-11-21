package com.midipult.app;

import android.content.Context;
import android.webkit.CookieManager;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Прямые команды станции, когда WebView уже нет (приложение свернуто/убито,
 * а сервис уведомления продолжает работать). Авторизация — cookie панели.
 */
final class StationApi {
    private StationApi() {
    }

    static void command(Context context, String command, int position) {
        String action = command;
        if ("toggle".equals(action)) action = "play";
        JSONObject body = new JSONObject();
        try {
            body.put("action", action);
            if ("seek".equals(action)) body.put("position", Math.max(0, position));
        } catch (Exception ignored) {
        }
        post(context, "/cgi-bin/media.py", body);
    }

    static void volume(Context context, int level) {
        JSONObject body = new JSONObject();
        try {
            body.put("level", Math.max(0, Math.min(level, 100)));
        } catch (Exception ignored) {
        }
        post(context, "/cgi-bin/volume.py", body);
    }

    private static void post(Context context, String path, JSONObject body) {
        Station station = StationStore.active(context);
        if (station == null || station.url.isEmpty()) return;
        String base = station.url.endsWith("/")
                ? station.url.substring(0, station.url.length() - 1) : station.url;
        HttpURLConnection connection = null;
        try {
            URL url = new URL(base + path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(8000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-Requested-With", "MidiPult");
            connection.setRequestProperty("Origin", base);
            connection.setRequestProperty("Referer", base + "/");
            try {
                String cookie = CookieManager.getInstance().getCookie(base);
                if (cookie != null && !cookie.isEmpty()) {
                    connection.setRequestProperty("Cookie", cookie);
                }
            } catch (Exception ignored) {
                // CookieManager может быть ещё не инициализирован.
            }
            byte[] bytes = body.toString().getBytes("UTF-8");
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(bytes);
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream != null) {
                byte[] buffer = new byte[512];
                while (stream.read(buffer) > 0) {
                    // Ответ не нужен, но поток надо дочитать.
                }
                stream.close();
            }
        } catch (Exception ignored) {
            // Станция недоступна — следующее обновление/команда повторит.
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
