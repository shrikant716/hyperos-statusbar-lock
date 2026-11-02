package dev.sk.focuslock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk.focuslock.Prefs

/**
 * Full-screen app picker: search, system-app filter, checkboxes, selected apps first.
 * [allowManual] adds a chip for typing a package that has no launcher icon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(
    title: String,
    initial: Set<String>,
    allowManual: Boolean,
    onSave: (Set<String>) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var selected by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    var manualDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { apps = AppRepository.launchable(context) }
    BackHandler(onBack = onClose)

    val scroll = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onSave(selected) },
                icon = { Icon(Icons.Rounded.Check, contentDescription = null) },
                text = { Text(if (selected.isEmpty()) "Save" else "Save (${selected.size})") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = showSystem,
                    onClick = { showSystem = !showSystem },
                    label = { Text("System apps") },
                    leadingIcon = if (showSystem) {
                        { Icon(Icons.Rounded.Check, null, Modifier.padding(0.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(),
                )
                if (allowManual) {
                    FilterChip(
                        selected = false,
                        onClick = { manualDialog = true },
                        label = { Text("Add by package name") },
                        leadingIcon = { Icon(Icons.Rounded.Add, null) },
                    )
                }
            }

            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val known = list.associateBy { it.pkg }
                // Selected packages without a launcher icon still show up (and can be unticked).
                val extra = selected.filter { it !in known }.map {
                    AppEntry(it, AppRepository.label(context, it), system = true)
                }
                val q = query.trim().lowercase()
                val shown = (extra + list)
                    .filter { showSystem || !it.system || it.pkg in selected }
                    .filter { q.isEmpty() || it.label.lowercase().contains(q) || it.pkg.contains(q) }
                    .sortedWith(compareByDescending<AppEntry> { it.pkg in initial })

                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(shown, key = { it.pkg }) { app ->
                        val checked = app.pkg in selected
                        ListItem(
                            leadingContent = { AppIcon(app.pkg) },
                            headlineContent = {
                                Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(
                                    app.pkg,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            trailingContent = {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        selected = if (checked) selected - app.pkg else selected + app.pkg
                                    },
                                )
                            },
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.large)
                                .clickable {
                                    selected = if (checked) selected - app.pkg else selected + app.pkg
                                },
                        )
                    }
                }
            }
        }
    }

    if (manualDialog) {
        var text by remember { mutableStateOf("") }
        val valid = Prefs.parse(text).size == 1
        AlertDialog(
            onDismissRequest = { manualDialog = false },
            title = { Text("Add package") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.trim() },
                    label = { Text("Package name") },
                    placeholder = { Text("com.example.app") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = {
                        selected = selected + Prefs.parse(text)
                        manualDialog = false
                    },
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { manualDialog = false }) { Text("Cancel") }
            },
        )
    }
}
