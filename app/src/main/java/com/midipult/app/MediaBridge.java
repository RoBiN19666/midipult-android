package com.midipult.app;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.lang.ref.WeakReference;

/**
 * Мост между WebView и системным плеером Android.
 *
 * Страница присылает сюда текущий трек/состояние, а сервис уведомления
 * присылает обратно команды play/pause/next/volume. Если WebView уже нет
 * (приложение свернули/процесс пересоздали), команды уходят напрямую в API
 * станции.
 */
public final class MediaBridge {
    static final String ACTION_UPDATE = "com.midipult.app.MEDIA_UPDATE";
    static final String ACTION_CLEAR = "com.midipult.app.MEDIA_CLEAR";
    static final String EXTRA_JSON = "json";

    private static WeakReference<WebView> current = new WeakReference<>(null);

    private MediaBridge() {
    }

    static void attach(WebView view) {
        current = new WeakReference<>(view);
    }

    static void detach(WebView view) {
        if (current.get() == view) current = new WeakReference<>(null);
    }

    static boolean hasView() {
        return current.get() != null;
    }

    static void eval(String script) {
        WebView view = current.get();
        if (view == null || script == null) return;
        view.post(() -> {
            try {
                view.evaluateJavascript(script, null);
            } catch (Exception ignored) {
                // Страница могла быть уже уничтожена.
            }
        });
    }

    static void update(Context context, String json) {
        if (context == null || json == null) return;
        Intent intent = new Intent(context.getApplicationContext(), PlaybackService.class)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_JSON, json);
        start(context, intent);
    }

    static void clear(Context context) {
        if (context == null) return;
        try {
            // Не startForegroundService: если сервис ещё не запущен, система
            // потребует startForeground(), а нам нужно просто убрать плеер.
            context.stopService(new Intent(context.getApplicationContext(), PlaybackService.class));
        } catch (Exception ignored) {
        }
    }

    static void command(Context context, String command, int position) {
        if (context == null || command == null) return;
        if (hasView()) {
            String script;
            switch (command) {
                case "play":
                    script = "window.MidiPultRemote&&MidiPultRemote.play()";
                    break;
                case "pause":
                    script = "window.MidiPultRemote&&MidiPultRemote.pause()";
                    break;
                case "toggle":
                    script = "window.MidiPultRemote&&MidiPultRemote.playPause()";
                    break;
                case "next":
                    script = "window.MidiPultRemote&&MidiPultRemote.next()";
                    break;
                case "prev":
                    script = "window.MidiPultRemote&&MidiPultRemote.prev()";
                    break;
                case "seek":
                    script = "window.MidiPultRemote&&MidiPultRemote.seek(" + position + ")";
                    break;
                default:
                    script = null;
            }
            if (script != null) {
                eval(script);
                return;
            }
        }
        StationApi.command(context, command, position);
    }

    static void volume(Context context, int level) {
        if (context == null) return;
        if (hasView()) {
            eval("window.MidiPultRemote&&MidiPultRemote.setVolume(" + level + ")");
        } else {
            StationApi.volume(context, level);
        }
    }

    private static void start(Context context, Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception ignored) {
            // Android 12+ может запретить старт из фона; тогда уведомление
            // появится при следующем открытии приложения.
        }
    }

    /** Объект, который WebView видит как window.MidiPultMedia. */
    public static final class Js {
        private final Context context;

        public Js(Context context) {
            this.context = context.getApplicationContext();
        }

        @JavascriptInterface
        public void update(String json) {
            MediaBridge.update(context, json);
        }

        @JavascriptInterface
        public void clear() {
            MediaBridge.clear(context);
        }
    }
}
