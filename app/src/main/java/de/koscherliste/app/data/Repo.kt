package de.koscherliste.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.koscherliste.app.match.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Product(
    val id: Int,
    val name: String,
    val manufacturer: String,
    val categories: List<String>,
    val certificates: List<String>,
    val dairy: List<String>,
    val pessach: List<String>,
    val notes: String,
    val image: String?,
) {
    /** Manche Einträge stehen nur im Hinweis als milchig */
    val dairyNote: Boolean get() = dairy.isEmpty() && Regex("(?<!nicht )milchig", RegexOption.IGNORE_CASE).containsMatchIn(notes)
    val dairyLabels: List<String> get() = if (dairyNote) listOf("Milchig (laut Hinweis)") else dairy
    val isDairy get() = dairyLabels.isNotEmpty()
    val nameTokens: Set<String> by lazy { Text.tokens(name) }
    val searchText: String by lazy {
        Text.norm(listOf(name, manufacturer, categories.joinToString(" "), certificates.joinToString(" ")).joinToString(" "))
    }
}

data class ShopItem(val id: Long, val productId: Int?, val text: String, val checked: Boolean)

/** Hält die Koscherliste, die Barcode-Tabelle, die Einkaufsliste und eigene Zuordnungen. */
object Repo {
    private const val RAW = "https://raw.githubusercontent.com/Blauerbarsch/Koscher-Liste-App/data/"
    private val FILES = listOf("products.json", "barcodes.json", "brands.json")

    var products by mutableStateOf<List<Product>>(emptyList())
        private set
    var fetched by mutableStateOf("")
        private set
    private var byId: Map<Int, Product> = emptyMap()
    private var barcodes: Map<String, Int> = emptyMap()
    var brands: Map<String, List<List<String>>> = emptyMap()
        private set

    val shopping = mutableStateListOf<ShopItem>()
    val userCodes = mutableStateMapOf<String, Int>()

    private lateinit var ctx: Context
    private val prefs get() = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)

    fun product(id: Int?) = id?.let { byId[it] }

    fun productForBarcode(code: String): Pair<Product, Boolean>? {
        userCodes[code]?.let { id -> byId[id]?.let { return it to true } }
        barcodes[code]?.let { id -> byId[id]?.let { return it to false } }
        // EAN-13 mit führender 0 entspricht UPC-A
        if (code.length == 12) return productForBarcode("0$code")
        return null
    }

    fun barcodesFor(id: Int): List<String> =
        (barcodes.filterValues { it == id }.keys + userCodes.filterValues { it == id }.keys).distinct()

    suspend fun init(context: Context) = withContext(Dispatchers.IO) {
        ctx = context.applicationContext
        loadData()
        loadUserData()
    }

    private fun readFile(name: String): String {
        val updated = File(ctx.filesDir, name)
        return if (updated.exists()) updated.readText() else ctx.assets.open(name).bufferedReader().readText()
    }

    private fun loadData() {
        val root = JSONObject(readFile("products.json"))
        fetched = root.optString("fetched")
        val arr = root.getJSONArray("products")
        val list = ArrayList<Product>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list += Product(
                id = o.optInt("id", -i - 1),
                name = o.optString("name").trim(),
                manufacturer = o.optString("manufacturer").trim(),
                categories = o.optJSONArray("categories").strings(),
                certificates = o.optJSONArray("certificates").strings(),
                dairy = o.optJSONArray("dairy").strings(),
                pessach = o.optJSONArray("pessach").strings(),
                notes = o.optString("notes"),
                image = o.optString("image").takeIf { it.startsWith("http") },
            )
        }
        list.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        byId = list.associateBy { it.id }
        products = list

        val bc = JSONObject(readFile("barcodes.json"))
        barcodes = bc.keys().asSequence().associateWith { bc.getInt(it) }

        val br = JSONObject(readFile("brands.json"))
        brands = br.keys().asSequence().associateWith { m ->
            val a = br.getJSONArray(m)
            (0 until a.length()).map { a.getJSONArray(it).strings() }
        }
    }

    /** Lädt die neueste Liste aus dem GitHub-Repo. Gibt die Anzahl Produkte zurück. */
    suspend fun update(): Int = withContext(Dispatchers.IO) {
        val texts = FILES.associateWith { httpGet(RAW + it) }
        val count = JSONObject(texts.getValue("products.json")).getJSONArray("products").length()
        require(count > 1000) { "Unvollständige Daten" }
        texts.forEach { (n, t) -> File(ctx.filesDir, n).writeText(t) }
        loadData()
        count
    }

    // ---------- Einkaufsliste & eigene Zuordnungen ----------

    private fun loadUserData() {
        val s = JSONArray(prefs.getString("shopping", "[]"))
        val items = (0 until s.length()).map {
            val o = s.getJSONObject(it)
            ShopItem(o.getLong("id"), if (o.has("p")) o.getInt("p") else null, o.optString("t"), o.optBoolean("c"))
        }
        val u = JSONObject(prefs.getString("codes", "{}")!!)
        val codes = u.keys().asSequence().associateWith { u.getInt(it) }
        withMain {
            shopping.clear(); shopping.addAll(items)
            userCodes.clear(); userCodes.putAll(codes)
        }
    }

    private fun withMain(block: () -> Unit) = android.os.Handler(android.os.Looper.getMainLooper()).post(block)

    private fun saveShopping() {
        val a = JSONArray()
        shopping.forEach { i ->
            a.put(JSONObject().put("id", i.id).put("t", i.text).put("c", i.checked).apply { i.productId?.let { put("p", it) } })
        }
        prefs.edit().putString("shopping", a.toString()).apply()
    }

    fun inShopping(id: Int) = shopping.any { it.productId == id && !it.checked }

    fun toggleShopping(p: Product) {
        val existing = shopping.filter { it.productId == p.id }
        if (existing.any { !it.checked }) shopping.removeAll(existing)
        else { shopping.removeAll(existing); shopping.add(ShopItem(System.nanoTime(), p.id, p.name, false)) }
        saveShopping()
    }

    fun addText(text: String) {
        if (text.isBlank()) return
        shopping.add(ShopItem(System.nanoTime(), null, text.trim(), false)); saveShopping()
    }

    fun setChecked(item: ShopItem, c: Boolean) {
        val i = shopping.indexOfFirst { it.id == item.id }
        if (i >= 0) { shopping[i] = item.copy(checked = c); saveShopping() }
    }

    fun remove(item: ShopItem) { shopping.removeAll { it.id == item.id }; saveShopping() }
    fun clearChecked() { shopping.removeAll { it.checked }; saveShopping() }

    fun assign(code: String, id: Int) {
        userCodes[code] = id
        prefs.edit().putString("codes", JSONObject(userCodes.toMap() as Map<*, *>).toString()).apply()
    }

    fun unassign(code: String) {
        userCodes.remove(code)
        prefs.edit().putString("codes", JSONObject(userCodes.toMap() as Map<*, *>).toString()).apply()
    }

    fun clearAssignments() { userCodes.clear(); prefs.edit().remove("codes").apply() }
}

fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }

fun httpGet(url: String): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 10000
    c.readTimeout = 20000
    c.setRequestProperty("User-Agent", "KoscherListeApp/1.0 (privat; github.com/Blauerbarsch/Koscher-Liste-App)")
    try {
        if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
        return c.inputStream.bufferedReader().readText()
    } finally {
        c.disconnect()
    }
}
