"""Text-Normalisierung und Abgleich. Die App (Matcher.kt) nutzt dieselben Regeln."""
import re
import unicodedata
from difflib import SequenceMatcher

STOP = {
    "der", "die", "das", "und", "mit", "von", "vom", "the", "and", "of", "in", "a", "de", "la", "le",
    "g", "gr", "kg", "ml", "l", "cl", "x", "st", "stk", "stueck", "pack", "packung", "er", "fuer",
}
LEGAL = {
    "gmbh", "co", "kg", "ag", "inc", "llc", "ltd", "se", "und", "firma", "brauerei", "baeckerei",
    "konditorei", "gebr", "dr", "of", "the", "group", "europe", "markenvertrieb", "oele", "kaffee",
    "honig", "muehle", "tee", "schokolade", "waffeln", "ohne", "angabe", "heidelberg", "hannover",
    "bremen", "sweden", "kindergriess", "milchzucker", "foods", "food", "pure", "celestial",
    "ingredients", "division", "gewuerz", "gewuerze", "teuto", "krautfabrik", "grosse", "lebensmittel",
    "fleetwood", "lofthouse", "oelmuehle", "schweisse",
}
FILLER = STOP | {"original", "classic", "klassik", "neu", "new", "mini", "maxi", "family", "vorratspack"}

# Marken, unter denen die Hersteller ihre Produkte verkaufen
ALIASES = {
    "Mondelēz": ["milka", "oreo", "toblerone", "tuc", "philadelphia", "mondelez", "daim", "mikado", "cote d or", "lu"],
    "Mars Inc": ["mars", "snickers", "twix", "m m s", "bounty", "milky way", "ben s original", "uncle ben s", "maltesers", "balisto"],
    "Ferrero": ["ferrero", "nutella", "kinder", "duplo", "hanuta", "tic tac", "raffaello", "mon cheri", "yogurette"],
    "Eckes / Granini": ["granini", "hohes c", "eckes"],
    "Carl Kühne KG": ["kuehne"],
    "HERO / Schwartau": ["schwartau", "hero", "corny"],
    "Schwartau": ["schwartau"],
    "Schwartau Corny": ["corny", "schwartau"],
    "Langnese": ["langnese", "magnum", "cornetto", "carte d or", "algida"],
    "Ben & Jerry": ["ben jerry s", "ben jerrys", "ben jerry"],
    "Kellogs": ["kellogg s", "kelloggs", "kellogg", "smacks", "special k", "tresor"],
    "Peter Kölln": ["koelln", "kolln"],
    "Brauerei Beck & Co GmbH": ["beck s", "becks"],
    "Brauerei Schneider Weisse": ["schneider weisse", "schneider"],
    "Coca Cola": ["coca cola", "fanta", "sprite", "mezzo mix", "coke", "powerade", "lift"],
    "Caprisonne": ["capri sun", "capri sonne", "caprisonne"],
    "Uncle Ben´s": ["uncle ben s", "ben s original"],
    "Dr. Oetker": ["oetker", "dr oetker"],
    "Dr. Ritter": ["ritter"],
    "Dr. Schär": ["schaer", "schar"],
    "Dr.Pepper": ["dr pepper"],
    "Wrigleys": ["wrigley s", "wrigleys", "extra", "airwaves", "hubba bubba", "orbit", "juicy fruit", "doublemint"],
    "Lorenz Bahlsen": ["lorenz", "crunchips", "naturals", "saltletts", "erdnusslocken", "chipsfrisch"],
    "Bahlsen": ["bahlsen", "leibniz", "pick up", "hit", "afrika", "choco leibniz"],
    "Ritter Sport": ["ritter sport", "ritter"],
    "Mövenpick": ["moevenpick", "movenpick"],
    "Müller`s": ["mueller s", "muellers"],
    "Müllers Mühle": ["muellers muehle", "mueller s muehle"],
    "Luigi LAVAZZA": ["lavazza"],
    "J.G. NIEDEREGGER": ["niederegger"],
    "J.T. Ronnefeld KG": ["ronnefeldt", "ronnefeld"],
    "Melitta Europe GmbH": ["melitta"],
    "Kikkoman": ["kikkoman"],
    "HEINZ": ["heinz"],
    "Tchibo": ["tchibo", "eduscho"],
    "Teekanne": ["teekanne"],
    "Hipp": ["hipp"],
    "MILUPA": ["milupa", "aptamil"],
    "Jelly Belly": ["jelly belly"],
    "John West": ["john west"],
    "Walkers Shortbread": ["walkers"],
    "Cavendish & Harvey": ["cavendish harvey", "cavendish"],
    "Edelweiss® Milchzucker": ["edelweiss"],
    "Pomps ® Kindergrieß": ["pomps"],
    "Dreyer Honig": ["dreyer"],
    "AZUL Kaffee GmbH & Co. KG of Bremen": ["azul"],
    "Bäckerei Seip, Heidelberg": ["seip"],
    "Bäckerei und Konditorei Werner, Hannover": ["werner"],
    "Ludwig Trumpf Schokolade": ["trumpf"],
    "Lofthouse of Fleetwood": ["fisherman s friend", "fishermans friend"],
    "Schweppes": ["schweppes"],
    "Underberg AG": ["underberg", "grasovka"],
    "Rügenfisch": ["ruegenfisch", "ruegen fisch", "ostsee fisch"],
    "Hamburger Gewürz-Mühle": ["hamburger gewuerz muehle"],
    "OSTMANN Gewürze Teuto Markenvertrieb GmbH": ["ostmann"],
    "Gebr. Engelke Große Mühle": ["engelke"],
    "Grafschafter Krautfabrik": ["grafschafter"],
    "Ölmühle Pelzmann": ["pelzmann"],
    "Esterer Ölmühle": ["esterer"],
    "Fauser Vitaquell": ["vitaquell"],
    "Fauser Vitaquell ÖLE": ["vitaquell"],
    "Cenovis Heiler": ["cenovis"],
    "Raps GmbH Öle": ["raps"],
    "Erich Trapp Scandia": ["scandia"],
    "Hain Pure Foods": ["hain"],
    "Hain Celestial Group": ["hain", "celestial seasonings"],
    "Demeter Bauck": ["bauck", "bauckhof"],
    "Bauck": ["bauck", "bauckhof"],
    "Milford Meßmer": ["milford", "messmer"],
    "Wollenhaupt Tee": ["wollenhaupt"],
    "ohne Angabe": [],
    "Waffeln": [],
}


