package de.werks11.app;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Einfacher Werbe- und Tracking-Blocker für den Lesebereich der App. */
public class AdBlock {

    // Bekannte Werbe-, Tracking- und Popup-Netzwerke (Domain und alle Subdomains)
    static final String[] HOSTS = {
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "googletagservices.com", "adservice.google.com",
        "adservice.google.de", "pagead2.googlesyndication.com", "google-analytics.com", "googletagmanager.com", "imasdk.googleapis.com",
        "amazon-adsystem.com", "aax.amazon-adsystem.com", "adnxs.com", "adnxs-simple.com", "rubiconproject.com", "pubmatic.com",
        "openx.net", "casalemedia.com", "indexww.com", "criteo.com", "criteo.net", "taboola.com", "taboolasyndication.com",
        "outbrain.com", "outbrainimg.com", "teads.tv", "teads.com", "seedtag.com", "smartadserver.com", "sascdn.com",
        "yieldlove.com", "yieldlove-ad-serving.net", "adition.com", "adtech.de", "adform.net", "adformdsp.net", "adsrvr.org",
        "bidswitch.net", "bidr.io", "3lift.com", "triplelift.com", "sharethrough.com", "media.net", "contextweb.com",
        "lijit.com", "sovrn.com", "33across.com", "gumgum.com", "yieldmo.com", "districtm.io", "emxdgt.com", "rhythmone.com",
        "spotxchange.com", "spotx.tv", "springserve.com", "smaato.net", "inmobi.com", "mopub.com", "applovin.com",
        "unityads.unity3d.com", "moatads.com", "moatpixel.com", "doubleverify.com", "adsafeprotected.com", "integralads.com",
        "scorecardresearch.com", "quantserve.com", "quantcount.com", "chartbeat.com", "chartbeat.net", "hotjar.com",
        "mouseflow.com", "crazyegg.com", "newrelic.com", "nr-data.net", "bluekai.com", "krxd.net", "demdex.net", "omtrdc.net",
        "everesttech.net", "rlcdn.com", "tapad.com", "agkn.com", "mathtag.com", "turn.com", "exelator.com", "eyeota.net",
        "liadm.com", "id5-sync.com", "adsymptotic.com", "zemanta.com", "plista.com", "ligatus.com", "ligadx.com",
        "nativendo.de", "stroeerdigitalgroup.de", "stroeerdigitalmedia.de", "adscale.de", "emetriq.de", "theadex.com",
        "ioam.de", "xplosion.de", "nuggad.net", "meetrics.net", "mxcdn.net", "adalliance.io", "ad-alliance.de",
        "uimserv.net", "adup-tech.com", "permutive.com", "permutive.app", "cxense.com", "piano.io", "tinypass.com",
        "adhese.com", "adhese.net", "glomex.com", "glomex.cloud", "jwpltx.com", "connatix.com", "primis.tech", "vidoomy.com",
        "aniview.com", "ex.co", "playbuzz.com", "nativeads.com", "mgid.com", "revcontent.com", "propellerads.com",
        "popads.net", "popcash.net", "adcash.com", "exoclick.com", "juicyads.com", "trafficjunky.net", "hilltopads.net",
        "onclickads.net", "zedo.com", "advertising.com", "adtechus.com", "yahoo-ads.com", "ads.yahoo.com", "adsonar.com",
        "facebook.net", "connect.facebook.net", "ads-twitter.com", "static.ads-twitter.com", "analytics.tiktok.com",
        "bat.bing.com", "clarity.ms", "adition.net", "flashtalking.com", "serving-sys.com", "sizmek.com", "innovid.com",
        "tremorhub.com", "lkqd.net", "undertone.com", "kargo.com", "ogury.io", "presage.io", "adtelligent.com", "vidazoo.com",
        "smilewanted.com", "richaudience.com", "onetag-sys.com", "e-planning.net", "improvedigital.com", "360yield.com",
        "adkernel.com", "admixer.net", "otm-r.com", "adyoulike.com", "omnitagjs.com", "quantcast.com", "tealiumiq.com",
        "adrtx.net", "adspirit.de", "orbidder.otto.de", "ad4mat.de", "affilinet.de", "zanox.com", "awin1.com", "webgains.com",
        "dianomi.com", "powerlinks.com", "sportradarserving.com", "betting-ads.com"
    };
    static final Set<String> SET = new HashSet<>();
    static { for (String h : HOSTS) SET.add(h); }

    static final Pattern PATH = Pattern.compile("(/|\\.|_|-)(ads?|adserver|adframe|adunit|prebid|pagead|advert(ising)?|werbung|sponsor(ed)?|popunder|doubleclick)(/|\\.|_|-|\\?)",
            Pattern.CASE_INSENSITIVE);

    static boolean on(Context c) { return Notifier.prefs(c).getBoolean("adblock", true); }

    // Seiten, die mit Werbeblocker gar keine Inhalte zeigen (Adblock-Sperre) – dort bleibt der Blocker aus
    // Ergebnis des Seiten-Tests (Okt. 2026): BILD sperrt mit Blocker, bei Yahoo fehlt mit Blocker rund die Hälfte des Inhalts.
    // Alle anderen getesteten Quellen funktionieren mit Blocker. Weitere Sperren erkennt die App automatisch.
    static final String[] WALLS = {"bild.de", "yahoo.com"};

    static java.util.Set<String> allowSet(Context c) {
        return new HashSet<>(Notifier.prefs(c).getStringSet("abAllow", new HashSet<>()));
    }

