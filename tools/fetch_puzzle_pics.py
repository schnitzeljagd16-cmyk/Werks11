"""Lädt beim Bauen Fotos für das Foto-Puzzle von Wikipedia/Wikimedia Commons
und legt sie fest in die App (app/src/main/assets/pz). Schlägt etwas fehl,
wird das Bild einfach übersprungen – die App lädt es dann wie bisher online."""
import json, re, time, urllib.parse, urllib.request, os, html

OUT = "app/src/main/assets/pz"
UA = "Werks11-FanApp/2.4 (https://github.com/schnitzeljagd16-cmyk/Werks11; build script)"
PICS = [
    ("BayArena", "BayArena"), ("Ulf Kirsten", "Ulf Kirsten"), ("Stefan Kießling", "Stefan Kießling"),
    ("Rudi Völler", "Rudi Völler"), ("Xabi Alonso", "Xabi Alonso"), ("Florian Wirtz", "Florian Wirtz"),
    ("Patrik Schick", "Patrik Schick"), ("Michael Ballack", "Michael Ballack"), ("Bernd Schneider", "Bernd Schneider (Fußballspieler)"),
    ("Granit Xhaka", "Granit Xhaka"), ("Kai Havertz", "Kai Havertz"), ("Jonathan Tah", "Jonathan Tah"),
    ("Lúcio", "Lúcio"), ("Hans-Jörg Butt", "Hans-Jörg Butt"), ("Simon Rolfes", "Simon Rolfes"),
    ("Lars Bender", "Lars Bender"), ("Bernd Leno", "Bernd Leno"), ("Jeremie Frimpong", "Jeremie Frimpong"),
    ("Arturo Vidal", "Arturo Vidal"), ("Heung-min Son", "Son Heung-min"), ("Dimitar Berbatov", "Dimitar Berbatov"),
    ("Erich Ribbeck", "Erich Ribbeck"), ("Leverkusen", "Leverkusen"),
]

def get(url, binary=False):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        data = r.read()
    return data if binary else json.loads(data.decode("utf-8"))

def clean(s):
    s = re.sub(r"<[^>]+>", "", s or "")
    return html.unescape(re.sub(r"\s+", " ", s)).strip()[:80]

os.makedirs(OUT, exist_ok=True)
out = []
for i, (label, title) in enumerate(PICS):
    try:
        q = urllib.parse.urlencode({"action": "query", "format": "json", "redirects": 1, "titles": title,
                                    "prop": "pageimages", "piprop": "thumbnail|name", "pithumbsize": 800})
        j = get("https://de.wikipedia.org/w/api.php?" + q)
        pg = next(iter(j["query"]["pages"].values()))
        src, name = pg.get("thumbnail", {}).get("source"), pg.get("pageimage")
        if not src or not name:
            print("kein Bild:", title); continue
        ext = ".png" if src.lower().endswith(".png") else ".jpg"
        fn = "pz%02d%s" % (i, ext)
        data = get(src, binary=True)
        if len(data) < 5000:
            print("zu klein:", title); continue
        open(os.path.join(OUT, fn), "wb").write(data)
        artist, lic = "", ""
        try:
            q2 = urllib.parse.urlencode({"action": "query", "format": "json", "titles": "File:" + name,
                                         "prop": "imageinfo", "iiprop": "extmetadata"})
            m = next(iter(get("https://de.wikipedia.org/w/api.php?" + q2)["query"]["pages"].values()))["imageinfo"][0]["extmetadata"]
            artist, lic = clean(m.get("Artist", {}).get("value")), clean(m.get("LicenseShortName", {}).get("value"))
        except Exception as e:
            print("keine Lizenzinfo:", title, e)
        out.append({"t": label, "file": fn, "artist": artist, "license": lic})
        print("ok:", title, fn, len(data))
        time.sleep(0.4)
    except Exception as e:
        print("Fehler:", title, e)

open(os.path.join(OUT, "pics.js"), "w", encoding="utf-8").write("window.PZ_PICS=" + json.dumps(out, ensure_ascii=False) + ";\n")
print(len(out), "Bilder fest eingebaut")