UNIT = re.compile(r"^\d+(g|gr|kg|ml|l|cl|er|x|st|stk)?$")


def norm(s: str) -> str:
    s = (s or "").lower()
    for a, b in (("ä", "ae"), ("ö", "oe"), ("ü", "ue"), ("ß", "ss")):
        s = s.replace(a, b)
    s = unicodedata.normalize("NFKD", s)
    s = "".join(c for c in s if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]+", " ", s).strip()


def tokens(s: str) -> set:
    return {t for t in norm(s).split() if t not in STOP and not UNIT.match(t)}


def brand_keys(manufacturer: str) -> list:
    if manufacturer in ALIASES:
        return [a.split() for a in ALIASES[manufacturer]]
    toks = [t for t in norm(manufacturer).split() if t not in LEGAL and len(t) >= 3 and not t.isdigit()]
    return [[t] for t in toks]


def brand_hit(keys: list, brand_tokens: set) -> bool:
    return any(k and all(t in brand_tokens for t in k) for k in keys)


def strict_match(ord_tokens: set, off_tokens: set) -> bool:
    if not ord_tokens or not off_tokens:
        return False
    if ord_tokens <= off_tokens and not (off_tokens - ord_tokens - FILLER):
        return True
    a, b = " ".join(sorted(ord_tokens)), " ".join(sorted(off_tokens - FILLER))
    return SequenceMatcher(None, a, b).ratio() >= 0.92
