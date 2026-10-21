# Koscher-Liste App

Inoffizielle Android-App für die [Koscherliste der Orthodoxen Rabbinerkonferenz Deutschland (ORD)](https://www.ordonline.de/koscherliste/), für den privaten Gebrauch.

## Funktionen

- Alle Produkte der ORD-Liste durchsuchen, filtern nach Kategorie, Hersteller, Zertifikat, Milchig und Pessach
- Barcode-Scanner: 🟢 koscher laut ORD · 🟡 bitte prüfen · ⚪ nicht gelistet
- Packungsfoto-Scanner: liest den Text auf der Packung offline und sucht passende Produkte
- Eigene Zuordnung von Barcodes, die sich die App merkt
- Einkaufsliste mit eigenen Einträgen, teilbar als Text
- Produktbilder aus Open Food Facts

## Installieren

1. Auf dem Handy [die neueste Version](https://github.com/Blauerbarsch/Koscher-Liste-App/releases/latest) öffnen und `Koscher-Liste.apk` herunterladen.
2. Die Datei öffnen. Beim ersten Mal fragt Android, ob der Browser Apps installieren darf: erlauben.
3. Updates genauso installieren, sie ersetzen die alte Version, die Einkaufsliste bleibt erhalten.

## Wie es funktioniert

- `scraper/scrape_ord.py` lädt jeden Montag die komplette Liste von ordonline.de (Workflow *Koscherliste aktualisieren*).
- `scraper/match_off.py` gleicht sie mit dem Datenexport von [Open Food Facts](https://world.openfoodfacts.org) ab und speichert Barcodes und Bilder.
- Die Daten liegen im Branch `data`. Die App bringt sie mit und kann sie unter *Info → Liste jetzt aktualisieren* neu laden.
- Danach baut der Workflow *App bauen* automatisch eine neue APK und veröffentlicht sie als Release.

Die Barcodes kommen aus einem automatischen Abgleich, nicht von der ORD. Ein Produkt, das nicht auf der Liste steht, ist nicht automatisch nicht koscher.
