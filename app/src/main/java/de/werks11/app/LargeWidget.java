package de.werks11.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/** Werks11-Widget (groß). */
public class LargeWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try { NextMatchWidget.refreshAll(context); } catch (Exception ignored) { }
            pr.finish();
        }).start();
    }
}
