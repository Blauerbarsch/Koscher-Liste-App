package de.koscherliste.app.match

import de.koscherliste.app.data.Product
import de.koscherliste.app.data.Repo
import de.koscherliste.app.data.httpGet
import de.koscherliste.app.data.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.Normalizer

/** Gleiche Regeln wie scraper/textmatch.py */
object Text {
    private val STOP = setOf(
        "der", "die", "das", "und", "mit", "von", "vom", "the", "and", "of", "in", "a", "de", "la", "le",
        "g", "gr", "kg", "ml", "l", "cl", "x", "st", "stk", "stueck", "pack", "packung", "er", "fuer",
    )
    private val FILLER = STOP + setOf("original", "classic", "klassik", "neu", "new", "mini", "maxi", "family", "vorratspack")
    private val UNIT = Regex("^\\d+(g|gr|kg|ml|l|cl|er|x|st|stk)?$")
    private val NON_ALNUM = Regex("[^a-z0-9]+")
    private val MARKS = Regex("\\p{Mn}+")

    fun norm(s: String): String {
        var t = s.lowercase().replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        t = MARKS.replace(Normalizer.normalize(t, Normalizer.Form.NFKD), "")
        return NON_ALNUM.replace(t, " ").trim()
    }

    fun tokens(s: String): Set<String> =
        norm(s).split(' ').filter { it.isNotEmpty() && it !in STOP && !UNIT.matches(it) }.toSet()

    fun strict(ord: Set<String>, off: Set<String>): Boolean {
        if (ord.isEmpty() || off.isEmpty()) return false
        if (off.containsAll(ord) && (off - ord - FILLER).isEmpty()) return true
        return similarity(ord.sorted().joinToString(" "), (off - FILLER).sorted().joinToString(" ")) >= 0.92
    }

    /** Ähnlichkeit 0..1 (Levenshtein-basiert) */
    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return 1.0 - prev[b.length].toDouble() / maxOf(a.length, b.length)
    }
}

enum class Status { KOSHER, CHECK, UNKNOWN }

data class OffInfo(val name: String, val brands: String, val image: String?, val koscherLabel: Boolean)

data class ScanResult(
    val status: Status,
    val code: String? = null,
    val product: Product? = null,
    val candidates: List<Product> = emptyList(),
    val off: OffInfo? = null,
    val reason: String = "",
    val offline: Boolean = false,
)

object Matcher {

    private fun manufacturersFor(brandTokens: Set<String>): List<String> =
        Repo.brands.filter { (_, keys) -> keys.any { k -> k.isNotEmpty() && brandTokens.containsAll(k) } }.keys.toList()

    private fun productsOf(manus: Collection<String>) = Repo.products.filter { it.manufacturer in manus }

    /** Rangfolge nach Wortüberdeckung (wie viel vom ORD-Namen im Text steckt) */
    private fun rank(pool: List<Product>, text: Set<String>, minCoverage: Double): List<Pair<Product, Double>> =
        pool.mapNotNull { p ->
            val t = p.nameTokens
            if (t.isEmpty()) return@mapNotNull null
            val cov = t.count { it in text }.toDouble() / t.size
            if (cov >= minCoverage) p to cov + 0.01 * t.size else null
        }.sortedByDescending { it.second }

