package de.koscherliste.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.koscherliste.app.data.Repo
import kotlinx.coroutines.launch

@Composable
fun InfoScreen(padding: PaddingValues) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current

    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Datenstand", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${Repo.products.size} Produkte, abgerufen am ${Repo.fetched}")
                Text(
                    "Die Liste wird jede Woche automatisch neu von ordonline.de geladen. Hier holst du dir den neuesten Stand ohne neue App-Version.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(enabled = !busy, onClick = {
                    busy = true; status = "Lade …"
                    scope.launch {
                        status = try {
                            "Aktualisiert: ${Repo.update()} Produkte"
                        } catch (e: Exception) {
                            "Aktualisieren fehlgeschlagen (${e.message ?: "keine Verbindung"})"
                        }
                        busy = false
                    }
                }) { Text("Liste jetzt aktualisieren") }
                status?.let { Text(it) }
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Eigene Barcode-Zuordnungen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${Repo.userCodes.size} gespeichert")
                if (Repo.userCodes.isNotEmpty()) OutlinedButton(onClick = { Repo.clearAssignments() }) { Text("Alle löschen") }
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("So liest du die Ergebnisse", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("🟢 Koscher laut ORD – das Produkt steht auf der Liste.")
                Text("🟡 Bitte prüfen – ähnliche Produkte stehen auf der Liste, oft nur bestimmte Sorten.")
                Text("⚪ Nicht gelistet – keine Aussage. Die ORD hat es nicht geprüft; schau nach einem Hechscher.")
                Text(
                    "Die Barcodes stammen aus einem automatischen Abgleich mit Open Food Facts, nicht von der ORD. Achte bei Hinweisen wie „alle außer …“ auf die genaue Packung.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "Quelle: Koscherliste der Orthodoxen Rabbinerkonferenz Deutschland. Inoffizielle App für den privaten Gebrauch, ohne Gewähr. Produktbilder und Barcodes: Open Food Facts (ODbL).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { uri.openUri("https://www.ordonline.de/koscherliste/") }, modifier = Modifier.fillMaxWidth()) {
            Text("Koscherliste auf ordonline.de öffnen")
        }
        OutlinedButton(onClick = { uri.openUri("https://github.com/Blauerbarsch/Koscher-Liste-App/releases/latest") }, modifier = Modifier.fillMaxWidth()) {
            Text("Neueste App-Version")
        }
    }
}
