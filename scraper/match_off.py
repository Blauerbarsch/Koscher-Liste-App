"""Gleicht die ORD-Produkte mit dem Open-Food-Facts-Datenexport ab.

Liest den OFF-CSV-Export (TSV) von stdin, schreibt
  barcodes.json : {barcode: ord_id}
  brands.json   : {hersteller: [[markentokens], ...]}
und ergänzt products.json um Bild-URLs.
"""
import csv
import json
import sys
from collections import defaultdict
from pathlib import Path

from textmatch import brand_hit, brand_keys, norm, strict_match, tokens

OUT = Path(sys.argv[1])
data = json.loads((OUT / "products.json").read_text())
products = data["products"]

by_manu = defaultdict(list)
for p in products:
    by_manu[p["manufacturer"]].append(p)
keys = {m: brand_keys(m) for m in by_manu}
first_tokens = defaultdict(list)  # erstes Markentoken -> Hersteller (schneller Vorfilter)
for m, ks in keys.items():
    for k in ks:
        if k:
            first_tokens[k[0]].append(m)

csv.field_size_limit(1 << 30)
reader = csv.reader(sys.stdin, delimiter="\t", quoting=csv.QUOTE_NONE)
header = next(reader)
col = {h: i for i, h in enumerate(header)}
name_cols = [col[c] for c in ("product_name", "product_name_de", "abbreviated_product_name", "generic_name") if c in col]
i_code, i_brands = col["code"], col["brands"]
i_img = col.get("image_small_url")
i_labels = col.get("labels_tags")

barcode_cands = {}  # code -> (score, ord_id)
images = {}
rows = hits = 0
for row in reader:
    rows += 1
    if len(row) < len(header):
        continue
    btoks = set(norm(row[i_brands]).split())
    if not btoks:
        continue
    manus = {m for t in btoks for m in first_tokens.get(t, ())}
    manus = [m for m in manus if brand_hit(keys[m], btoks)]
    if not manus:
        continue
    code = row[i_code].strip()
    if not code.isdigit():
        continue
    names = [row[i] for i in name_cols if row[i].strip()]
    best = None
    for m in manus:
        for p in by_manu[m]:
            ot = tokens(p["name"])
            pt = ot - btoks or ot
            for n in names:
                nt = tokens(n)
                rt = (nt - btoks) if (ot - btoks) else (nt | btoks)
                if strict_match(pt, rt):
                    score = len(pt)
                    if best is None or score > best[0]:
                        best = (score, p["id"], False)
                    elif score == best[0] and p["id"] != best[1]:
                        best = (score, best[1], True)  # nicht eindeutig
                    break
    if best and not best[2]:
        hits += 1
        barcode_cands[code] = best[1]
        if i_img is not None and row[i_img] and best[1] not in images:
            images[best[1]] = row[i_img]
    if rows % 500000 == 0:
        print(f"{rows} OFF-Zeilen, {hits} Treffer", flush=True)

for p in products:
    if p["id"] in images:
        p["image"] = images[p["id"]]
(OUT / "products.json").write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
(OUT / "barcodes.json").write_text(json.dumps(barcode_cands, separators=(",", ":")))
(OUT / "brands.json").write_text(json.dumps(keys, ensure_ascii=False, separators=(",", ":")))
matched = len(set(barcode_cands.values()))
print(f"Fertig: {rows} OFF-Zeilen, {len(barcode_cands)} Barcodes für {matched} von {len(products)} ORD-Produkten, {len(images)} Bilder")
