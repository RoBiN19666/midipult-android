package com.midipult.app;

import android.app.Activity;

/** Проверка и установка обновлений APK через кастомный экран UpdateSheet. */
final class UpdateFlow {
    private UpdateFlow() {
    }

    /** Проверить обновление и показать результат в UpdateSheet. */
    static void check(final Activity activity, final int bottomOffsetPx) {
        final UpdateSheet sheet = new UpdateSheet(activity, bottomOffsetPx);
        sheet.showChecking();
        sheet.show();
        UpdateChecker.check(activity, true, (info, error) -> {
            if (activity.isFinishing() || activity.isDestroyed() || sheet.isDismissed()) {
                sheet.dismiss();
                return;
            }
            if (error != null) {
                sheet.showCheckError(error, () -> {
                    sheet.dismiss();
                    check(activity, bottomOffsetPx);
                });
            } else if (info == null) {
                sheet.showUpToDate(BuildConfig.VERSION_NAME);
            } else {
                showAvailable(activity, sheet, info);
            }
        });
    }

    /** Показать уже найденное обновление (например, после авто-проверки). */
    static void showAvailable(Activity activity, int bottomOffsetPx,
                              UpdateChecker.UpdateInfo info) {
        showAvailable(activity, new UpdateSheet(activity, bottomOffsetPx), info);
    }

    private static void showAvailable(final Activity activity, final UpdateSheet sheet,
                                      final UpdateChecker.UpdateInfo info) {
        if (sheet.isDismissed()) return;
        sheet.showAvailable(info,
                () -> startDownload(activity, sheet, info),
                sheet::dismiss);
        sheet.show();
    }

    private static void startDownload(final Activity activity, final UpdateSheet sheet,
                                      final UpdateChecker.UpdateInfo info) {
        final UpdateChecker.Task task = new UpdateChecker.Task();
        sheet.showDownloading(info, () -> {
            task.cancel();
            sheet.dismiss();
        });
        UpdateChecker.downloadAndInstall(activity, info,
                (downloaded, total) -> sheet.setProgress(downloaded, total),
                task,
                (file, error) -> {
                    if (activity.isFinishing() || activity.isDestroyed() || sheet.isDismissed()) {
                        return;
                    }
                    if (error != null) {
                        if (UpdateChecker.CANCELLED.equals(error)) {
                            sheet.dismiss();
                        } else {
                            sheet.showDownloadError(error,
                                    () -> startDownload(activity, sheet, info));
                        }
                        return;
                    }
                    sheet.showReady(file, info.hasChecksum(), () -> {
                        // Если установка не началась (нет разрешения) — оставляем
                        // экран открытым, чтобы после настроек можно было повторить.
                        if (UpdateChecker.install(activity, file)) sheet.dismiss();
                    }, sheet::dismiss);
                });
    }
}
