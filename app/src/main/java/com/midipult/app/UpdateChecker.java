package com.midipult.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Проверка и установка обновлений самого APK из GitHub Releases. */
public final class UpdateChecker {
    private static final String UPDATE_PREFS = "midipult_updates";
    private static final String PENDING_VERSION = "pending_version";

    public interface Callback {
        void onResult(UpdateInfo info, String error);
    }

    public interface DownloadCallback {
        void onResult(File file, String error);
    }

    public interface ProgressListener {
        void onProgress(long downloaded, long total);
    }

    /** Отмена текущей загрузки APK. */
    public static final class Task {
        private volatile boolean cancelled;

        public void cancel() {
            cancelled = true;
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }

    public static final class UpdateInfo {
        public final String version;
        public final String name;
        public final String body;
        public final String apkUrl;
        public final String shaUrl;
        public final long size;
        public final String digest;

        UpdateInfo(String version, String name, String body, String apkUrl, String shaUrl,
                   long size, String digest) {
            this.version = version;
            this.name = name;
            this.body = body;
            this.apkUrl = apkUrl;
            this.shaUrl = shaUrl;
            this.size = size;
            this.digest = digest;
        }

        public boolean hasChecksum() {
            return shaUrl != null && !shaUrl.trim().isEmpty();
        }
    }

    private static final String API = "https://api.github.com/repos/RoBiN19666/midipult-android/releases?per_page=20";
    private static final String PREFIX = "android-v";
    /** Служебный код отменённой загрузки (не показывается пользователю). */
    public static final String CANCELLED = "__cancelled__";

    private static final ExecutorService IO = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private UpdateChecker() {
    }

    public static void autoCheck(Context context, Callback callback) {
        long last = StationStore.lastApkCheck(context);
        if (System.currentTimeMillis() - last < 24L * 60L * 60L * 1000L) {
            callback.onResult(null, null);
            return;
        }
        check(context, false, callback);
    }

    public static void check(Context context, boolean force, Callback callback) {
        final Context app = context.getApplicationContext();
        IO.execute(() -> {
            String error = null;
            UpdateInfo found = null;
            try {
                HttpURLConnection connection = open(API);
                int code = connection.getResponseCode();
                if (code != 200) throw new RuntimeException("GitHub HTTP " + code);
                String text = read(connection);
                JSONArray releases = new JSONArray(text);
                for (int i = 0; i < releases.length(); i++) {
                    JSONObject release = releases.optJSONObject(i);
                    if (release == null) continue;
                    if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue;
                    String tag = release.optString("tag_name", "");
                    if (!tag.startsWith(PREFIX)) continue;
                    String version = tag.substring(PREFIX.length());
                    if (!isNewer(version, BuildConfig.VERSION_NAME)) continue;
                    JSONObject asset = findApk(release.optJSONArray("assets"), version);
                    if (asset == null) continue;
                    JSONObject sha = findSha(release.optJSONArray("assets"), version);
                    // Без .sha256 обновление не предлагаем: проверка целостности обязательна.
                    if (sha == null) continue;
                    found = new UpdateInfo(
                            version,
                            release.optString("name", tag),
                            release.optString("body", ""),
                            asset.optString("browser_download_url", ""),
                            sha.optString("browser_download_url", ""),
                            asset.optLong("size", 0L),
                            digestOf(asset.optString("digest", ""))
                    );
                    break;
                }
                StationStore.setLastApkCheck(app, System.currentTimeMillis());
            } catch (Exception exc) {
                error = friendly(app, exc);
            }
            final UpdateInfo result = found;
            final String message = force ? error : null;
            MAIN.post(() -> callback.onResult(result, message));
        });
    }

