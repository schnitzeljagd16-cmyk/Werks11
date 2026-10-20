package de.werks11.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Prüft im Hintergrund auf Neuigkeiten und Spielereignisse und zeigt Benachrichtigungen. */
public class Notifier {

    static final String PREFS = "werks11";
    static final String CH_MATCH = "spiele";
    static final String CH_NEWS = "news";
    static final int JOB_ID = 1104;
    static final int TEAM_ID = 6;

    static final Pattern RUMOR = Pattern.compile("transfer|gerücht|interesse|wechsel|leihe|ablöse|buhl|kandidat|im visier|angebot|abgang|neuzugang|poker|kaufoption|klausel", Pattern.CASE_INSENSITIVE);
    static final Pattern RELEVANT = Pattern.compile("leverkusen|bayer 04|werkself", Pattern.CASE_INSENSITIVE);
    static final Pattern SKIP = Pattern.compile("frauen|u19|u17|u23|basketball|handball", Pattern.CASE_INSENSITIVE);

    // ---------- Einstellungen & Planung ----------
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static JSONObject settings(Context c) {
        try { return new JSONObject(prefs(c).getString("settings", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    static boolean anyOn(JSONObject s) {
        return s.optBoolean("news") || s.optBoolean("rumors") || s.optBoolean("kickoff") || s.optBoolean("goals");
    }

    static void apply(Context c, String json) {
        prefs(c).edit().putString("settings", json).apply();
        createChannels(c);
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        try {
            if (anyOn(new JSONObject(json))) {
                JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, NotifyJob.class))
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPeriodic(15 * 60 * 1000L)
                        .setPersisted(true)
                        .build();
                JobInfo existing = null;
                for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == JOB_ID) existing = j;
                if (existing == null) js.schedule(job);
            } else {
                js.cancel(JOB_ID);
            }
        } catch (Exception ignored) { }
    }

    static void createChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel m = new NotificationChannel(CH_MATCH, "Spiele & Tore", NotificationManager.IMPORTANCE_HIGH);
        m.setDescription("Anpfiff, Tore und Endstand");
        nm.createNotificationChannel(m);
        NotificationChannel n = new NotificationChannel(CH_NEWS, "Nachrichten & Gerüchte", NotificationManager.IMPORTANCE_DEFAULT);
        n.setDescription("Neue Meldungen rund um Bayer 04");
        nm.createNotificationChannel(n);
    }

    static boolean permitted(Context c) {
        if (Build.VERSION.SDK_INT >= 33) {
            return c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm == null || nm.areNotificationsEnabled();
    }

    static void show(Context c, int id, String channel, String title, String text, String url) {
        if (!permitted(c)) return;
        createChannels(c);
        Intent open = (url != null && url.startsWith("https://"))
                ? new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                : new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(c, channel)
                : new android.app.Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(text))
                .setColor(0xFFD6001C)
                .setAutoCancel(true)
                .setContentIntent(pi);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(id, b.build());
    }

    // ---------- Netzwerk ----------
    static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Werks11/1.2");
        c.setRequestProperty("Accept-Language", "de-DE,de;q=0.9");
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    static String unxml(String s) {
        s = s.replaceAll("(?s)<!\\[CDATA\\[(.*?)\\]\\]>", "$1");
        return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").trim();
    }

    static List<String[]> rss(String xml) {
        List<String[]> out = new ArrayList<>();
        Matcher m = Pattern.compile("(?s)<item>(.*?)</item>").matcher(xml);
        while (m.find()) {
            String it = m.group(1);
            String t = find(it, "title"), l = find(it, "link"), src = find(it, "source");
            if (t.isEmpty()) continue;
            Matcher gm = Pattern.compile("^(.*)\\s+-\\s+([^-]{2,60})$").matcher(t);
            if (src.isEmpty() && gm.find()) { src = gm.group(2).trim(); t = gm.group(1).trim(); }
            else if (gm.find(0)) { t = gm.group(1).trim(); }
            out.add(new String[]{t, l, src});
        }
        return out;
    }

    static String find(String s, String tag) {
        Matcher m = Pattern.compile("(?s)<" + tag + "(?:\\s[^>]*)?>(.*?)</" + tag + ">").matcher(s);
        return m.find() ? unxml(m.group(1)) : "";
    }

