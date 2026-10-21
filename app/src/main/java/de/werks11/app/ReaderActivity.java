package de.werks11.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Zeigt eine Webseite innerhalb der App an – ohne externen Browser. */
public class ReaderActivity extends Activity {

    private WebView web;
    private boolean block = true;
    private int blocked = 0;
    private TextView shield;
    private String pageHost = null;

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String url = getIntent().getStringExtra("url");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.parseColor("#D6001C"));
        bar.setPadding(dp(4), 0, dp(12), 0);

        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextColor(Color.WHITE);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Schließen");
        close.setOnClickListener(v -> finish());
        bar.addView(close, new LinearLayout.LayoutParams(dp(52), dp(52)));

        final TextView title = new TextView(this);
        title.setText("Werks11");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        block = AdBlock.on(this);
        shield = new TextView(this);
        shield.setTextColor(Color.WHITE);
        shield.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        shield.setTypeface(Typeface.DEFAULT_BOLD);
        shield.setGravity(Gravity.CENTER);
        shield.setPadding(dp(10), dp(4), dp(10), dp(4));
        android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
        pill.setColor(0x33000000); pill.setCornerRadius(dp(14));
        shield.setBackground(pill);
        shield.setContentDescription("Werbeblocker");
        shield.setOnClickListener(v -> { // Seite kaputt? Blocker für diese Seite aus- bzw. wieder einschalten
            block = !block; blocked = 0; updateShield();
            android.widget.Toast.makeText(this, block ? "Werbeblocker an" : "Werbeblocker für diese Seite aus", android.widget.Toast.LENGTH_SHORT).show();
            web.reload();
        });
        updateShield();
        bar.addView(shield, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        final ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        root.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (block && !request.isForMainFrame() && AdBlock.shouldBlock(request.getUrl(), pageHost)) {
                    blocked++;
                    if (blocked % 3 == 1) runOnUiThread(() -> updateShield());
                    return AdBlock.empty();
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String u, android.graphics.Bitmap favicon) {
                try { pageHost = android.net.Uri.parse(u).getHost(); } catch (Exception ignored) { }
            }

            @Override
            public void onPageCommitVisible(WebView view, String u) { inject(); }

            @Override
            public void onPageFinished(WebView view, String u) { inject(); updateShield(); }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String sc = request.getUrl().getScheme();
                return !("http".equals(sc) || "https".equals(sc)); // alles Weitere bleibt in der App
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                progress.setProgress(p);
                progress.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onReceivedTitle(WebView view, String t) {
                if (t != null && !t.isEmpty()) title.setText(t);
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        if (url != null) web.loadUrl(url);
    }

    private void inject() {
        if (block && web != null) web.evaluateJavascript(AdBlock.cosmeticJs(AdBlock.cookies(this)), null);
    }

    private void updateShield() {
        if (shield == null) return;
        shield.setText(block ? "🛡 " + blocked : "🛡 aus");
        shield.setAlpha(block ? 1f : .6f);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (blocked > 0) { android.content.SharedPreferences p = Notifier.prefs(this); p.edit().putInt("adsBlocked", p.getInt("adsBlocked", 0) + blocked).apply(); }
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
