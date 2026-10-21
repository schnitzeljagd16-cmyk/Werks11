"""Testet alle Nachrichtenquellen der App mit und ohne Werbeblocker (Headless-Chromium
auf dem GitHub-Server) und schreibt das Ergebnis nach probe/results.json.
Gezählt wird: Adblock-Sperre erkannt? Wie viel Text ist mit Blocker sichtbar im Vergleich zu ohne?"""
import json, re, os, sys, time
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright

SITES = {
    "kicker": "https://www.kicker.de/bayer-04-leverkusen/startseite",
    "Bayer 04 (offiziell)": "https://www.bayer04.de/de-de",
    "RP Online": "https://rp-online.de/sport/fussball/bayer-04/",
    "Kölner Stadt-Anzeiger": "https://www.ksta.de/sport/bayer-04-leverkusen",
    "Express": "https://www.express.de/sport/fussball/bayer-04-leverkusen",
    "BILD": "https://www.bild.de/sport/fussball/bayer-leverkusen/home-10302822.bild.html",
    "Sport1": "https://www.sport1.de/team/bayer-04-leverkusen",
    "Sky Sport": "https://sport.sky.de/bayer-leverkusen",
    "Sportschau": "https://www.sportschau.de/fussball/bundesliga/",
    "WDR": "https://www1.wdr.de/sport/fussball/index.html",
    "ZDF": "https://www.zdf.de/sport",
    "Transfermarkt": "https://www.transfermarkt.de/bayer-04-leverkusen/startseite/verein/15",
    "LigaInsider": "https://www.ligainsider.de/bayer-04-leverkusen/4/",
    "90min": "https://www.90min.de/teams/bayer-04-leverkusen",
    "SPOX": "https://www.spox.com/de/sport/fussball/bundesliga/",
    "GOAL": "https://www.goal.com/de/mannschaft/bayer-04-leverkusen/4ff3a5d4ae73e0d0bf2b6a87",
    "t-online": "https://www.t-online.de/sport/fussball/bundesliga/",
    "Sportbuzzer": "https://www.sportbuzzer.de/",
    "fussballtransfers": "https://www.fussballtransfers.com/",
    "fussballeuropa": "https://www.fussballeuropa.com/",
    "ran": "https://www.ran.de/sports/fussball/bundesliga",
    "n-tv": "https://www.n-tv.de/sport/fussball/",
    "Spiegel": "https://www.spiegel.de/sport/fussball/",
    "FAZ": "https://www.faz.net/aktuell/sport/fussball/bundesliga/",
    "Süddeutsche": "https://www.sueddeutsche.de/sport/fussball",
    "Welt": "https://www.welt.de/sport/fussball/bundesliga/",
    "Bulinews": "https://bulinews.com/",
    "Yahoo": "https://de.sports.yahoo.com/",
    "ESPN": "https://www.espn.com/soccer/team/_/id/131/bayer-leverkusen",
    "Get German Football News": "https://www.getfootballnewsgermany.com/",
    "Merkur / tz": "https://www.tz.de/sport/fc-bayern/",
    "Kölnische Rundschau": "https://www.rundschau-online.de/sport",
    "Sport Bild": "https://sportbild.bild.de/fussball/bundesliga/",
    "Focus": "https://www.focus.de/sport/fussball/",
    "Stern": "https://www.stern.de/sport/fussball/",
    "RTL / ntv Sport": "https://www.rtl.de/sport/fussball/",
    "Fußball.de": "https://www.fussball.de/",
    "Bundesliga.com": "https://www.bundesliga.com/de/bundesliga/clubs/bayer-04-leverkusen",
}
UA = "Mozilla/5.0 (Linux; Android 14; SM-S911B Build/UP1A.231005.007; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/129.0.0.0 Mobile Safari/537.36"
WALL = re.compile(r"adblockwall|aufgrund ihres blockers|werbeblocker (deaktivieren|ausschalten)|adblocker (deaktivieren|ausschalten)|ad ?blocker detected|disable your ad ?blocker|turn off your ad ?blocker|deaktivieren sie (ihren|den) (ad|werbe)blocker|werbeblocker erkannt|adblocker erkannt", re.I)

java = open("app/src/main/java/de/werks11/app/AdBlock.java", encoding="utf-8").read()
hosts_block = java[java.index("HOSTS = {"):java.index("};", java.index("HOSTS = {"))]
HOSTS = set(re.findall(r'"([a-z0-9.-]+)"', hosts_block))
PATH = re.compile(r"(/|\.|_|-)(ads?|adserver|adframe|adunit|prebid|pagead|advert(ising)?|werbung|sponsor(ed)?|popunder|doubleclick)(/|\.|_|-|\?)", re.I)

def base(h):
    p = h.split("."); return ".".join(p[-2:]) if len(p) >= 2 else h

def blocked(url, page_host):
    h = (urlparse(url).hostname or "").lower()
    x = h
    while x.count(".") >= 1:
        if x in HOSTS: return True
        x = x.split(".", 1)[1]
    third = page_host and not h.endswith(base(page_host))
    return bool(third and PATH.search(urlparse(url).path or ""))

def visit(ctx, url, block):
    page = ctx.new_page()
    host = urlparse(url).hostname
    n = {"blocked": 0}
    if block:
        def route(r):
            if r.request.resource_type != "document" and blocked(r.request.url, host):
                n["blocked"] += 1; return r.abort()
            return r.continue_()
        page.route("**/*", route)
    res = {"ok": False}
    try:
        page.goto(url, wait_until="domcontentloaded", timeout=45000)
        page.wait_for_timeout(9000)
        txt = page.evaluate("document.body ? document.body.innerText : ''") or ""
        title = page.title() or ""
        res = {"ok": True, "len": len(txt), "title": title[:90], "wall": bool(WALL.search(title + " " + txt[:6000])), "blocked": n["blocked"],
               "snippet": re.sub(r"\s+", " ", txt[:160])}
    except Exception as e:
        res = {"ok": False, "err": str(e)[:160]}
    page.close()
    return res

out = {}
with sync_playwright() as p:
    br = p.chromium.launch()
    for name, url in SITES.items():
        r = {}
        for mode in ("ohne", "mit"):
            ctx = br.new_context(user_agent=UA, locale="de-DE", viewport={"width": 390, "height": 844}, is_mobile=True, has_touch=True)
            r[mode] = visit(ctx, url, mode == "mit")
            ctx.close()
        a, b = r["ohne"], r["mit"]
        if not a.get("ok") and not b.get("ok"): verdict = "nicht erreichbar"
        elif b.get("wall"): verdict = "SPERRE"
        elif a.get("ok") and b.get("ok") and a["len"] > 800 and b["len"] < a["len"] * 0.35: verdict = "KAPUTT"
        elif not b.get("ok") and a.get("ok"): verdict = "KAPUTT"
        else: verdict = "ok"
        r["url"] = url; r["urteil"] = verdict; r["host"] = base(urlparse(url).hostname)
        out[name] = r
        print(f"{verdict:16} {name:26} ohne={a.get('len')} mit={b.get('len')} blockiert={b.get('blocked')} {b.get('title','')[:50]}", flush=True)
    br.close()
os.makedirs("probe", exist_ok=True)
json.dump(out, open("probe/results.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
