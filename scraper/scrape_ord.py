"""Lädt die komplette Koscherliste von ordonline.de und speichert sie als JSON.

Die Seite lädt ihre Produkte per JetEngine "listing_load_more" (9 pro Seite).
Wir öffnen die Seite einmal im Browser, fangen die echte Nachlade-Anfrage ab
und spielen sie dann für jede Seite mit geänderter Seitenzahl nach.
"""
import json
import re
import sys
import time
from pathlib import Path
from urllib.parse import parse_qsl, urlencode

from bs4 import BeautifulSoup
from playwright.sync_api import sync_playwright

URL = "https://www.ordonline.de/koscherliste/"
OUT = Path(sys.argv[1] if len(sys.argv) > 1 else "out")
OUT.mkdir(parents=True, exist_ok=True)

LABELS = {
    "hersteller": "manufacturer",
    "zertifikate": "certificates",
    "zertifikat": "certificates",
    "milchig": "dairy",
    "pessach-status": "pessach",
    "pessach status": "pessach",
    "pessach": "pessach",
    "kategorie": "categories",
    "kategorien": "categories",
}


def split_list(v):
    return [x.strip() for x in re.split(r",\s*", v) if x.strip()]


def parse_items(html):
    soup = BeautifulSoup(html, "html.parser")
    items = []
    for el in soup.select(".jet-listing-grid__item"):
        pid = el.get("data-post-id")
        h = el.find(["h1", "h2", "h3", "h4"])
        name = h.get_text(" ", strip=True) if h else ""
        # Jedes Elementor-Widget ergibt eine Textzeile
        widgets = el.select(".elementor-widget")
        merged = []
        for w in widgets:
            t = re.sub(r"\s+", " ", w.get_text(" ", strip=True)).replace(" ,", ",").strip()
            if t:
                merged.append(t)
        rec = {"id": int(pid) if pid and pid.isdigit() else None, "name": name,
               "categories": [], "manufacturer": "", "certificates": [], "dairy": [],
               "pessach": [], "notes": []}
        # Kategorien: erstes Term-Widget ohne Label
        cats = []
        for tw in el.select(".jet-listing-dynamic-terms"):
            if ":" not in tw.get_text(" ", strip=True):
                cats = [a.get_text(" ", strip=True) for a in tw.select(".jet-listing-dynamic-terms__link")]
                break
        seen_name = False
        pending_label = None
        for t in merged:
            if not seen_name and t == name:
                seen_name = True
                continue
            m = re.match(r"^([A-Za-zÄÖÜäöüß\- ]+?)\s*:\s*(.*)$", t)
            key = LABELS.get(m.group(1).strip().lower()) if m else None
            if key:
                val = m.group(2).strip()
                if not val:
                    pending_label = key
                    continue
                pending_label = None
                if key == "manufacturer":
                    rec[key] = val
                else:
                    rec[key] += split_list(val)
                continue
            if pending_label:
                if pending_label == "manufacturer":
                    rec["manufacturer"] = t
                else:
                    rec[pending_label] += split_list(t)
                pending_label = None
                continue
            if cats and split_list(t) == cats:
                rec["categories"] = cats
                continue
            if any(t == c for c in cats):
                continue
            rec["notes"].append(t)
        rec["notes"] = " ".join(rec["notes"]).strip()
        for k in ("categories", "certificates", "dairy", "pessach"):
            rec[k] = list(dict.fromkeys(rec[k]))
        items.append(rec)
    return items


def main():
    with sync_playwright() as p:
        browser = p.chromium.launch()
        ctx = browser.new_context(locale="de-DE", user_agent=(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/130.0 Safari/537.36 KoscherListeApp-Sync"))
        page = ctx.new_page()
        captured = {}

        def on_req(req):
            if req.method == "POST" and "listing_load_more" in (req.post_data or ""):
                captured.setdefault("url", req.url)
                captured.setdefault("body", req.post_data)

        page.on("request", on_req)
        page.goto(URL, wait_until="networkidle", timeout=120000)
        first_html = page.inner_html(".jet-listing-grid__items")
        pages_total = int(page.get_attribute(".jet-listing-grid__items", "data-pages") or "1")
        for _ in range(20):
            if "body" in captured:
                break
            page.mouse.wheel(0, 20000)
            page.wait_for_timeout(1500)
        if "body" not in captured:
            sys.exit("Nachlade-Anfrage nicht gefunden")

        params = parse_qsl(captured["body"], keep_blank_values=True)
        keys = [k for k, _ in params]
        (OUT / "debug_request_keys.txt").write_text("\n".join(keys))
        page_key = "page" if "page" in keys else None
        if not page_key:
            sys.exit("Kein 'page'-Parameter in der Anfrage: " + ", ".join(keys[-20:]))

        products = parse_items(first_html)
        (OUT / "debug_first_page.html").write_text(first_html[:20000])
        print(f"Seiten: {pages_total}, Seite 1: {len(products)} Produkte")

        failures = 0
        for n in range(2, pages_total + 1):
            body = urlencode([(k, str(n) if k == page_key else v) for k, v in params])
            for attempt in range(4):
                try:
                    r = ctx.request.post(captured["url"], data=body, headers={
                        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
                        "X-Requested-With": "XMLHttpRequest"}, timeout=60000)
                    data = r.json()
                    html = data.get("data", {}).get("html", "")
                    got = parse_items(html)
                    products += got
                    break
                except Exception as e:  # noqa: BLE001
                    print(f"Seite {n} Versuch {attempt + 1} fehlgeschlagen: {e}")
                    time.sleep(3 * (attempt + 1))
            else:
                failures += 1
            if n % 50 == 0:
                print(f"Seite {n}/{pages_total}: {len(products)} Produkte")
            time.sleep(0.25)
        browser.close()

    # Duplikate entfernen
    uniq = {}
    for pr in products:
        key = pr["id"] or (pr["name"], pr["manufacturer"])
        uniq[key] = pr
    products = list(uniq.values())
    print(f"Fertig: {len(products)} Produkte, {failures} fehlgeschlagene Seiten")
    if len(products) < 1000 or failures > 10:
        sys.exit("Zu wenige Produkte – Abbruch, alte Daten bleiben erhalten")
    (OUT / "products.json").write_text(json.dumps(
        {"source": URL, "fetched": time.strftime("%Y-%m-%d"), "products": products},
        ensure_ascii=False, separators=(",", ":")))


if __name__ == "__main__":
    main()
