package com.midipult.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.VolumeProvider;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Полноценный системный плеер: MediaSession + MediaStyle-уведомление в шторке
 * и на экране блокировки.
 *
 * Громкость отдана в VolumeProvider: аппаратные кнопки Android показывают свою
 * шкалу «Громкость мультимедиа», но меняют только громкость колонки, а не
 * телефона.
 */
public final class PlaybackService extends Service {
    static final String ACTION_UPDATE = "com.midipult.app.MEDIA_UPDATE";
    static final String ACTION_CLEAR = "com.midipult.app.MEDIA_CLEAR";
    static final String ACTION_COMMAND = "com.midipult.app.MEDIA_COMMAND";
    static final String ACTION_VOLUME = "com.midipult.app.MEDIA_VOLUME";
    static final String EXTRA_JSON = "json";
    static final String EXTRA_COMMAND = "command";
    static final String EXTRA_POSITION = "position";
    static final String EXTRA_VOLUME = "volume";

    private static final String CHANNEL_ID = "midipult_media";
    private static final int NOTIFICATION_ID = 41;
    private static final String PREFS = "midipult_media";
    private static final String KEY_JSON = "last_json";

    private MediaSession session;
    private VolumeProvider volumeProvider;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private String title = "";
    private String artist = "";
    private String cover = "";
    private String artUrl = "";
    private Bitmap art;
    private boolean playing;
    private long position;
    private long duration;
    private int volume = 40;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        session = new MediaSession(this, "MidiPult");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                handleCommand("play");
            }

            @Override
            public void onPause() {
                handleCommand("pause");
            }

            @Override
            public void onSkipToNext() {
                handleCommand("next");
            }

            @Override
            public void onSkipToPrevious() {
                handleCommand("prev");
            }

            @Override
            public void onSeekTo(long pos) {
                position = Math.max(0, pos);
                handleCommand("seek");
            }
        });
        volumeProvider = new VolumeProvider(VolumeProvider.VOLUME_CONTROL_ABSOLUTE, 100, volume) {
            @Override
            public void onSetVolumeTo(int value) {
                changeVolume(value);
            }

            @Override
            public void onAdjustVolume(int direction) {
                changeVolume(volume + direction * 5);
            }
        };
        session.setPlaybackToRemote(volumeProvider);
        session.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_CLEAR.equals(action)) {
            clear();
            return START_NOT_STICKY;
        }
        if (ACTION_UPDATE.equals(action)) {
            String json = intent.getStringExtra(EXTRA_JSON);
            if (json != null) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_JSON, json).apply();
                applyJson(json);
            }
        } else if (ACTION_COMMAND.equals(action)) {
            String command = intent.getStringExtra(EXTRA_COMMAND);
            if ("seek".equals(command)) {
                position = Math.max(0, intent.getIntExtra(EXTRA_POSITION, (int) position));
            }
            handleCommand(command);
        } else if (ACTION_VOLUME.equals(action)) {
            changeVolume(intent.getIntExtra(EXTRA_VOLUME, volume));
        } else if (title.isEmpty()) {
            String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_JSON, "");
            if (!saved.isEmpty()) applyJson(saved);
        }

        if (title.isEmpty()) {
            clear();
            return START_NOT_STICKY;
        }
        startForegroundCompat();
        postNotification();
        return START_STICKY;
    }

    private void applyJson(String json) {
        try {
            JSONObject data = new JSONObject(json);
            title = data.optString("title", "");
            artist = data.optString("artist", "");
            cover = data.optString("cover", "");
            playing = data.optBoolean("playing", false);
            position = Math.max(0, data.optLong("position", position));
            duration = Math.max(0, data.optLong("duration", duration));
            int nextVolume = data.optInt("volume", volume);
            applyPanelVolume(nextVolume);
            if (cover.isEmpty()) {
                artUrl = "";
                art = null;
            } else if (!cover.equals(artUrl)) {
                loadArt(cover);
            }
            syncSession();
        } catch (Exception ignored) {
            // Битый JSON не должен ронять сервис.
        }
    }

    private void syncSession() {
        if (session == null) return;
        MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "MidiPult")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, duration);
        if (art != null) {
            metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
            metadata.putBitmap(MediaMetadata.METADATA_KEY_ART, art);
        }
        session.setMetadata(metadata.build());

        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        if (duration > 0) actions |= PlaybackState.ACTION_SEEK_TO;
        PlaybackState.Builder state = new PlaybackState.Builder()
                .setActions(actions)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        Math.max(0, position), playing ? 1f : 0f);
        session.setPlaybackState(state.build());
    }

    private void handleCommand(String command) {
        if (command == null) return;
        switch (command) {
            case "play":
                playing = true;
                break;
            case "pause":
                playing = false;
                break;
            case "toggle":
                playing = !playing;
                command = playing ? "play" : "pause";
                break;
            case "next":
            case "prev":
                break;
            case "seek":
                break;
            default:
                return;
        }
        if (session != null) session.setActive(true);
        syncSession();
        postNotification();
        int seekPosition = (int) Math.min(Integer.MAX_VALUE, Math.max(0, position));
        MediaBridge.command(this, command, seekPosition);
    }

    private void applyPanelVolume(int value) {
        volume = Math.max(0, Math.min(value, 100));
        if (volumeProvider != null) volumeProvider.setCurrentVolume(volume);
    }

    private void changeVolume(int value) {
        applyPanelVolume(value);
        MediaBridge.volume(this, volume);
    }

    private void loadArt(String url) {
        final String requested = url;
        artUrl = url;
        io.execute(() -> {
            Bitmap bitmap = null;
            try (InputStream stream = new URL(requested).openStream()) {
                bitmap = BitmapFactory.decodeStream(stream);
            } catch (Exception ignored) {
            }
            final Bitmap loaded = bitmap;
            if (loaded == null) return;
            main.post(() -> {
                if (!requested.equals(artUrl)) return;
                art = loaded;
                syncSession();
                postNotification();
            });
        });
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.media_channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.media_channel_description));
        channel.setShowBadge(false);
        channel.setSound(null, null);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);
    }

    private void startForegroundCompat() {
        Notification notification = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception exc) {
            try {
                startForeground(NOTIFICATION_ID, notification);
            } catch (Exception ignored) {
                // Разрешение на уведомления могло быть отозвано — сервис
                // всё равно продолжит работать, пока приложение живо.
            }
        }
    }

    private void postNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
    }

    private Notification buildNotification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title.isEmpty() ? "MidiPult" : title)
                .setContentText(artist)
                .setContentIntent(contentIntent())
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setOngoing(playing)
                .setDeleteIntent(null);
        if (art != null) builder.setLargeIcon(art);
        builder.addAction(action(android.R.drawable.ic_media_previous, "Назад", "prev"));
        builder.addAction(action(playing ? android.R.drawable.ic_media_pause
                        : android.R.drawable.ic_media_play,
                playing ? "Пауза" : "Играть", "toggle"));
        builder.addAction(action(android.R.drawable.ic_media_next, "Вперёд", "next"));
        Notification.MediaStyle style = new Notification.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2);
        if (session != null) style.setMediaSession(session.getSessionToken());
        builder.setStyle(style);
        return builder.build();
    }

    private Notification.Action action(int icon, String title, String command) {
        Intent intent = new Intent(this, PlaybackService.class)
                .setAction(ACTION_COMMAND)
                .putExtra(EXTRA_COMMAND, command);
        int request = command.hashCode();
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getService(this, request, intent, flags);
        return new Notification.Action.Builder(Icon.createWithResource(this, icon), title, pending).build();
    }

    private PendingIntent contentIntent() {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(this, 100, intent, flags);
    }

    private void clear() {
        if (session != null) {
            session.setActive(false);
            session.release();
            session = null;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        io.shutdownNow();
        if (session != null) {
            session.setActive(false);
            session.release();
            session = null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
