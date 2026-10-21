package de.koscherliste.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.koscherliste.app.data.Product
import de.koscherliste.app.match.ScanResult
import de.koscherliste.app.match.Status

val Green = Color(0xFF2E9E5B)
val Amber = Color(0xFFE0A100)
val Grey = Color(0xFF8A939B)

@Composable
fun ScanScreen(
    padding: PaddingValues,
    result: ScanResult?,
    busy: Boolean,
    onBarcode: () -> Unit,
    onPhoto: () -> Unit,
    onOpen: (Product, String?) -> Unit,
    onPick: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onBarcode, modifier = Modifier.weight(1f).height(72.dp), enabled = !busy) {
                Icon(Icons.Filled.QrCodeScanner, null)
                Spacer(Modifier.size(8.dp))
                Text("Barcode")
            }
            FilledTonalButton(onClick = onPhoto, modifier = Modifier.weight(1f).height(72.dp), enabled = !busy) {
                Icon(Icons.Filled.CameraAlt, null)
                Spacer(Modifier.size(8.dp))
                Text("Packungsfoto")
            }
        }
        if (busy) Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (result == null) Text(
            "Scanne den Barcode eines Produkts. Hat es keinen, fotografiere die Vorderseite der Packung – die App liest den Namen direkt auf dem Handy.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else ResultCard(result, onOpen, onPick)
    }
}

@Composable
private fun ResultCard(r: ScanResult, onOpen: (Product, String?) -> Unit, onPick: (String) -> Unit) {
    val (color, icon, title) = when (r.status) {
        Status.KOSHER -> Triple(Green, Icons.Filled.CheckCircle, "Koscher laut ORD")
        Status.CHECK -> Triple(Amber, Icons.Filled.Warning, "Bitte prüfen")
        Status.UNKNOWN -> Triple(Grey, Icons.Filled.Info, "Nicht gelistet")
    }
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.14f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(40.dp))
                Spacer(Modifier.size(12.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
            }
            Text(r.reason)
            if (r.status == Status.UNKNOWN) Text(
                "Keine Aussage möglich: Die ORD hat dieses Produkt nicht geprüft. Es kann trotzdem koscher sein – achte auf ein Hechscher auf der Packung.",
                style = MaterialTheme.typography.bodySmall,
            )
            r.code?.let { Text("Barcode: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }

    r.off?.let { off ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProductImage(off.image, off.name.ifBlank { "?" }, 56.dp)
            Column(Modifier.padding(start = 12.dp)) {
                Text(off.name.ifBlank { "Unbenanntes Produkt" }, fontWeight = FontWeight.SemiBold)
                Text(off.brands, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("laut Open Food Facts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (off.koscherLabel && r.status != Status.KOSHER) Text(
            "ℹ️ Laut Open Food Facts trägt die Packung ein Koscher-Siegel. Das ist nicht von der ORD geprüft.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    r.product?.let { p ->
        Card {
            ProductRow(p, onClick = { onOpen(p, null) })
        }
    }

    if (r.candidates.isNotEmpty()) {
        Text(
            if (r.status == Status.KOSHER) "Ähnliche Produkte" else "Mögliche Treffer – tippe dein Produkt an",
            style = MaterialTheme.typography.titleSmall,
        )
        Card {
            Column {
                r.candidates.forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider()
                    ProductRow(p, onClick = { onOpen(p, r.code) })
                }
            }
        }
    }

    if (r.code != null) {
        OutlinedButton(onClick = { onPick(r.code) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Link, null)
            Spacer(Modifier.size(8.dp))
            Text(if (r.status == Status.KOSHER) "Falsches Produkt? Selbst zuordnen" else "In der Liste suchen und zuordnen")
        }
    }
}
