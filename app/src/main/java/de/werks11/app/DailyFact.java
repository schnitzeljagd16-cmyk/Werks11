package de.werks11.app;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Jeden Tag um 19:04 Uhr: Buttis Fakt des Tages – mit eigenem Jingle. */
public class DailyFact extends BroadcastReceiver {

    static final String ACTION_FIRE = "de.werks11.app.DAILY_FACT";
    static final String CH_FACT = "fakt_jingle";
    static final int HOUR = 19, MINUTE = 4, NOTI_ID = 1904;

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i == null ? null : i.getAction();
        if (ACTION_FIRE.equals(a)) {
            final PendingResult pr = goAsync();
            new Thread(() -> {
                try {
                    String today = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(new java.util.Date());
                    SharedPreferences p = Notifier.prefs(c);
                    if (enabled(c) && !today.equals(p.getString("dailyLast", ""))) {
                        show(c);
                        p.edit().putString("dailyLast", today).apply();
                    }
                } catch (Exception ignored) { }
                schedule(c);
                pr.finish();
            }).start();
        } else {
            // Neustart, App-Update oder Zeitumstellung: Wecker neu stellen
            schedule(c);
            try { KickoffAlarm.scheduleAll(c); } catch (Exception ignored) { }
        }
    }

    static boolean enabled(Context c) {
        return Notifier.settings(c).optBoolean("daily", true);
    }

    static PendingIntent pending(Context c) {
        Intent i = new Intent(c, DailyFact.class).setAction(ACTION_FIRE);
        return PendingIntent.getBroadcast(c, 1904, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static boolean canExact(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pending(c);
        am.cancel(pi);
        if (!enabled(c)) return;
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, HOUR);
        cal.set(Calendar.MINUTE, MINUTE);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 2000) cal.add(Calendar.DAY_OF_YEAR, 1);
        long t = cal.getTimeInMillis();
        try {
            if (canExact(c)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            else am.setWindow(AlarmManager.RTC_WAKEUP, t, 3 * 60 * 1000L, pi);
        } catch (SecurityException e) {
            am.setWindow(AlarmManager.RTC_WAKEUP, t, 3 * 60 * 1000L, pi);
        }
    }

    static String asset(Context c, String name) throws Exception {
        InputStream in = c.getAssets().open(name);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    /** Sucht den Fakt für heute aus: zuerst „Heute vor … Jahren“, sonst der nächste noch nicht gezeigte. */
    static void show(Context c) throws Exception {
        if (!Notifier.permitted(c)) return;
        JSONObject data = new JSONObject(asset(c, "facts.json"));
        JSONArray facts = data.getJSONArray("facts"), days = data.optJSONArray("days");
        Calendar now = Calendar.getInstance();
        String md = String.format(Locale.ROOT, "-%02d-%02d", now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH));
        String title, text, more = "";
        int idx = -1;
        String dayText = null;
        if (days != null) for (int k = 0; k < days.length(); k++) {
            JSONArray d = days.getJSONArray(k);
            if (d.getString(0).endsWith(md)) {
                int yrs = now.get(Calendar.YEAR) - Integer.parseInt(d.getString(0).substring(0, 4));
                dayText = "Heute vor " + yrs + " Jahren: " + d.getString(1);
            }
        }
        if (dayText != null) {
            title = "🦁 Butti erinnert sich";
            text = dayText;
        } else {
            SharedPreferences p = Notifier.prefs(c);
            List<Integer> order = new ArrayList<>();
            try { JSONArray o = new JSONArray(p.getString("dailyOrder", "[]")); for (int k = 0; k < o.length(); k++) order.add(o.getInt(k)); } catch (Exception ignored) { }
            if (order.size() != facts.length()) {
                order.clear();
                for (int k = 0; k < facts.length(); k++) order.add(k);
                Collections.shuffle(order);
                p.edit().putInt("dailyPos", 0).apply();
            }
            int pos = p.getInt("dailyPos", 0);
            if (pos >= order.size()) { Collections.shuffle(order); pos = 0; }
            idx = order.get(pos);
            p.edit().putString("dailyOrder", new JSONArray(order).toString()).putInt("dailyPos", pos + 1).apply();
            JSONArray f = facts.getJSONArray(idx);
            boolean funny = f.getString(0).toLowerCase(Locale.GERMAN).contains("anekdote");
            title = funny ? "😂 Buttis Anekdote des Tages" : "🦁 Buttis Fakt des Tages";
            text = f.getString(1);
            more = f.optString(2, "");
        }
        Notifier.createChannels(c);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        open.putExtra("daily_fact", idx);
        open.putExtra("daily_text", text);
        PendingIntent pi = PendingIntent.getActivity(c, NOTI_ID, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(c, CH_FACT)
                : new android.app.Notification.Builder(c);
        String big = more.isEmpty() ? text : text + "\n\n" + (more.length() > 260 ? more.substring(0, 257) + " …" : more);
        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(big))
                .setColor(0xFFD6001C)
                .setAutoCancel(true)
                .setContentIntent(pi);
        try { b.setLargeIcon(BitmapFactory.decodeResource(c.getResources(), R.drawable.lion_head)); } catch (Exception ignored) { }
        if (Build.VERSION.SDK_INT < 26) b.setSound(jingle(c));
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTI_ID, b.build());
    }

    static Uri jingle(Context c) {
        return Uri.parse("android.resource://" + c.getPackageName() + "/" + R.raw.fakt_jingle);
    }
}