    /** Darf auf dieser Seite geblockt werden? */
    static boolean blockOn(Context c, String host) {
        if (!on(c)) return false;
        if (host == null) return true;
        String base = baseDomain(host.toLowerCase(Locale.ROOT));
        if (allowSet(c).contains(base)) return false;
        if (Notifier.prefs(c).getStringSet("abForce", new HashSet<>()).contains(base)) return true;
        for (String w : WALLS) if (base.equals(w)) return false;
        return true;
    }

    /** Merkt sich, ob der Blocker auf dieser Seite an oder aus sein soll. */
    static void remember(Context c, String host, boolean blockIt) {
        if (host == null) return;
        String base = baseDomain(host.toLowerCase(Locale.ROOT));
        Set<String> allow = allowSet(c), force = new HashSet<>(Notifier.prefs(c).getStringSet("abForce", new HashSet<>()));
        if (blockIt) { allow.remove(base); force.add(base); } else { allow.add(base); force.remove(base); }
        Notifier.prefs(c).edit().putStringSet("abAllow", allow).putStringSet("abForce", force).apply();
    }

    static final String WALL_JS = "(function(){try{var t=(document.title+' '+(document.body?document.body.innerText.slice(0,4000):''));"
            + "return /adblockwall|aufgrund ihres blockers|werbeblocker (deaktivieren|ausschalten)|adblocker (deaktivieren|ausschalten)|ad ?blocker detected|disable your ad ?blocker|turn off your ad ?blocker/i.test(t);}catch(e){return false}})()";
    static boolean cookies(Context c) { return Notifier.prefs(c).getBoolean("hidecookies", true); }

    static boolean blockedHost(String host) {
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        while (true) {
            if (SET.contains(host)) return true;
            int i = host.indexOf('.');
            if (i < 0 || host.indexOf('.', i + 1) < 0) return false;
            host = host.substring(i + 1);
        }
    }

    /** true = Anfrage blockieren. Die Seite selbst wird nie blockiert. */
    static boolean shouldBlock(Uri u, String pageHost) {
        String host = u.getHost();
        if (host == null) return false;
        if (blockedHost(host)) return true;
        boolean third = pageHost != null && !host.endsWith(baseDomain(pageHost));
        String path = u.getEncodedPath() == null ? "" : u.getEncodedPath();
        return third && PATH.matcher(path).find();
    }

    static String baseDomain(String host) {
        String[] p = host.split("\\.");
        if (p.length < 2) return host;
        return p[p.length - 2] + "." + p[p.length - 1];
    }

    static WebResourceResponse empty() {
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
    }

    /** Blendet übrig gebliebene Werbeflächen (und auf Wunsch Cookie-Banner) per CSS aus. */
    static String cosmeticJs(boolean hideCookies) {
        String ads = "[id^='google_ads'],[id^='div-gpt-ad'],[id*='taboola'],[class*='taboola'],[id*='outbrain'],[class*='OUTBRAIN'],"
                + "ins.adsbygoogle,iframe[src*='doubleclick'],iframe[src*='googlesyndication'],iframe[id^='google_ads'],"
                + "[class~='ad'],[class~='ads'],[class*='ad-slot'],[class*='adslot'],[class*='ad-container'],[class*='ad-wrapper'],[class*='adContainer'],"
                + "[class*='advert'],[id*='advert'],[class*='werbung'],[id*='werbung'],[class*='sponsored'],[data-ad],[data-ad-slot],[data-google-query-id],"
                + "[class*='sdgSlot'],[class*='sdg-ad'],[id^='sdgSlot'],[class*='ems-ad'],[id*='superbanner'],[id*='skyscraper'],[class*='billboard'],"
                + "[class*='Ad-module'],[class*='adLabel'],[class*='traffective'],[id*='traffective'],[class*='yieldlove'],[id*='yieldlove'],"
                + "[class*='sticky-ad'],[class*='stickyAd'],[id*='sticky-ad'],[class*='teads'],[id*='teads'],[class*='ligatus'],[class*='plista']";
        String cookie = "#sp_message_container,[id^='sp_message'],.sp-message-open,#usercentrics-root,#usercentrics-cmp-ui,#onetrust-consent-sdk,#onetrust-banner-sdk,"
                + ".fc-consent-root,#didomi-host,#CybotCookiebotDialog,#cmpbox,#cmpbox2,.cmpboxBG,#qc-cmp2-container,.qc-cmp2-container,[class*='cookie-banner'],[id*='cookie-banner'],"
                + "[class*='consent-banner'],[id*='consent-layer'],[class*='cmp-container'],#truste-consent-track";
        String css = ads + "{display:none!important;visibility:hidden!important;height:0!important;min-height:0!important}"
                + (hideCookies ? cookie + "{display:none!important}html,body{overflow:auto!important;position:static!important}body.sp-message-open,body.didomi-popup-open{overflow:auto!important}" : "");
        return "(function(){try{if(window.__w11ab)return;window.__w11ab=1;var s=document.createElement('style');s.textContent="
                + org.json.JSONObject.quote(css)
                + ";(document.head||document.documentElement).appendChild(s);"
                + "var n=0,clean=function(){document.querySelectorAll('iframe').forEach(function(f){var r=f.getBoundingClientRect();var src=f.src||'';"
                + "if(/ads|doubleclick|googlesyndication|adnxs|criteo|taboola|outbrain|teads|yieldlove|smartadserver/i.test(src)){f.remove();n++;}});};"
                + "clean();new MutationObserver(clean).observe(document.documentElement,{childList:true,subtree:true});}catch(e){}})();";
    }
}