    static String key(String title) {
        return title.toLowerCase(Locale.GERMAN).replaceAll("[^a-zäöüß0-9]", "");
    }

    // ---------- Prüfen ----------
    static void check(Context c) {
        JSONObject s = settings(c);
        if (!anyOn(s)) return;
        if (s.optBoolean("news") || s.optBoolean("rumors")) { try { checkNews(c, s); } catch (Exception ignored) { } }
        if (s.optBoolean("kickoff") || s.optBoolean("goals")) { try { checkMatches(c, s); } catch (Exception ignored) { } }
    }

    static void checkNews(Context c, JSONObject s) throws Exception {
        SharedPreferences p = prefs(c);
        Set<String> seen = new HashSet<>(p.getStringSet("seen", new HashSet<>()));
        boolean first = seen.isEmpty();
        List<String[]> items = new ArrayList<>();
        String[] feeds = {
                "https://newsfeed.kicker.de/team/bayer-04-leverkusen",
                "https://news.google.com/rss/search?q=" + Uri.encode("\"Bayer Leverkusen\" when:1d") + "&hl=de&gl=DE&ceid=DE:de",
                "https://news.google.com/rss/search?q=" + Uri.encode("Leverkusen Transfer when:2d") + "&hl=de&gl=DE&ceid=DE:de"
        };
        for (int i = 0; i < feeds.length; i++) {
            try {
                for (String[] it : rss(get(feeds[i]))) {
                    if (i > 0 && !RELEVANT.matcher(it[0]).find()) continue;
                    if (SKIP.matcher(it[0]).find()) continue;
                    if (i == 0 && it[2].isEmpty()) it[2] = "kicker";
                    items.add(it);
                }
            } catch (Exception ignored) { }
        }
        int sent = 0;
        Set<String> next = new HashSet<>(seen);
        for (String[] it : items) {
            String k = key(it[0]);
            if (k.length() > 60) k = k.substring(0, 60);
            if (seen.contains(k)) continue;
            next.add(k);
            if (first || sent >= 3) continue;
            boolean rumor = RUMOR.matcher(it[0]).find();
            if (s.optBoolean("news") || (rumor && s.optBoolean("rumors"))) {
                show(c, 2000 + Math.abs(k.hashCode() % 5000), CH_NEWS, rumor ? "Gerüchteküche · " + it[2] : "Werks11 · " + it[2], it[0], it[1]);
                sent++;
            }
        }
        // Liste klein halten
        if (next.size() > 400) { List<String> l = new ArrayList<>(next); next = new HashSet<>(l.subList(l.size() - 300, l.size())); }
        p.edit().putStringSet("seen", next).apply();
    }

