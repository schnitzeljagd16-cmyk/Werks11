package de.werks11.app;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Lädt eine neue Werks11-Version herunter und installiert sie über den Android-Installer. */
public class Updater {

    interface Progress { void on(int percent, String state); }

    static int versionCode(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode; } catch (Exception e) { return 0; }
    }

    /** true, wenn Werks11 Apps installieren darf; sonst öffnet sich die passende Einstellung. */
    static boolean ensurePermission(Activity a) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName()));
            a.startActivity(i);
            return false;
        }
        return true;
    }

    static void install(Context c, String url, Progress p) {
        File f = new File(c.getCacheDir(), "werks11-update.apk");
        try {
            // 1) Herunterladen (GitHub leitet auf einen Download-Server weiter)
            String u = url;
            HttpURLConnection con = null;
            for (int hop = 0; hop < 6; hop++) {
                con = (HttpURLConnection) new URL(u).openConnection();
                con.setInstanceFollowRedirects(false);
                con.setConnectTimeout(15000); con.setReadTimeout(30000);
                con.setRequestProperty("User-Agent", "Werks11-Updater");
                int code = con.getResponseCode();
                if (code >= 300 && code < 400) { u = new URL(new URL(u), con.getHeaderField("Location")).toString(); con.disconnect(); continue; }
                if (code != 200) throw new Exception("HTTP " + code);
                break;
            }
            long total = con.getContentLengthLong(), done = 0; int last = -1;
            try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(f)) {
                byte[] buf = new byte[32768]; int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n); done += n;
                    int pct = total > 0 ? (int) (done * 100 / total) : -1;
                    if (pct != last) { last = pct; p.on(pct, "download"); }
                }
            }
            con.disconnect();
            p.on(100, "install");
            // 2) Installieren
            PackageInstaller pi = c.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            int sid = pi.createSession(params);
            try (PackageInstaller.Session s = pi.openSession(sid)) {
                try (InputStream in = new FileInputStream(f); OutputStream out = s.openWrite("werks11", 0, f.length())) {
                    byte[] buf = new byte[65536]; int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                    s.fsync(out);
                }
                Intent cb = new Intent(c, InstallReceiver.class);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                PendingIntent pend = PendingIntent.getBroadcast(c, 4711, cb, flags);
                s.commit(pend.getIntentSender());
            }
        } catch (Exception e) {
            p.on(-1, "error:" + String.valueOf(e.getMessage()).replaceAll("[^0-9A-Za-z .:_-]", ""));
        }
    }
}
