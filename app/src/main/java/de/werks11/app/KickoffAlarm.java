package de.werks11.app;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

/** Erinnert genau eine Stunde vor Anpfiff – mit einem lustigen Spruch von Butti zum Gegner. */
public class KickoffAlarm extends BroadcastReceiver {

    static final String ACTION = "de.werks11.app.KICKOFF";
    static final long BEFORE = 60 * 60 * 1000L;

    // [Muster im Vereinsnamen, Sprüche …]  {opp} = Gegnername
    static final String[][] LINES = {
        {"köln|koeln", "Rheinderby! Butti hat seinen Plan gegen den Geißbock fertig: meckern lassen und Tore schießen. 🐐",
                "Heute klären wir am Rhein, wer der König ist – der Löwe natürlich! 🦁", "Derbyzeit! Butti hat die Mähne extra rot-schwarz gefönt."},
        {"bayern|münchen|muenchen", "Gegen den Rekordmeister! Butti weiß seit 2024: Meisterschalen passen auch nach Leverkusen. 🏆",
                "Lederhosen bleiben heute im Schrank – die Werkself trägt Rot-Schwarz!", "Butti hat gehört, in München gibt's Weißwurst. Er nimmt lieber drei Punkte."},
        {"dortmund|bvb", "Gelb ist heute nur die Farbe von Buttis Mähne. 🦁", "Die Gelbe Wand ist groß – aber Buttis Brüllen ist lauter!"},
        {"leipzig", "Löwe gegen Bulle – laut Butti steht im Tierlexikon, wer da gewinnt. 🐂", "Rote Bullen? Butti sieht heute nur Rot-Schwarz."},
        {"stuttgart|vfb", "Gegen die Schwaben: Butti hat auf Spätzle verzichtet – leicht und schnell muss er heute sein!", "Butti schaut, ob in Stuttgart noch Platz im Pokalregal ist. 😉"},
        {"frankfurt|eintracht", "Gegen die Adler: Löwen können zwar nicht fliegen, aber Tore schießen! 🦅", "Butti hat gehört, der Adler heißt Attila. Klingt gefährlich – Butti brüllt trotzdem."},
        {"freiburg", "Ab gegen den Breisgau: Butti hat Sonnencreme eingepackt – in Freiburg scheint ja angeblich immer die Sonne. ☀️", "Freiburg spielt clever – Butti hat extra ein Taktikbuch gelesen. Na ja, durchgeblättert."},
        {"gladbach|mönchengladbach|borussia m", "Gegen die Fohlen: Ein Löwe lässt sich von Pferdchen nicht abhängen! 🐎", "Fohlen sind flink – aber Butti hat heute Turbo-Pfoten."},
        {"wolfsburg", "Wölfe gegen Löwe! Im Werksclub-Duell entscheidet heute das lautere Brüllen. 🐺", "Butti heult heute nicht mit den Wölfen – er brüllt!"},
        {"mainz", "Mainz bleibt Mainz – aber die drei Punkte bleiben in Leverkusen! 🎭", "Helau? Butti feiert erst nach dem Abpfiff Karneval."},
        {"bremen|werder", "Gegen Werder denkt Butti gern an das 5:0 am Meistertag 2024 zurück. 🐟", "Fisch auf dem Wappen? Butti hat Hunger – auf Tore!"},
        {"augsburg", "Gegen die Fuggerstädter: Butti hat sein Kleingeld gezählt – reicht genau für drei Punkte!", "Augsburg kämpft immer – Butti hat seine Grätschen-Pfoten ausgepackt."},
        {"hoffenheim|tsg", "Gegen Hoffenheim prüft Butti vorher ganz genau das Seitennetz. Man weiß ja nie … 😉", "Sinsheim ruft! Butti erinnert sich an ein gewisses Phantomtor."},
        {"union berlin|fc union", "Eisern Union? Butti ist zwar nur aus Fell – aber heute hart wie Eisen!", "Butti hat gehört, die Unioner singen laut. Er brüllt einfach lauter."},
        {"hamburg|hsv", "Gegen den Dino: Butti hat keine Angst vor Dinosauriern! 🦖", "Butti grüßt die Elbe und schnappt sich die Punkte."},
        {"st. pauli|pauli", "Totenkopf gegen Löwenkopf – Butti ist bereit! 🏴‍☠️", "Butti mag den Kiez – aber die Punkte nimmt er trotzdem mit."},
        {"heidenheim", "Heidenheim kämpft immer bis zum Schluss – Butti auch!", "Butti hat gehört, Heidenheim liegt auf der Ostalb. Höhentraining hat er gemacht."},
        {"schalke", "Königsblau? Hier gibt's nur einen König – den Löwen! 👑", "Butti erinnert sich an das Pokalfinale 2002 – heute gibt's Revanche!"},
        {"hertha", "Gegen die alte Dame: Butti hält ihr höflich die Tür auf – und schießt dann ein.", "Butti denkt an 1993: Pokalsieg in Berlin. Gute Erinnerungen!"},
        {"bochum", "Gegen Bochum: Butti singt schon mal vor – aber erst nach dem Sieg."},
        {"kaiserslautern|lautern", "Gegen die Roten Teufel denkt Butti ans Pokalfinale 2024. Das Double! 🏆"},
        {"real madrid", "Real Madrid! Butti hat noch eine Rechnung von 2002 offen. 😤"},
        {"atalanta|bergamo", "Atalanta! Butti will endlich die Revanche fürs Finale in Dublin."},
    };
    static final String[] GENERIC = {
        "Gleich gegen {opp}! Butti hat die Handschuhe an und die Mähne gekämmt. 🦁",
        "Gegen {opp}: Butti trägt seine Glücksstutzen – heute klappt's!",
        "{opp}? Butti brüllt schon mal vor: Werkself, Werkself! 🔴⚫",
        "Butti hat {opp} genau studiert. Ergebnis: Wir gewinnen. Glaubt er zumindest. 😄",
        "Noch eine Stunde! Butti macht sich warm – gegen {opp} wird's spannend.",
        "Gegen {opp} gilt: Abwehr dicht, Pfoten warm, Tore schießen!"
    };