    suspend fun byBarcode(code: String): ScanResult {
        Repo.productForBarcode(code)?.let { (p, own) ->
            return ScanResult(Status.KOSHER, code, p,
                reason = if (own) "Von dir zugeordnet" else "Barcode in der Koscherliste-Datenbank gefunden")
        }
        val off = try {
            fetchOff(code)
        } catch (e: Exception) {
            return ScanResult(Status.UNKNOWN, code, offline = true,
                reason = "Offline: Der Barcode ist nicht in der gespeicherten Tabelle. Mit Internet kann die App mehr herausfinden.")
        } ?: return ScanResult(Status.UNKNOWN, code,
            reason = "Dieser Barcode ist weder in der Liste noch bei Open Food Facts bekannt.")

        val brandTokens = Text.norm(off.brands).split(' ').filter { it.isNotEmpty() }.toSet()
        val nameTokens = Text.tokens(off.name)
        val manus = manufacturersFor(brandTokens)
        if (manus.isNotEmpty()) {
            val pool = productsOf(manus)
            val strict = pool.filter { p ->
                val ot = p.nameTokens
                val pt = (ot - brandTokens).ifEmpty { ot }
                val rt = if ((ot - brandTokens).isNotEmpty()) nameTokens - brandTokens else nameTokens + brandTokens
                Text.strict(pt, rt)
            }.sortedByDescending { it.nameTokens.size }
            if (strict.size == 1 || (strict.size > 1 && strict[0].nameTokens.size > strict[1].nameTokens.size)) {
                return ScanResult(Status.KOSHER, code, strict[0], off = off,
                    reason = "Automatisch erkannt als „${off.name}“. Vergleiche kurz die Sorte auf der Packung.")
            }
            val cands = (strict + rank(pool, nameTokens + brandTokens, 0.5).map { it.first }).distinct().take(8)
            return if (cands.isNotEmpty()) ScanResult(Status.CHECK, code, candidates = cands, off = off,
                reason = "Von ${manus.joinToString()} stehen ähnliche Produkte auf der Liste. Prüfe, ob deine Sorte dabei ist.")
            else ScanResult(Status.UNKNOWN, code, off = off, candidates = pool.take(0),
                reason = "Von ${manus.joinToString()} stehen ${pool.size} Produkte auf der Liste, dieses aber nicht.")
        }
        val cands = rank(Repo.products, nameTokens, 0.75).filter { it.first.nameTokens.size >= 2 }.map { it.first }.take(6)
        return if (cands.isNotEmpty()) ScanResult(Status.CHECK, code, candidates = cands, off = off,
            reason = "Der Hersteller passt nicht eindeutig, aber diese Produkte klingen ähnlich.")
        else ScanResult(Status.UNKNOWN, code, off = off, reason = "Weder Hersteller noch Produkt stehen auf der ORD-Liste.")
    }

    private suspend fun fetchOff(code: String): OffInfo? = withContext(Dispatchers.IO) {
        val json = JSONObject(httpGet(
            "https://world.openfoodfacts.org/api/v2/product/$code.json?fields=product_name,product_name_de,brands,image_small_url,image_front_small_url,labels_tags"))
        if (json.optInt("status") != 1) return@withContext null
        val p = json.getJSONObject("product")
        val name = p.optString("product_name_de").ifBlank { p.optString("product_name") }
        val labels = p.optJSONArray("labels_tags").strings()
        OffInfo(
            name = name,
            brands = p.optString("brands"),
            image = (p.optString("image_front_small_url").ifBlank { p.optString("image_small_url") }).takeIf { it.startsWith("http") },
            koscherLabel = labels.any { "kosher" in it || "koscher" in it },
        )
    }

    /** Abgleich eines Packungsfotos (erkannter Text) */
    fun byText(raw: String): ScanResult {
        val words = Text.tokens(raw)
        if (words.isEmpty()) return ScanResult(Status.UNKNOWN, reason = "Auf dem Foto wurde kein Text erkannt.")
        val manus = manufacturersFor(words)
        val ranked = rank(Repo.products, words, 0.6).map { (p, s) ->
            p to s + if (p.manufacturer in manus) 0.5 else 0.0
        }.sortedByDescending { it.second }
        if (ranked.isEmpty()) return ScanResult(Status.UNKNOWN,
            reason = if (manus.isNotEmpty()) "Der Hersteller ${manus.joinToString()} steht auf der Liste, das Produkt wurde aber nicht erkannt."
            else "Auf dem Foto wurde kein Produkt der Liste erkannt.")
        val top = ranked[0]
        val clear = top.first.manufacturer in manus && top.first.nameTokens.size >= 2 &&
            top.first.nameTokens.all { it in words } && (ranked.size == 1 || ranked[1].second < top.second - 0.05)
        return if (clear) ScanResult(Status.KOSHER, product = top.first, candidates = ranked.drop(1).take(4).map { it.first },
            reason = "Auf der Packung erkannt. Vergleiche kurz die Sorte.")
        else ScanResult(Status.CHECK, candidates = ranked.take(8).map { it.first },
            reason = "Diese Produkte passen zum Text auf dem Foto. Tippe das richtige an.")
    }
}