    public static void downloadAndInstall(Context context, UpdateInfo info,
                                          ProgressListener progress, Task task,
                                          DownloadCallback callback) {
        final Context app = context.getApplicationContext();
        IO.execute(() -> {
            File target = null;
            String error = null;
            boolean cancelled = false;
            try {
                if (info.apkUrl == null || info.apkUrl.trim().isEmpty()) {
                    throw new IllegalStateException("NO_APK");
                }
                if (!info.hasChecksum()) {
                    throw new IllegalStateException("NO_SHA");
                }
                File dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) dir = app.getCacheDir();
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IllegalStateException("NO_DIR");
                }
                target = new File(dir, "midipult-android-" + info.version + ".apk");
                cleanupOldApks(dir, target);
                download(info.apkUrl, target, progress, task);

                String expected = downloadText(info.shaUrl);
                if (expected == null || expected.isEmpty()) {
                    throw new IllegalStateException("SHA_MISSING");
                }
                String actual = sha256(target);
                if (!actual.equalsIgnoreCase(expected)) {
                    throw new IllegalStateException("SHA_MISMATCH");
                }
                if (info.digest != null && !info.digest.isEmpty()
                        && !actual.equalsIgnoreCase(info.digest)) {
                    throw new IllegalStateException("DIGEST_MISMATCH");
                }
            } catch (CancelledException exc) {
                cancelled = true;
                if (target != null) target.delete();
                target = null;
            } catch (Exception exc) {
                error = friendly(app, exc);
                if (target != null) target.delete();
                target = null;
            }
            final File file = target;
            final String message = cancelled ? CANCELLED : error;
            MAIN.post(() -> callback.onResult(file, message));
        });
    }

    public static boolean install(Context context, File file) {
        if (file == null || !file.isFile()) {
            Toast.makeText(context, context.getString(R.string.update_apk_missing),
                    Toast.LENGTH_LONG).show();
            return false;
        }
        if (Build.VERSION.SDK_INT >= 26 && !canInstall(context)) {
            openInstallSettings(context);
            return false;
        }
        try {
            Uri uri = Uri.parse("content://" + context.getPackageName() + ".files/updates/"
                    + Uri.encode(file.getName()));
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception exc) {
            openInstallSettings(context);
            return false;
        }
    }

    /** Запомнить версию, для которой открыт системный установщик. */
    public static void markPendingInstall(Context context, String version) {
        if (version == null || version.trim().isEmpty()) return;
        context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
                .edit().putString(PENDING_VERSION, version).apply();
    }

    /**
     * Удалить скачанные APK после того, как новая версия действительно установлена.
     * Пока установка не завершена или отменена, файл остаётся для повтора.
     */
    public static void cleanupAfterInstall(Context context) {
        try {
            String pending = context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
                    .getString(PENDING_VERSION, "");
            if (pending == null || pending.isEmpty()) return;
            String installed = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
            if (!pending.equals(installed)) return;
            File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) dir = context.getCacheDir();
            if (dir != null) cleanupOldApks(dir, null);
            context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
                    .edit().remove(PENDING_VERSION).apply();
        } catch (Exception ignored) {
            // Не мешаем запуску приложения из-за очистки.
        }
    }

    private static boolean canInstall(Context context) {        try {
            return context.getPackageManager().canRequestPackageInstalls();
        } catch (Exception ignored) {
            return true;
        }
    }

    private static void openInstallSettings(Context context) {
        try {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(settings);
            Toast.makeText(context, context.getString(R.string.update_allow_install),
                    Toast.LENGTH_LONG).show();
        } catch (Exception ignored) {
            Toast.makeText(context, context.getString(R.string.update_installer_failed),
                    Toast.LENGTH_LONG).show();
        }
    }

    private static JSONObject findApk(JSONArray assets, String version) {
        if (assets == null) return null;
        String expected = "midipult-android-" + version + ".apk";
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset != null && expected.equals(asset.optString("name", ""))) return asset;
        }
        return null;
    }

    private static JSONObject findSha(JSONArray assets, String version) {
        if (assets == null) return null;
        String apk = "midipult-android-" + version + ".apk";
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            String name = asset == null ? "" : asset.optString("name", "");
            if (name.equals(apk + ".sha256")) return asset;
        }
        return null;
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "MidiPult-Android/" + BuildConfig.VERSION_NAME);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        return connection;
    }

    private static String read(HttpURLConnection connection) throws Exception {
        try (InputStream input = connection.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) text.append(line).append('\n');
            return text.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static void download(String url, File target, ProgressListener progress, Task task)
            throws Exception {
        if (task != null && task.isCancelled()) throw new CancelledException();
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "MidiPult-Android/" + BuildConfig.VERSION_NAME);
        try {
            int code = connection.getResponseCode();
            if (code != 200) throw new RuntimeException("HTTP " + code);
            long total = connection.getContentLength();
            long done = 0;
            long reported = 0;
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) > 0) {
                    if (task != null && task.isCancelled()) throw new CancelledException();
                    output.write(buffer, 0, count);
                    done += count;
                    if (progress != null && (done - reported >= 32 * 1024
                            || (total > 0 && done >= total))) {
                        reported = done;
                        postProgress(progress, done, total);
                    }
                }
            }
            if (task != null && task.isCancelled()) throw new CancelledException();
            if (progress != null) postProgress(progress, done, total);
        } finally {
            connection.disconnect();
        }
    }

    private static void cleanupOldApks(File dir, File keep) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (keep != null && file.equals(keep)) continue;
            String name = file.getName();
            if (name.startsWith("midipult-android-")
                    && (name.endsWith(".apk") || name.endsWith(".apk.sha256"))) {
                // Старые версии и недокачанные файлы больше не нужны.
                file.delete();
            }
        }
    }

    private static void postProgress(final ProgressListener progress, final long done, final long total) {
        MAIN.post(() -> progress.onProgress(done, total));
    }

    private static String digestOf(String digest) {
        String value = digest == null ? "" : digest.trim();
        if (value.startsWith("sha256:")) value = value.substring("sha256:".length());
        return value.toLowerCase(Locale.US);
    }

    private static String downloadText(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "MidiPult-Android/" + BuildConfig.VERSION_NAME);
        String text = read(connection);
        int index = text.indexOf('\n');
        if (index >= 0) text = text.substring(0, index);
        String token = text.trim().split("\\s+")[0];
        return token == null ? "" : token.toLowerCase(Locale.US);
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) > 0) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02x", value));
        return result.toString();
    }

    private static int[] semver(String version) {
        String value = version == null ? "" : version.trim();
        int dash = value.indexOf('-');
        if (dash >= 0) value = value.substring(0, dash);
        int plus = value.indexOf('+');
        if (plus >= 0) value = value.substring(0, plus);
        String[] parts = value.split("\\.");
        int[] numbers = new int[3];
        for (int i = 0; i < 3 && i < parts.length; i++) {
            String digits = parts[i].replaceAll("[^0-9]", "");
            if (digits.isEmpty()) break;
            try {
                numbers[i] = Integer.parseInt(digits);
            } catch (NumberFormatException ignored) {
                break;
            }
        }
        return numbers;
    }

    private static boolean isNewer(String candidate, String current) {
        int[] left = semver(candidate);
        int[] right = semver(current);
        for (int i = 0; i < 3; i++) {
            if (left[i] != right[i]) return left[i] > right[i];
        }
        return false;
    }

    private static String friendly(Context context, Exception exc) {
        String raw = exc.getMessage() == null ? "" : exc.getMessage().trim();
        String lower = raw.toLowerCase(Locale.US);
        if ("NO_APK".equals(raw)) return context.getString(R.string.update_error_no_apk);
        if ("NO_SHA".equals(raw)) return context.getString(R.string.update_error_no_sha);
        if ("NO_DIR".equals(raw)) return context.getString(R.string.update_error_no_dir);
        if ("SHA_MISSING".equals(raw)) return context.getString(R.string.update_error_sha_missing);
        if ("SHA_MISMATCH".equals(raw)) return context.getString(R.string.update_error_sha_mismatch);
        if ("DIGEST_MISMATCH".equals(raw)) {
            return context.getString(R.string.update_error_digest_mismatch);
        }
        if (lower.contains("unable to resolve host") || lower.contains("unknownhost")
                || lower.contains("nodename nor servname")) {
            return context.getString(R.string.network_no_internet);
        }
        if (lower.contains("timed out") || lower.contains("timeout")) {
            return context.getString(R.string.network_timeout);
        }
        if (lower.contains("connection refused") || lower.contains("econnrefused")) {
            return context.getString(R.string.network_connection_refused);
        }
        if (lower.contains("network is unreachable") || lower.contains("no route to host")) {
            return context.getString(R.string.network_address_unreachable);
        }
        if (lower.startsWith("github http ")) {
            return context.getString(R.string.network_github_code,
                    raw.substring("GitHub HTTP ".length()));
        }
        if (lower.startsWith("http ")) {
            return context.getString(R.string.network_server_code, raw.substring("HTTP ".length()));
        }
        return context.getString(R.string.update_error_generic);
    }

    private static final class CancelledException extends Exception {
    }
}
