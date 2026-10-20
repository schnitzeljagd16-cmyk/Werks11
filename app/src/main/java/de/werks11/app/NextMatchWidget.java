package de.werks11.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Werks11-Widget (mittel). Klein und Groß nutzen dieselben Daten. */
public class NextMatchWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try { refreshAll(context); } catch (Exception ignored) { }
            pr.finish();
        }).start();
    }

    // ---------- Daten ----------
    static class Data {
        String comp = "", n1 = "", n2 = "", i1 = "", i2 = "", when = "", left = "", big = "--:--", sub = "VS";
        String opp = "", oppIcon = "", ha = "";
        boolean live = false, found = false, hide = false;
        Bitmap b1, b2;
        List<String> form = new ArrayList<>();
        String last = "", place = "–", pts = "", news = "", newsSrc = "NEUESTE MELDUNG";
    }

    static final Map<String, Bitmap> logoCache = new HashMap<>();

    static Bitmap logo(String url) {
        if (url == null || url.isEmpty() || !url.startsWith("http")) return null;
        if (logoCache.containsKey(url)) return logoCache.get(url);
        Bitmap b = null;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Werks11");
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            byte[] bytes = out.toByteArray();
            b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length); // SVG-Wappen lassen sich nicht laden → Initialen
            if (b != null) {
                int max = 128; float sc = Math.min(1f, (float) max / Math.max(b.getWidth(), b.getHeight()));
                if (sc < 1f) b = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * sc), Math.round(b.getHeight() * sc), true);
            }
            c.disconnect();
        } catch (Exception ignored) { }
        logoCache.put(url, b);
        return b;
    }

    static String nameOf(JSONObject t) {
        String n = t.optString("shortName");
        if (n.isEmpty()) n = t.optString("teamName");
        return n.replace("Bayer 04 Leverkusen", "Leverkusen").replace("Bayer Leverkusen", "Leverkusen");
    }

    static String ini(String n) {
        String[] w = n.replaceAll("^(1\\. FC|1\\. FSV|FC|SC|SV|VfB|VfL|TSG|RB)\\s+", "").trim().split("\\s+");
        String s = w.length > 1 ? "" + w[0].charAt(0) + w[1].charAt(0) : (w[0].length() >= 2 ? w[0].substring(0, 2) : w[0]);
        return s.toUpperCase(Locale.GERMANY);
    }

    static Data load(Context c) {
        Data d = new Data();
        JSONObject settings = Notifier.settings(c);
        try {
            JSONArray arr = new JSONArray(Notifier.get("https://api.openligadb.de/getmatchesbyteamid/" + Notifier.TEAM_ID + "/5/4"));
            long now = System.currentTimeMillis();
            JSONObject next = null; long nt = Long.MAX_VALUE;
            List<JSONObject> past = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject m = arr.getJSONObject(i);
                String lg = m.optString("leagueShortcut").toLowerCase(Locale.ROOT);
                if (!lg.matches("^(bl1|dfb\\d*|uel\\d*|ucl\\d*|uecl\\d*)$")) continue; // keine Test-Ligen
                long t = Notifier.parseUtc(m.optString("matchDateTimeUTC"));
                if (m.optBoolean("matchIsFinished")) { past.add(m); continue; }
                if (t < now - 3 * 3600 * 1000L) continue;
                if (t < nt) { nt = t; next = m; }
            }
            past.sort((a, b) -> Long.compare(Notifier.parseUtc(a.optString("matchDateTimeUTC")), Notifier.parseUtc(b.optString("matchDateTimeUTC"))));
            // Form & letzte Spiele
            StringBuilder last = new StringBuilder();
            int from = Math.max(0, past.size() - 5);
            for (int i = from; i < past.size(); i++) {
                JSONObject m = past.get(i);
                int[] r = result(m);
                boolean home = m.getJSONObject("team1").optInt("teamId") == Notifier.TEAM_ID;
                int us = home ? r[0] : r[1], them = home ? r[1] : r[0];
                if (watching(settings, m.optInt("matchID"))) { d.form.add("?"); continue; }
                d.form.add(us > them ? "S" : us < them ? "N" : "U");
            }
            for (int i = past.size() - 1; i >= Math.max(0, past.size() - 3); i--) {
                JSONObject m = past.get(i);
                JSONObject t1 = m.getJSONObject("team1"), t2 = m.getJSONObject("team2");
                int[] r = result(m);
                String score = watching(settings, m.optInt("matchID")) ? "?:?" : r[0] + ":" + r[1];
                if (last.length() > 0) last.append("\n");
                last.append(nameOf(t1)).append("  ").append(score).append("  ").append(nameOf(t2));
            }
            d.last = last.toString();

            if (next != null) {
                d.found = true;
                JSONObject t1 = next.getJSONObject("team1"), t2 = next.getJSONObject("team2");
                d.n1 = nameOf(t1); d.n2 = nameOf(t2);
                d.i1 = t1.optString("teamIconUrl"); d.i2 = t2.optString("teamIconUrl");
                boolean home = t1.optInt("teamId") == Notifier.TEAM_ID;
                d.opp = home ? d.n2 : d.n1; d.oppIcon = home ? d.i2 : d.i1; d.ha = home ? "vs " : "@ ";
                String lg = next.optString("leagueShortcut");
                d.comp = Notifier.comp(lg).toUpperCase(Locale.GERMANY);
                SimpleDateFormat day = new SimpleDateFormat("EE, d. MMM", Locale.GERMANY), hm = new SimpleDateFormat("HH:mm", Locale.GERMANY);
                day.setTimeZone(TimeZone.getTimeZone("Europe/Berlin")); hm.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
                String tv = Notifier.tv(lg, nt);
                d.when = day.format(new Date(nt)) + " · " + hm.format(new Date(nt)) + " Uhr" + (tv.isEmpty() ? "" : " · " + tv);
                long diff = nt - now;
                d.hide = watching(settings, next.optInt("matchID"));
                if (diff <= 0) {
                    d.live = true; d.left = "● LIVE";
                    JSONArray goals = next.optJSONArray("goals");
                    int s1 = 0, s2 = 0;
                    if (goals != null && goals.length() > 0) { JSONObject g = goals.getJSONObject(goals.length() - 1); s1 = g.optInt("scoreTeam1"); s2 = g.optInt("scoreTeam2"); }
                    d.big = d.hide ? "? : ?" : s1 + ":" + s2;
                    long e = -diff / 60000;
                    d.sub = d.hide ? "SPOILER-SCHUTZ" : e <= 46 ? (e + 1) + "'" : e <= 62 ? "HALBZEIT" : Math.min(90, e - 16) + "'";
                } else {
                    d.big = hm.format(new Date(nt)); d.sub = "VS";
                    if (diff < 3600_000L) d.left = "in " + Math.max(1, diff / 60000) + " Min.";
                    else if (sameDay(nt, now)) d.left = "HEUTE";
                    else if (diff < 86400_000L * 2 && sameDay(nt, now + 86400_000L)) d.left = "MORGEN";
                    else d.left = "in " + Math.max(1, Math.round(diff / 86400_000.0)) + " Tagen";
                }
                d.b1 = logo(d.i1); d.b2 = logo(d.i2);
            }
        } catch (Exception e) {
            d.when = "Keine Verbindung – tippe zum Öffnen";
        }
        // Tabellenplatz
        try {
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"));
            int season = cal.get(Calendar.MONTH) >= Calendar.JULY ? cal.get(Calendar.YEAR) : cal.get(Calendar.YEAR) - 1;
            JSONArray tb = new JSONArray(Notifier.get("https://api.openligadb.de/getbltable/bl1/" + season));
            for (int i = 0; i < tb.length(); i++) {
                JSONObject r = tb.getJSONObject(i);
                if (r.optInt("teamInfoId") == Notifier.TEAM_ID) { d.place = (i + 1) + "."; d.pts = r.optInt("points") + " Punkte"; }
            }
        } catch (Exception ignored) { }
        // Neueste Meldung
        try {
            String xml = Notifier.get("https://newsfeed.kicker.de/team/bayer-04-leverkusen");
            Matcher m = Pattern.compile("(?s)<item>.*?<title>(.*?)</title>").matcher(xml);
            if (m.find()) { d.news = Notifier.unxml(m.group(1)); d.newsSrc = "KICKER · NEUESTE MELDUNG"; }
        } catch (Exception ignored) { }
        return d;
    }

    static boolean watching(JSONObject s, int id) {
        JSONArray w = s.optJSONArray("watch");
        if (w != null) for (int k = 0; k < w.length(); k++) if (w.optInt(k) == id) return true;
        return false;
    }

    static int[] result(JSONObject m) {
        JSONArray rs = m.optJSONArray("matchResults");
        int r1 = 0, r2 = 0;
        if (rs != null) for (int k = 0; k < rs.length(); k++) {
            JSONObject r = rs.optJSONObject(k);
            if (r != null && (r.optInt("resultTypeID") == 2 || k == rs.length() - 1)) { r1 = r.optInt("pointsTeam1"); r2 = r.optInt("pointsTeam2"); }
        }
        return new int[]{r1, r2};
    }

    static boolean sameDay(long a, long b) {
        Calendar x = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin")), y = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"));
        x.setTimeInMillis(a); y.setTimeInMillis(b);
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR);
    }

    // ---------- Darstellung ----------
    static void team(RemoteViews v, int logoId, int iniId, Bitmap b, String name) {
        if (b != null) { v.setImageViewBitmap(logoId, b); v.setViewVisibility(iniId, View.GONE); v.setViewVisibility(logoId, View.VISIBLE); }
        else { v.setTextViewText(iniId, ini(name)); v.setViewVisibility(iniId, View.VISIBLE); v.setViewVisibility(logoId, View.GONE); }
    }

    static void pill(RemoteViews v, Data d) {
        v.setTextViewText(R.id.w_left, d.left);
        v.setViewVisibility(R.id.w_left, d.left.isEmpty() ? View.GONE : View.VISIBLE);
        v.setInt(R.id.w_left, "setBackgroundResource", d.live ? R.drawable.widget_pill_live : R.drawable.widget_pill);
        v.setTextColor(R.id.w_left, d.live ? 0xFFFFFFFF : 0xFFD6001C);
    }

    static RemoteViews medium(Context c, Data d, int layout) {
        RemoteViews v = new RemoteViews(c.getPackageName(), layout);
        v.setTextViewText(R.id.w_comp, d.found ? d.comp : "WERKS11");
        pill(v, d);
        v.setTextViewText(R.id.w_team1, d.found ? d.n1 : "");
        v.setTextViewText(R.id.w_team2, d.found ? d.n2 : "");
        team(v, R.id.w_logo1, R.id.w_ini1, d.b1, d.n1.isEmpty() ? "?" : d.n1);
        team(v, R.id.w_logo2, R.id.w_ini2, d.b2, d.n2.isEmpty() ? "?" : d.n2);
        v.setTextViewText(R.id.w_big, d.found ? d.big : "–");
        v.setTextViewText(R.id.w_sub, d.found ? d.sub : "KEIN SPIEL");
        v.setTextViewText(R.id.w_when, d.when);
        return v;
    }

    static RemoteViews small(Context c, Data d) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_small);
        v.setTextViewText(R.id.w_comp, d.found ? d.comp : "WERKS11");
        pill(v, d);
        Bitmap b = d.oppIcon.equals(d.i1) ? d.b1 : d.b2;
        team(v, R.id.w_logo2, R.id.w_ini2, b, d.opp.isEmpty() ? "?" : d.opp);
        v.setTextViewText(R.id.w_team2, d.found ? d.ha + d.opp : "Kein Spiel");
        v.setTextViewText(R.id.w_big, d.found ? (d.live ? d.big + "  " + d.sub : d.big) : "");
        SimpleDateFormat day = new SimpleDateFormat("EE, d. MMM", Locale.GERMANY);
        String w = d.when; int p = w.indexOf(" · "); if (p > 0) w = w.substring(0, p);
        v.setTextViewText(R.id.w_when, w);
        return v;
    }

    static RemoteViews large(Context c, Data d) {
        RemoteViews v = medium(c, d, R.layout.widget_large);
        int[] ids = {R.id.w_f1, R.id.w_f2, R.id.w_f3, R.id.w_f4, R.id.w_f5};
        for (int i = 0; i < 5; i++) {
            if (i < d.form.size()) {
                String f = d.form.get(i);
                v.setTextViewText(ids[i], f);
                v.setInt(ids[i], "setBackgroundResource", f.equals("S") ? R.drawable.widget_chip_w : f.equals("U") ? R.drawable.widget_chip_d : f.equals("N") ? R.drawable.widget_chip_l : R.drawable.widget_chip_n);
                v.setViewVisibility(ids[i], View.VISIBLE);
            } else v.setViewVisibility(ids[i], View.GONE);
        }
        v.setTextViewText(R.id.w_place, d.place);
        v.setTextViewText(R.id.w_pts, d.pts);
        v.setTextViewText(R.id.w_last, d.last.isEmpty() ? "" : "Letzte Spiele\n" + d.last);
        v.setTextViewText(R.id.w_newsSrc, d.newsSrc);
        v.setTextViewText(R.id.w_news, d.news.isEmpty() ? "Öffne Werks11 für die neuesten Meldungen." : d.news);
        return v;
    }

    static void refreshAll(Context c) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(c);
        int[] mid = mgr.getAppWidgetIds(new ComponentName(c, NextMatchWidget.class));
        int[] sm = mgr.getAppWidgetIds(new ComponentName(c, SmallWidget.class));
        int[] lg = mgr.getAppWidgetIds(new ComponentName(c, LargeWidget.class));
        if (mid.length + sm.length + lg.length == 0) return;
        Data d = load(c);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 7, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        for (int id : mid) { RemoteViews v = medium(c, d, R.layout.widget_next); v.setOnClickPendingIntent(R.id.w_root, pi); mgr.updateAppWidget(id, v); }
        for (int id : sm) { RemoteViews v = small(c, d); v.setOnClickPendingIntent(R.id.w_root, pi); mgr.updateAppWidget(id, v); }
        for (int id : lg) { RemoteViews v = large(c, d); v.setOnClickPendingIntent(R.id.w_root, pi); mgr.updateAppWidget(id, v); }
    }
}
