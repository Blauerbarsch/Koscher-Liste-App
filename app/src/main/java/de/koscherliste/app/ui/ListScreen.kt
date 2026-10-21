package de.koscherliste.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.koscherliste.app.data.Product
import de.koscherliste.app.data.Repo
import de.koscherliste.app.match.Text as T

enum class FilterGroup(val title: String) { CATEGORY("Kategorie"), MANUFACTURER("Hersteller"), CERT("Zertifikat"), DAIRY("Milchig"), PESSACH("Pessach") }

data class Filter(val group: FilterGroup, val value: String)

private fun Product.values(g: FilterGroup): List<String> = when (g) {
    FilterGroup.CATEGORY -> categories
    FilterGroup.MANUFACTURER -> listOf(manufacturer)
    FilterGroup.CERT -> certificates
    FilterGroup.DAIRY -> dairyLabels.ifEmpty { listOf(NO_DAIRY) }
    FilterGroup.PESSACH -> pessach
}

const val NO_DAIRY = "ohne Milchig-Vermerk"

class ListState {
    var query by mutableStateOf("")
    var filters by mutableStateOf(setOf<Filter>())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(state: ListState, onOpen: (Product) -> Unit, picker: Boolean = false, padding: PaddingValues) {
    val all = Repo.products
    var showSheet by remember { mutableStateOf(false) }

    val result by remember(all) {
        derivedStateOf {
            val words = T.norm(state.query).split(' ').filter { it.isNotEmpty() }
            val byGroup = state.filters.groupBy { it.group }
            all.filter { p ->
                words.all { w -> p.searchText.contains(w) } &&
                    byGroup.all { (g, fs) -> p.values(g).any { v -> fs.any { it.value == v } } }
            }
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(state.query, state.filters) { listState.scrollToItem(0) }

    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { state.query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Produkt, Hersteller, Kategorie …") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) IconButton(onClick = { state.query = "" }) { Icon(Icons.Filled.Close, "Leeren") }
                },
            )
            IconButton(onClick = { showSheet = true }) {
                Icon(Icons.Filled.FilterList, "Filter", tint = if (state.filters.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
            }
        }
        if (state.filters.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.filters.toList()) { f ->
                    InputChip(
                        selected = true,
                        onClick = { state.filters -= f },
                        label = { Text(f.value) },
                        trailingIcon = { Icon(Icons.Filled.Close, "Entfernen") },
                    )
                }
            }
        }
        Text(
            if (picker) "Wähle das Produkt, das zu diesem Barcode gehört (${result.size})"
            else "${result.size} von ${all.size} Produkten",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(result, key = { it.id }) { p ->
                ProductRow(p, onClick = { onOpen(p) }, showCart = !picker)
                HorizontalDivider(Modifier.padding(start = 76.dp))
            }
            if (result.isEmpty()) item {
                Text(
                    "Nichts gefunden. Wenn ein Produkt hier fehlt, heißt das nur, dass die ORD es nicht geprüft hat – schau auf der Packung nach einem Koscher-Siegel.",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            FilterSheet(state, all)
        }
    }
}

@Composable
private fun FilterSheet(state: ListState, all: List<Product>) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var q by remember { mutableStateOf("") }
    val group = FilterGroup.entries[tab]
    val counts = remember(all, group) {
        all.flatMap { it.values(group) }.groupingBy { it }.eachCount().toList().sortedBy { it.first.lowercase() }
    }
    Column(Modifier.fillMaxWidth().height(560.dp)) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            FilterGroup.entries.forEachIndexed { i, g ->
                Tab(selected = tab == i, onClick = { tab = i; q = "" }, text = { Text(g.title, maxLines = 1) })
            }
        }
        if (counts.size > 12) {
            OutlinedTextField(
                value = q, onValueChange = { q = it }, singleLine = true,
                placeholder = { Text("${group.title} suchen") },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
        }
        val shown = counts.filter { q.isBlank() || it.first.contains(q, ignoreCase = true) }
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.first }) { (value, n) ->
                val f = Filter(group, value)
                val on = f in state.filters
                Row(
                    Modifier.fillMaxWidth().clickable { state.filters = if (on) state.filters - f else state.filters + f }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                    Text(value, Modifier.weight(1f))
                    Text("$n", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
                }
            }
            if (shown.isEmpty()) item { Text("Keine Einträge", Modifier.padding(24.dp)) }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
            if (state.filters.isNotEmpty()) TextButton(onClick = { state.filters = emptySet() }) { Text("Alle Filter löschen") }
        }
        Spacer(Modifier.height(16.dp))
    }
}
