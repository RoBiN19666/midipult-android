package com.midipult.app;

import android.app.Activity;

/** Общее меню приложения: используется и на панели, и в разделе «Колонки». */
final class AppMenu {
    private AppMenu() {
    }

    static void show(Activity activity, int bottomOffsetPx,
                     String stationName, String stationUrl,
                     Runnable onStations, Runnable onRename,
                     Runnable onUpdate, Runnable onBrowser) {
        final Ui.Sheet sheet = new Ui.Sheet(activity, activity.getString(R.string.app_name),
                activity.getString(R.string.menu_subtitle), bottomOffsetPx);
        sheet.row(activity.getString(R.string.menu_columns),
                activity.getString(R.string.menu_columns_desc), v -> {
                    sheet.dismiss();
                    if (onStations != null) onStations.run();
                });
        sheet.row(activity.getString(R.string.menu_rename), stationName, v -> {
            sheet.dismiss();
            if (onRename != null) onRename.run();
        });
        sheet.row(activity.getString(R.string.menu_update),
                activity.getString(R.string.menu_update_desc, BuildConfig.VERSION_NAME), v -> {
                    sheet.dismiss();
                    if (onUpdate != null) onUpdate.run();
                });
        sheet.row(activity.getString(R.string.menu_browser), stationUrl, v -> {
            sheet.dismiss();
            if (onBrowser != null) onBrowser.run();
        });
        sheet.negative(activity.getString(R.string.action_close));
        sheet.show();
    }
}
