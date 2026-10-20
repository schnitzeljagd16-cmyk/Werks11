package de.werks11.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Startbildschirm-Widget: nächstes Spiel mit Countdown. */
public class NextMatchWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try { refreshAll(context); } catch (Exception ignored) { }
            pr.finish();
        }).start();
    }

    static void refreshAll(Context c) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(c);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(c, NextMatchWidget.class));
        if (ids == null || ids.length == 0) return;
        String comp = "Werks11", teams = "Kein Spiel gefunden", when = "", left = "";
        try {
            JSONArray arr = new JSONArray(Notifier.get("https://api.openligadb.de/getmatchesbyteamid/" + Notifier.TEAM_ID + "/0/4"));
            long now = System.currentTimeMillis();
            JSONObject next = null; long nt = Long.MAX_VALUE;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject m = arr.getJSONObject(i);
                long t = Notifier.parseUtc(m.optString("matchDateTimeUTC"));
                if (m.optBoolean("matchIsFinished") || t < now - 3 * 3600 * 1000L) continue;
                if (t < nt) { nt = t; next = m; }
            }
            if (next != null) {
                JSONObject t1 = next.getJSONObject("team1"), t2 = next.getJSONObject("team2");
                String n1 = t1.optString("shortName"), n2 = t2.optString("shortName");
                if (n1.isEmpty()) n1 = t1.optString("teamName");
                if (n2.isEmpty()) n2 = t2.optString("teamName");
                comp = Notifier.comp(next.optString("leagueShortcut"));
                teams = n1 + " – " + n2;
                SimpleDateFormat f = new SimpleDateFormat("EE, d. MMM · HH:mm 'Uhr'", Locale.GERMANY);
                f.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
                String tv = Notifier.tv(next.optString("leagueShortcut"), nt);
                when = f.format(new Date(nt)) + (tv.isEmpty() ? "" : " · " + tv);
                long d = nt - now;
                if (d <= 0) left = "LÄUFT";
                else if (d < 3600_000L) left = "in " + Math.max(1, d / 60000) + " Min.";
                else if (d < 86400_000L) left = "in " + (d / 3600_000L) + " Std.";
                else { long days = d / 86400_000L; left = days == 1 ? "morgen" : "in " + days + " Tagen"; }
            }
        } catch (Exception e) {
            teams = "Keine Verbindung";
        }
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 7, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        for (int id : ids) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_next);
            v.setTextViewText(R.id.w_comp, comp.isEmpty() ? "Nächstes Spiel" : comp.toUpperCase(Locale.GERMANY));
            v.setTextViewText(R.id.w_teams, teams);
            v.setTextViewText(R.id.w_when, when);
            v.setTextViewText(R.id.w_left, left);
            v.setOnClickPendingIntent(R.id.w_root, pi);
            mgr.updateAppWidget(id, v);
        }
    }
}