    static long parseUtc(String iso) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(iso.replace("Z", "")).getTime();
        } catch (Exception e) { return 0; }
    }

    static String tv(String league, long t) {
        String lg = league.toLowerCase(Locale.ROOT);
        java.util.Calendar cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"));
        cal.setTimeInMillis(t);
        int wd = cal.get(java.util.Calendar.DAY_OF_WEEK), hr = cal.get(java.util.Calendar.HOUR_OF_DAY);
        if (lg.startsWith("bl1")) {
            if (wd == java.util.Calendar.SUNDAY) return "DAZN";
            if (wd == java.util.Calendar.SATURDAY && hr < 17) return "Sky/WOW (Konferenz: DAZN)";
            return "Sky/WOW";
        }
        if (lg.startsWith("dfb")) return "Sky";
        if (lg.startsWith("uecl")) return "RTL+";
        if (lg.startsWith("uel")) return "RTL+";
        if (lg.startsWith("ucl")) return "DAZN";
        return "";
    }

    static String comp(String league) {
        String lg = league.toLowerCase(Locale.ROOT);
        if (lg.startsWith("bl1")) return "Bundesliga";
        if (lg.startsWith("dfb")) return "DFB-Pokal";
        if (lg.startsWith("uecl")) return "Conference League";
        if (lg.startsWith("uel")) return "Europa League";
        if (lg.startsWith("ucl")) return "Champions League";
        return "";
    }

    static void checkMatches(Context c, JSONObject s) throws Exception {
        SharedPreferences p = prefs(c);
        JSONArray arr = new JSONArray(get("https://api.openligadb.de/getmatchesbyteamid/" + TEAM_ID + "/1/1"));
        long now = System.currentTimeMillis();
        SharedPreferences.Editor ed = p.edit();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject m = arr.getJSONObject(i);
            int id = m.optInt("matchID");
            long t = parseUtc(m.optString("matchDateTimeUTC"));
            String lg = m.optString("leagueShortcut");
            JSONObject t1 = m.optJSONObject("team1"), t2 = m.optJSONObject("team2");
            if (t1 == null || t2 == null) continue;
            String n1 = t1.optString("shortName", t1.optString("teamName")), n2 = t2.optString("shortName", t2.optString("teamName"));
            if (n1.isEmpty()) n1 = t1.optString("teamName");
            if (n2.isEmpty()) n2 = t2.optString("teamName");
            boolean fin = m.optBoolean("matchIsFinished");
            boolean watching = false;
            JSONArray w = s.optJSONArray("watch");
            if (w != null) for (int k = 0; k < w.length(); k++) if (w.optInt(k) == id) watching = true;

            // Anpfiff-Erinnerung (ca. 1 Std. vorher)
            if (s.optBoolean("kickoff") && !fin && t - now > 0 && t - now <= 80 * 60 * 1000L && !p.getBoolean("ko_" + id, false)) {
                SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.GERMANY);
                hm.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
                String tvs = tv(lg, t);
                show(c, 100000 + id % 100000, CH_MATCH, "Gleich geht's los! " + comp(lg),
                        n1 + " – " + n2 + " um " + hm.format(new Date(t)) + " Uhr" + (tvs.isEmpty() ? "" : " · live bei " + tvs), null);
                ed.putBoolean("ko_" + id, true);
            }

            if (!s.optBoolean("goals") || t > now || now - t > 4 * 3600 * 1000L) continue;
            JSONArray goals = m.optJSONArray("goals");
            int known = p.getInt("g_" + id, -1);
            int count = goals == null ? 0 : goals.length();
            if (known < 0) { // erste Prüfung während des Spiels: nichts nachmelden außer laufendem Stand
                known = 0;
            }
            for (int g = known; g < count && !watching; g++) {
                JSONObject go = goals.getJSONObject(g);
                int s1 = go.optInt("scoreTeam1"), s2 = go.optInt("scoreTeam2");
                boolean us = go.optInt("scoringTeamId", 0) == TEAM_ID;
                if (go.optInt("scoringTeamId", 0) == 0) {
                    int prev1 = g > 0 ? goals.getJSONObject(g - 1).optInt("scoreTeam1") : 0;
                    boolean team1 = s1 > prev1;
                    us = (team1 ? t1.optInt("teamId") : t2.optInt("teamId")) == TEAM_ID;
                }
                String who = go.optString("goalGetterName", "");
                String min = go.isNull("matchMinute") ? "" : go.optInt("matchMinute") + ". Min.";
                show(c, 200000 + id % 100000 * 10 + g, CH_MATCH, us ? "TOOOR für Bayer 04! " + s1 + ":" + s2 : "Gegentor · " + s1 + ":" + s2,
                        n1 + " " + s1 + ":" + s2 + " " + n2 + (who.isEmpty() ? "" : " – " + who) + (min.isEmpty() ? "" : " (" + min + ")"), null);
            }
            ed.putInt("g_" + id, count);

            if (fin && watching) ed.putBoolean("ft_" + id, true);
            if (fin && !p.getBoolean("ft_" + id, false)) {
                JSONArray rs = m.optJSONArray("matchResults");
                int r1 = 0, r2 = 0;
                if (rs != null) for (int k = 0; k < rs.length(); k++) {
                    JSONObject r = rs.getJSONObject(k);
                    if (r.optInt("resultTypeID") == 2 || k == rs.length() - 1) { r1 = r.optInt("pointsTeam1"); r2 = r.optInt("pointsTeam2"); }
                }
                boolean home = t1.optInt("teamId") == TEAM_ID;
                int us = home ? r1 : r2, them = home ? r2 : r1;
                String head = us > them ? "Sieg! 🎉" : us < them ? "Niederlage" : "Unentschieden";
                show(c, 300000 + id % 100000, CH_MATCH, "Abpfiff · " + head, n1 + " " + r1 + ":" + r2 + " " + n2 + " (" + comp(lg) + ")", null);
                ed.putBoolean("ft_" + id, true);
            }
        }
        ed.apply();
    }
}
