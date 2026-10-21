package de.koscherliste.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import de.koscherliste.app.data.Repo
import de.koscherliste.app.match.Matcher
import de.koscherliste.app.match.ScanResult
import de.koscherliste.app.match.Status
import de.koscherliste.app.ui.DetailScreen
import de.koscherliste.app.ui.InfoScreen
import de.koscherliste.app.ui.ListScreen
import de.koscherliste.app.ui.ListState
import de.koscherliste.app.ui.ScanScreen
import de.koscherliste.app.ui.ShoppingScreen
import de.koscherliste.app.ui.shoppingText
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

enum class Tab(val title: String) { LIST("Liste"), SCAN("Scanner"), SHOP("Einkauf"), INFO("Info") }

sealed interface Overlay {
    data class Detail(val id: Int, val assignCode: String? = null) : Overlay
    data class Pick(val code: String) : Overlay
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { App() } }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFF9DC2FF))
        else -> lightColorScheme(primary = Color(0xFF1F4E8C))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(Tab.LIST) }
    val stack = remember { mutableStateListOf<Overlay>() }
    val listState = remember { ListState() }
    val pickState = remember { ListState() }
    var scanResult by remember { mutableStateOf<ScanResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) { Repo.init(ctx); ready = true }

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

    fun scanBarcode(onCode: (String) -> Unit) {
        val opts = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(ctx, opts).startScan()
            .addOnSuccessListener { b -> b.rawValue?.let(onCode) }
            .addOnFailureListener { e -> toast("Scanner nicht verfügbar: ${e.message}") }
    }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = photoUri
        if (!ok || uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            scanResult = try {
                val text = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(InputImage.fromFilePath(ctx, uri)).await().text
                Matcher.byText(text)
            } catch (e: Exception) {
                ScanResult(Status.UNKNOWN, reason = "Texterkennung fehlgeschlagen: ${e.message}")
            }
            busy = false
        }
    }

    fun takePhoto() {
        val dir = File(ctx.cacheDir, "photos").apply { mkdirs() }
        val f = File(dir, "packung.jpg")
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        photoUri = uri
        photoLauncher.launch(uri)
    }

    fun onBarcode() = scanBarcode { code ->
        busy = true
        scope.launch { scanResult = Matcher.byBarcode(code); busy = false }
    }

    fun assign(code: String, id: Int) {
        Repo.assign(code, id)
        toast("Zugeordnet – der Scanner erkennt das Produkt künftig direkt")
        if (scanResult?.code == code) scanResult = ScanResult(Status.KOSHER, code, Repo.product(id), reason = "Von dir zugeordnet")
    }

    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

    val top = stack.lastOrNull()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (top) {
                            is Overlay.Detail -> "Produkt"
                            is Overlay.Pick -> "Produkt zuordnen"
                            null -> if (tab == Tab.LIST) "Koscher-Liste" else tab.title
                        },
                    )
                },
                navigationIcon = {
                    if (top != null) IconButton(onClick = { stack.removeAt(stack.lastIndex) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                    }
                },
                actions = {
                    if (top == null && tab == Tab.SHOP && Repo.shopping.isNotEmpty()) {
                        IconButton(onClick = { Repo.clearChecked() }) { Icon(Icons.Filled.DoneAll, "Erledigte entfernen") }
                        IconButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, "Einkaufsliste (koscher):\n" + shoppingText())
                            ctx.startActivity(Intent.createChooser(send, "Einkaufsliste teilen"))
                        }) { Icon(Icons.Filled.Share, "Teilen") }
                    }
                },
            )
        },
        bottomBar = {
            if (top == null) NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        label = { Text(t.title) },
                        icon = {
                            when (t) {
                                Tab.LIST -> Icon(Icons.AutoMirrored.Filled.List, null)
                                Tab.SCAN -> Icon(Icons.Filled.QrCodeScanner, null)
                                Tab.SHOP -> BadgedBox(badge = {
                                    val n = Repo.shopping.count { !it.checked }
                                    if (n > 0) Badge { Text("$n") }
                                }) { Icon(Icons.Filled.ShoppingCart, null) }
                                Tab.INFO -> Icon(Icons.Filled.Info, null)
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        if (!ready) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        when (top) {
            is Overlay.Detail -> Repo.product(top.id)?.let { p ->
                DetailScreen(
                    p, padding, top.assignCode,
                    onAssign = { code -> assign(code, p.id); stack.clear(); tab = Tab.SCAN },
                    onScanAssign = { scanBarcode { code -> assign(code, p.id) } },
                    onFilter = { f -> listState.query = ""; listState.filters = setOf(f); stack.clear(); tab = Tab.LIST },
                )
            }
            is Overlay.Pick -> ListScreen(pickState, onOpen = { p -> stack.add(Overlay.Detail(p.id, top.code)) }, picker = true, padding = padding)
            null -> when (tab) {
                Tab.LIST -> ListScreen(listState, onOpen = { stack.add(Overlay.Detail(it.id)) }, padding = padding)
                Tab.SCAN -> ScanScreen(
                    padding, scanResult, busy,
                    onBarcode = { onBarcode() },
                    onPhoto = { takePhoto() },
                    onOpen = { p, code -> stack.add(Overlay.Detail(p.id, code)) },
                    onPick = { code ->
                        pickState.query = scanResult?.off?.name.orEmpty()
                            .split(' ').firstOrNull { it.length > 3 }.orEmpty()
                        pickState.filters = emptySet()
                        stack.add(Overlay.Pick(code))
                    },
                )
                Tab.SHOP -> ShoppingScreen(padding, onOpen = { stack.add(Overlay.Detail(it.id)) })
                Tab.INFO -> InfoScreen(padding)
            }
        }
    }
}
