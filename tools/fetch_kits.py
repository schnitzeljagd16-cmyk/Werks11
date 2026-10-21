"""Baut beim Erstellen der App Trikotbilder der Werkself aus den Saison-Artikeln
der englischen Wikipedia (Vorlage „Football kit“). Ergebnis: app/src/main/assets/kits/*.png
und kits.js. Fehler werden protokolliert (kits/log.txt) und übersprungen."""
import json, os, re, io, time, urllib.parse, urllib.request, datetime

OUT = "app/src/main/assets/kits"
UA = "Werks11-FanApp/2.6 (https://github.com/schnitzeljagd16-cmyk/Werks11; build script)"
API = "https://en.wikipedia.org/w/api.php?"
log = []
def L(*a):
    log.append(" ".join(str(x) for x in a)); print(*a)

def get(url, binary=False):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=40) as r:
        d = r.read()
    return d if binary else json.loads(d.decode("utf-8"))

try:
    from PIL import Image
except ImportError:
    os.system("python3 -m pip install --quiet --break-system-packages pillow || python3 -m pip install --quiet --user pillow")
    import site, importlib; importlib.invalidate_caches()
    try: site.addsitedir(site.getusersitepackages())
    except Exception: pass
    from PIL import Image

def main():
    os.makedirs(OUT, exist_ok=True)
    now = datetime.date.today()
    last = now.year if now.month >= 7 else now.year - 1
    seasons = list(range(1985, last + 1))
    titles = {"%d–%02d Bayer 04 Leverkusen season" % (y, (y + 1) % 100): y for y in seasons}

    # 1) Wikitext aller Saison-Artikel holen (je 40 Titel pro Anfrage)
    texts = {}
    keys = list(titles.keys())
    for i in range(0, len(keys), 40):
        q = urllib.parse.urlencode({"action": "query", "format": "json", "prop": "revisions", "rvprop": "content", "rvslots": "main",
                                    "redirects": 1, "titles": "|".join(keys[i:i + 40])})
        try:
            j = get(API + q)
            red = {r["to"]: r["from"] for r in j["query"].get("redirects", [])}
            for pg in j["query"]["pages"].values():
                if "revisions" not in pg: continue
                t = red.get(pg["title"], pg["title"])
                if t in titles:
                    texts[titles[t]] = pg["revisions"][0]["slots"]["main"]["*"]
        except Exception as e:
            L("Fehler Wikitext", e)
    L("Saison-Artikel gefunden:", sorted(texts))

    def param(txt, name):
        m = re.search(r"\|\s*" + name + r"\s*=\s*([^|\n}]*)", txt)
        return m.group(1).strip() if m else None

    PARTS = [("la", "leftarm", "left_arm"), ("b", "body", "body"), ("ra", "rightarm", "right_arm"), ("sh", "shorts", "shorts"), ("so", "socks", "socks")]
    def kit_of(txt, n):
        k = {}
        for short, col, _ in PARTS:
            c = param(txt, col + str(n)); p = param(txt, "pattern_" + short + str(n))
            if not c: return None
            k[short] = (c.lstrip("#")[:6], p or "")
        return k

    img_cache = {}
    def commons_png(fname, width):
        key = (fname, width)
        if key in img_cache: return img_cache[key]
        q = urllib.parse.urlencode({"action": "query", "format": "json", "titles": "File:" + fname, "prop": "imageinfo", "iiprop": "url", "iiurlwidth": width})
        pg = next(iter(get(API + q)["query"]["pages"].values()))
        ii = pg.get("imageinfo")
        if not ii: img_cache[key] = None; return None
        url = ii[0].get("thumburl") or ii[0]["url"]
        im = Image.open(io.BytesIO(get(url, True))).convert("RGBA")
        img_cache[key] = im
        time.sleep(0.15)
        return im

    # Maße wie in der Wikipedia-Vorlage (Pixel)
    BOX = {"la": (0, 0, 31, 59), "b": (31, 0, 38, 59), "ra": (69, 0, 31, 59), "sh": (0, 59, 100, 36), "so": (0, 95, 100, 21)}
    BASE = {"la": "Kit left arm.svg", "b": "Kit body.svg", "ra": "Kit right arm.svg", "sh": "Kit shorts.svg", "so": "Kit socks long.svg"}
    FILE = {"la": "Kit left arm{}.png", "b": "Kit body{}.png", "ra": "Kit right arm{}.png", "sh": "Kit shorts{}.png", "so": "Kit socks{}.png"}
    S = 4  # Vergrößerung

    def hexcol(c):
        c = re.sub(r"[^0-9a-fA-F]", "", c)
        if len(c) == 3: c = "".join(ch * 2 for ch in c)
        return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4)) + (255,) if len(c) == 6 else (200, 200, 200, 255)

    def compose(k):
        W, H = 100 * S, 116 * S
        img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        for part, (x, y, w, h) in BOX.items():
            col, pat = k[part]
            layer = Image.new("RGBA", (w * S, h * S), hexcol(col))
            if pat:
                pim = commons_png(FILE[part].format(pat.replace("_", " ").strip() if not pat.startswith("_") else pat), w * S)
                if pim is None and pat.startswith("_"):
                    pim = commons_png(FILE[part].format(pat), w * S)
                if pim is not None:
                    layer.alpha_composite(pim.resize((w * S, h * S)))
            base = commons_png(BASE[part], w * S)
            if base is not None:
                layer.alpha_composite(base.resize((w * S, h * S)))
                # Außerhalb der Kontur transparent machen (Kontur-Bilder sind innen leer, außen transparent)
            img.alpha_composite(layer, (x * S, y * S))
        # Weißen Rand außerhalb der Trikot-Kontur entfernen (Flood-Fill von den Ecken)
        from PIL import ImageDraw
        px = img.load()
        stack = [(0, 0), (W - 1, 0), (0, H - 1), (W - 1, H - 1), (W // 2, 0)]
        seen = set()
        while stack:
            a, b = stack.pop()
            if (a, b) in seen or a < 0 or b < 0 or a >= W or b >= H: continue
            seen.add((a, b))
            r, g, bb, al = px[a, b]
            if al == 0 or (r > 235 and g > 235 and bb > 235):
                px[a, b] = (0, 0, 0, 0)
                stack.extend(((a + 1, b), (a - 1, b), (a, b + 1), (a, b - 1)))
        return img.crop(img.getbbox() or (0, 0, W, H))

    out = []
    for y in sorted(texts):
        txt = texts[y]
        for n, kind in ((1, "Heim"), (2, "Auswärts")):
            k = kit_of(txt, n)
            if not k: continue
            try:
                im = compose(k)
                fn = "kit%d_%d.png" % (y, n)
                im.save(os.path.join(OUT, fn), optimize=True)
                out.append({"y": y, "season": "%d/%02d" % (y, (y + 1) % 100), "kind": kind, "file": fn})
                L("ok", y, kind, k)
            except Exception as e:
                L("Fehler Trikot", y, kind, e)

    open(os.path.join(OUT, "kits.js"), "w", encoding="utf-8").write("window.KITS=" + json.dumps(out, ensure_ascii=False) + ";\n")
    open(os.path.join(OUT, "log.txt"), "w", encoding="utf-8").write("\n".join(log))
    L(len(out), "Trikots erzeugt")

try:
    main()
except Exception as e:
    import traceback
    L("ABBRUCH", traceback.format_exc())
finally:
    os.makedirs(OUT, exist_ok=True)
    open(os.path.join(OUT, "log.txt"), "w", encoding="utf-8").write("\n".join(log))
