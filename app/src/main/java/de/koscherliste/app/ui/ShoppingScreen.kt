package de.koscherliste.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.koscherliste.app.data.Product
import de.koscherliste.app.data.Repo
import de.koscherliste.app.data.ShopItem

@Composable
fun ShoppingScreen(padding: PaddingValues, onOpen: (Product) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val add = { Repo.addText(text); text = "" }
    val items = Repo.shopping.sortedBy { it.checked }

    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Eigenen Eintrag hinzufügen") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
            )
            IconButton(onClick = add, enabled = text.isNotBlank()) { Icon(Icons.Filled.Add, "Hinzufügen") }
        }
        if (items.isEmpty()) {
            Text(
                "Deine Einkaufsliste ist leer. Tippe in der Liste oder beim Scannen auf das Listen-Symbol, um Produkte hinzuzufügen.",
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { item ->
                ShopRow(item, onOpen)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ShopRow(item: ShopItem, onOpen: (Product) -> Unit) {
    val p = Repo.product(item.productId)
    Row(
        Modifier.clickable { if (p != null) onOpen(p) else Repo.setChecked(item, !item.checked) }.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.checked, onCheckedChange = { Repo.setChecked(item, it) })
        if (p != null) ProductImage(p.image, p.name, 40.dp, p.emoji)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                p?.name ?: item.text,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (p != null) {
                Text(p.manufacturer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!item.checked) ProductBadges(p)
            }
        }
        IconButton(onClick = { Repo.remove(item) }) { Icon(Icons.Filled.Delete, "Löschen") }
    }
}

fun shoppingText(): String = Repo.shopping.filter { !it.checked }.joinToString("\n") { i ->
    val p = Repo.product(i.productId)
    "• " + (p?.let { "${it.name} (${it.manufacturer})" + if (it.isDairy) " – milchig" else "" } ?: i.text)
}
