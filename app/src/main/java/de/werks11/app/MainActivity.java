package de.werks11.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private WebView web;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setSupportMultipleWindows(false);

        web.addJavascriptInterface(new Net(), "AndroidNet");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    // Seiten bleiben in der App (eigener Lesebereich)
                    openInApp(uri.toString());
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                deliverPending();
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl("file:///android_asset/index.html");
        }
        takeArticle(getIntent());
        try { Notifier.createChannels(this); DailyFact.schedule(getApplicationContext()); } catch (Exception ignored) { }
    }

    private boolean pageReady = false;
    private String pendingUrl = null, pendingTitle = null, pendingJs = null;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        takeArticle(intent);
    }

    /** Benachrichtigung angetippt: Artikel in der App öffnen. */
    private void takeArticle(Intent i) {
        if (i == null) return;
        if (i.hasExtra("daily_fact")) {
            pendingJs = "window.openDailyFact && window.openDailyFact(" + i.getIntExtra("daily_fact", -1) + "," + org.json.JSONObject.quote(String.valueOf(i.getStringExtra("daily_text"))) + ")";
            i.removeExtra("daily_fact");
            deliverPending();
        }
        if (i.hasExtra("open_tab")) {
            pendingJs = "window.openTabNative && window.openTabNative(" + org.json.JSONObject.quote(String.valueOf(i.getStringExtra("open_tab"))) + ")";
            i.removeExtra("open_tab");
            deliverPending();
        }
        String u = i.getStringExtra("article_url");
        if (u == null) return;
        pendingUrl = u;
        pendingTitle = i.getStringExtra("article_title");
        i.removeExtra("article_url");
        deliverPending();
    }

    private void deliverPending() {
        if (pageReady && pendingJs != null) {
            final String pj = pendingJs; pendingJs = null;
            web.postDelayed(() -> web.evaluateJavascript(pj, null), 700);
        }
        if (!pageReady || pendingUrl == null) return;
        String js = "window.openArticleUrl && window.openArticleUrl(" + org.json.JSONObject.quote(pendingUrl) + "," + org.json.JSONObject.quote(pendingTitle == null ? "" : pendingTitle) + ")";
        pendingUrl = null;
        web.postDelayed(() -> web.evaluateJavascript(js, null), 600);
    }

    void openInApp(String url) {
        Intent r = new Intent(this, ReaderActivity.class);
        r.putExtra("url", url);
        startActivity(r);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Widgets beim Öffnen der App gleich mit aktualisieren
        pool.execute(() -> { try { NextMatchWidget.refreshAll(getApplicationContext()); } catch (Exception ignored) { } });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        pool.shutdownNow();
        web.destroy();
        super.onDestroy();
    }

    /** Lädt Webseiten direkt übers Handy – ohne die Sperren eines Browsers. */
    public class Net {
        @JavascriptInterface
        public String notifyStatus() {
            try {
                android.content.SharedPreferences p = Notifier.prefs(getApplicationContext());
                android.app.job.JobScheduler js = (android.app.job.JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
                boolean scheduled = js != null && js.getPendingJob(Notifier.JOB_ID) != null;
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
                boolean battery = pm == null || pm.isIgnoringBatteryOptimizations(getPackageName());
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("lastCheck", p.getLong("lastCheck", 0)); o.put("lastSent", p.getInt("lastSent", 0)); o.put("lastErr", p.getString("lastErr", ""));
                o.put("exact", DailyFact.canExact(getApplicationContext()));
                o.put("scheduled", scheduled); o.put("battery", battery); o.put("permitted", Notifier.permitted(getApplicationContext()));
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface
        public void checkNow() {
            pool.execute(() -> {
                try { Notifier.check(getApplicationContext()); } catch (Exception ignored) { }
                runOnUiThread(() -> web.evaluateJavascript("window.__notiChecked && window.__notiChecked()", null));
            });
        }

        @JavascriptInterface
        public void openBattery() {
            runOnUiThread(() -> {
                try {
                    Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception e) {
                    try { startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Exception ignored) { }
                }
            });
        }

        @JavascriptInterface
        public void openAlarm() {
            runOnUiThread(() -> {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= 31)
                        startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
                } catch (Exception ignored) { }
            });
        }

        @JavascriptInterface
        public void playJingle() {
            runOnUiThread(() -> {
                try {
                    android.media.MediaPlayer mp = android.media.MediaPlayer.create(MainActivity.this, R.raw.fakt_jingle);
                    if (mp != null) { mp.setOnCompletionListener(android.media.MediaPlayer::release); mp.start(); }
                } catch (Exception ignored) { }
            });
        }

        @JavascriptInterface
        public void testDaily() {
            if (!Notifier.permitted(getApplicationContext()) && android.os.Build.VERSION.SDK_INT >= 33) {
                runOnUiThread(() -> requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 11));
                return;
            }
            pool.execute(() -> { try { DailyFact.show(getApplicationContext()); } catch (Exception ignored) { } });
        }

        @JavascriptInterface
        public String injCache() {
            try {
                android.content.SharedPreferences p = Notifier.prefs(getApplicationContext());
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("at", p.getLong("injAt", 0));
                o.put("html", p.getString("injHtml", ""));
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface
        public void setAdblock(boolean on, boolean cookies) {
            Notifier.prefs(getApplicationContext()).edit().putBoolean("adblock", on).putBoolean("hidecookies", cookies).apply();
        }

        @JavascriptInterface
        public int adsBlocked() { return Notifier.prefs(getApplicationContext()).getInt("adsBlocked", 0); }

        @JavascriptInterface
        public int versionCode() { return Updater.versionCode(getApplicationContext()); }

        @JavascriptInterface
        public void installUpdate(final String url) {
            if (!Updater.ensurePermission(MainActivity.this)) {
                runOnUiThread(() -> web.evaluateJavascript("window.__updState && window.__updState(-1,'permission')", null));
                return;
            }
            pool.execute(() -> Updater.install(getApplicationContext(), url, (pct, st) ->
                runOnUiThread(() -> web.evaluateJavascript("window.__updState && window.__updState(" + pct + "," + org.json.JSONObject.quote(st) + ")", null))));
        }

        @JavascriptInterface
        public void share(final String title, final String url) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_SUBJECT, title);
                i.putExtra(Intent.EXTRA_TEXT, title + "\n" + url + "\n\nGeteilt aus Werks11");
                startActivity(Intent.createChooser(i, "Teilen"));
            });
        }

        @JavascriptInterface
        public void openInApp(String url) {
            if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) runOnUiThread(() -> MainActivity.this.openInApp(url));
        }

        @JavascriptInterface
        public void setNotify(String json) {
            boolean firstTime = !Notifier.prefs(getApplicationContext()).contains("lastCheck");
            Notifier.apply(getApplicationContext(), json);
            if (firstTime) pool.execute(() -> { try { Notifier.check(getApplicationContext()); } catch (Exception ignored) { } });
            try {
                org.json.JSONObject sj = new org.json.JSONObject(json);
                if ((Notifier.anyOn(sj) || sj.optBoolean("daily")) && android.os.Build.VERSION.SDK_INT >= 33
                        && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    runOnUiThread(() -> requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 11));
                }
            } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public boolean hasNotifyPermission() {
            return Notifier.permitted(getApplicationContext());
        }

        @JavascriptInterface
        public void testNotify() {
            if (!Notifier.permitted(getApplicationContext()) && android.os.Build.VERSION.SDK_INT >= 33) {
                runOnUiThread(() -> requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 11));
                return;
            }
            Notifier.show(getApplicationContext(), 99, Notifier.CH_MATCH, "Werks11", "So sehen deine Benachrichtigungen aus. Auf geht's, Werkself! ⚫🔴", null);
            pool.execute(() -> { try { Notifier.check(getApplicationContext()); } catch (Exception ignored) { } });
        }

        @JavascriptInterface
        public void get(final String id, final String url) {
            pool.execute(() -> {
                boolean ok = false;
                String payload;
                HttpURLConnection c = null;
                try {
                    if (!url.startsWith("https://")) throw new Exception("Nur https erlaubt");
                    c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(15000);
                    c.setInstanceFollowRedirects(true);
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
                    c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/json;q=0.8,*/*;q=0.7");
                    c.setRequestProperty("Cookie", "CONSENT=YES+");
                    c.setRequestProperty("Accept-Language", "de-DE,de;q=0.9");
                    int code = c.getResponseCode();
                    if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
                    InputStream in = c.getInputStream();
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                    in.close();
                    payload = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
                    ok = true;
                } catch (Exception e) {
                    payload = String.valueOf(e.getMessage()).replaceAll("[^0-9A-Za-z .:_-]", "");
                } finally {
                    if (c != null) c.disconnect();
                }
                final String js = "window.__netCb(" + quote(id) + "," + ok + ",'" + payload + "')";
                runOnUiThread(() -> web.evaluateJavascript(js, null));
            });
        }
    }

    private static String quote(String s) {
        return "'" + s.replaceAll("[^0-9A-Za-z_-]", "") + "'";
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
