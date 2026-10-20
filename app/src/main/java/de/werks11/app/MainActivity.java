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
                    // Artikel im normalen Browser öffnen
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    return true;
                }
                return false;
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl("file:///android_asset/index.html");
        }
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
        public void setNotify(String json) {
            Notifier.apply(getApplicationContext(), json);
            try {
                if (Notifier.anyOn(new org.json.JSONObject(json)) && android.os.Build.VERSION.SDK_INT >= 33
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
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Werks11/1.0");
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
