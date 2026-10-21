package de.koscherliste.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.koscherliste.app.data.Product
import de.koscherliste.app.data.Repo

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    p: Product,
    padding: PaddingValues,
    assignCode: String?,
    onAssign: (String) -> Unit,
    onScanAssign: () -> Unit,
    onFilter: (Filter) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProductImage(p.image, p.name, 96.dp)
            Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(p.manufacturer, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("✅ Steht auf der Koscherliste der ORD", fontWeight = FontWeight.SemiBold)
                if (p.notes.isNotBlank()) Text("Hinweis: ${p.notes}", color = MaterialTheme.colorScheme.error)
                Field("Milchig") {
                    if (p.dairyLabels.isEmpty()) Text("kein Vermerk", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { p.dairyLabels.forEach { Badge(it, DairyColor) } }
                }
                if (p.pessach.isNotEmpty()) Field("Pessach") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { p.pessach.forEach { Badge(it, PessachColor) } }
                }
            }
        }

        Field("Kategorie") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                p.categories.forEach { c -> AssistChip(onClick = { onFilter(Filter(FilterGroup.CATEGORY, c)) }, label = { Text(c) }) }
            }
        }
        Field("Hersteller") {
            AssistChip(onClick = { onFilter(Filter(FilterGroup.MANUFACTURER, p.manufacturer)) }, label = { Text("Alle von ${p.manufacturer}") })
        }
        Field("Zertifikate") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                p.certificates.forEach { c -> AssistChip(onClick = { onFilter(Filter(FilterGroup.CERT, c)) }, label = { Text(c) }) }
            }
        }

        val inList = Repo.inShopping(p.id)
        if (assignCode != null) {
            Button(onClick = { onAssign(assignCode) }, modifier = Modifier.fillMaxWidth()) {
                Text("Barcode $assignCode diesem Produkt zuordnen")
            }
        }
        Button(onClick = { Repo.toggleShopping(p) }, modifier = Modifier.fillMaxWidth()) {
            Icon(if (inList) Icons.AutoMirrored.Filled.PlaylistAddCheck else Icons.AutoMirrored.Filled.PlaylistAdd, null)
            Spacer(Modifier.size(8.dp))
            Text(if (inList) "Auf der Einkaufsliste – entfernen" else "Auf die Einkaufsliste")
        }

        val codes = Repo.barcodesFor(p.id)
        Field("Bekannte Barcodes") {
            if (codes.isEmpty()) Text("Noch keiner. Scanne die Packung, damit der Scanner dieses Produkt künftig direkt erkennt.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            codes.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c + if (c in Repo.userCodes) "  (von dir)" else "", Modifier.weight(1f))
                    if (c in Repo.userCodes) IconButton(onClick = { Repo.unassign(c) }) { Icon(Icons.Filled.Close, "Zuordnung löschen") }
                }
            }
            OutlinedButton(onClick = onScanAssign, modifier = Modifier.padding(top = 6.dp)) {
                Icon(Icons.Filled.QrCodeScanner, null)
                Spacer(Modifier.size(8.dp))
                Text("Barcode scannen und zuordnen")
            }
        }
        Text(
            "Quelle: Koscherliste der Orthodoxen Rabbinerkonferenz Deutschland (ordonline.de), Stand ${Repo.fetched}. " +
                "Angaben ohne Gewähr – Rezepturen können sich ändern.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Field(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        content()
    }
}
