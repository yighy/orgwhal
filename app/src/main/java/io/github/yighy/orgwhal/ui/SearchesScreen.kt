package io.github.yighy.orgwhal.ui

import io.github.yighy.orgwhal.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Saved searches: a tap runs one over all notebooks, the pencil edits it. A long press selects:
 * the selection can be deleted, and a single one moved up or down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchesScreen(viewModel: AppViewModel, snackbar: SnackbarHostState) {
    val searches by viewModel.savedSearches.collectAsState()
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    val selecting = selected.isNotEmpty()
    fun toggle(index: Int) {
        selected = if (index in selected) selected - index else selected + index
    }
    BackHandler(enabled = selecting) { selected = emptySet() }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text(pluralStringResource(R.plurals.n_selected, selected.size, selected.size)) },
                    colors = blendedTopBarColors(),
                    navigationIcon = {
                        IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Default.Close, stringResource(R.string.clear_selection)) }
                    },
                    actions = {
                        // Order: one search at a time
                        selected.singleOrNull()?.let { index ->
                            IconButton(enabled = index > 0, onClick = { selected = setOf(viewModel.moveSavedSearch(index, -1)) }) {
                                Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.move_up))
                            }
                            IconButton(enabled = index < searches.lastIndex, onClick = { selected = setOf(viewModel.moveSavedSearch(index, 1)) }) {
                                Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.move_down))
                            }
                        }
                        IconButton(onClick = { viewModel.deleteSavedSearches(selected); selected = emptySet() }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.searches)) },
                    colors = blendedTopBarColors(),
                    navigationIcon = {
                        IconButton(onClick = { viewModel.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    },
                )
            }
        },
        snackbarHost = { SwipeableSnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 48.dp)) {
            if (searches.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_saved_searches),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            // Keyed by name and query (and rank among identical ones), so a moved search slides to its place
            val keys = searches.mapIndexed { i, s -> "${s.name}\u0000${s.query}\u0000${searches.subList(0, i).count { it == s }}" }
            itemsIndexed(searches, key = { i, _ -> keys[i] }) { index, search ->
                val isSelected = index in selected
                ListItem(
                    selected = isSelected,
                    onClick = { if (selecting) toggle(index) else viewModel.runSavedSearch(search) },
                    onLongClick = { toggle(index) },
                    supportingContent = { Text(search.query, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    leadingContent = {
                        if (isSelected) {
                            Box(
                                Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Default.Check, stringResource(R.string.item_selected), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary) }
                        } else {
                            Icon(Icons.Default.Search, null)
                        }
                    },
                    trailingContent = {
                        if (!selecting) {
                            IconButton(onClick = { viewModel.editSavedSearch(index) }) { Icon(Icons.Default.Edit, stringResource(R.string.edit_search, search.name)) }
                        }
                    },
                    modifier = Modifier.animateItem(),
                ) { Text(search.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}