    static String line(String opp, int seed) {
        String o = opp.toLowerCase(Locale.GERMAN);
        Random r = new Random(seed);
        for (String[] l : LINES) {
            if (java.util.regex.Pattern.compile(l[0]).matcher(o).find() && r.nextInt(4) != 0) return l[1 + r.nextInt(l.length - 1)];
        }
        return GENERIC[r.nextInt(GENERIC.length)].replace("{opp}", opp);
    }

    /** Plant für die nächsten Spiele (aus den App-Einstellungen) je einen Wecker eine Stunde vor Anpfiff. */
    static void scheduleAll(Context c) {
        JSONObject s = Notifier.settings(c);
        JSONArray up = s.optJSONArray("upcoming");
        if (up == null) return;
        for (int i = 0; i < up.length(); i++) {
            JSONObject m = up.optJSONObject(i);
            if (m != null) schedule(c, m, s.optBoolean("kickoff", true));
        }
    }

    static PendingIntent pending(Context c, JSONObject m) {
        Intent i = new Intent(c, KickoffAlarm.class).setAction(ACTION).putExtra("match", m.toString());
        return PendingIntent.getBroadcast(c, 50000 + (m.optInt("id") % 100000), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c, JSONObject m, boolean on) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pending(c, m);
        am.cancel(pi);
        long t = m.optLong("t"), now = System.currentTimeMillis();
        if (!on || t <= now) return;
        long at = t - BEFORE;
        if (at <= now) { // App erst kurz vor dem Spiel geöffnet: sofort erinnern (falls noch nicht geschehen)
            if (t - now > 10 * 60 * 1000L) new Thread(() -> show(c, m)).start();
            return;
        }
        try {
            if (DailyFact.canExact(c)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else am.setWindow(AlarmManager.RTC_WAKEUP, at - 5 * 60 * 1000L, 5 * 60 * 1000L, pi);
        } catch (SecurityException e) {
            am.setWindow(AlarmManager.RTC_WAKEUP, at - 5 * 60 * 1000L, 5 * 60 * 1000L, pi);
        }
    }

    @Override
    public void onReceive(Context c, Intent i) {
        if (i == null || !ACTION.equals(i.getAction())) return;
        try {
            JSONObject m = new JSONObject(i.getStringExtra("match"));
            final PendingResult pr = goAsync();
            new Thread(() -> { try { show(c, m); } finally { pr.finish(); } }).start();
        } catch (Exception ignored) { }
    }

    /** Zeigt die Erinnerung – höchstens einmal pro Spiel. */
    static synchronized void show(Context c, JSONObject m) {
        try {
            JSONObject s = Notifier.settings(c);
            if (!s.optBoolean("kickoff", true) || !Notifier.permitted(c)) return;
            int id = m.optInt("id");
            SharedPreferences p = Notifier.prefs(c);
            if (p.getBoolean("ko_" + id, false)) return;
            long t = m.optLong("t");
            if (t < System.currentTimeMillis()) return;
            p.edit().putBoolean("ko_" + id, true).apply();
            boolean home = m.optBoolean("home");
            String t1 = m.optString("t1"), t2 = m.optString("t2"), opp = home ? t2 : t1, lg = m.optString("sc");
            SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.GERMANY);
            hm.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
            String tv = Notifier.tv(lg, t), comp = Notifier.comp(lg);
            String title = "⚽ Gleich geht's los: " + t1 + " – " + t2;
            String info = (comp.isEmpty() ? "" : comp + " · ") + "Anpfiff " + hm.format(new Date(t)) + " Uhr" + (tv.isEmpty() ? "" : " · live bei " + tv);
            String joke = line(opp, id);
            Notifier.createChannels(c);
            Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("open_match", id);
            PendingIntent pi = PendingIntent.getActivity(c, 60000 + id % 100000, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new android.app.Notification.Builder(c, Notifier.CH_MATCH) : new android.app.Notification.Builder(c);
            b.setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(joke)
                    .setStyle(new android.app.Notification.BigTextStyle().bigText(joke + "\n" + info))
                    .setColor(0xFFD6001C).setAutoCancel(true).setContentIntent(pi);
            try { b.setLargeIcon(BitmapFactory.decodeResource(c.getResources(), R.drawable.lion_head)); } catch (Exception ignored) { }
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(100000 + id % 100000, b.build());
        } catch (Exception ignored) { }
    }
}
