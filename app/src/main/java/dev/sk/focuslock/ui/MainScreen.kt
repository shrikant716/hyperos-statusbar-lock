package dev.sk.focuslock.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class SetupStatus(
    val shizukuRunning: Boolean = false,
    val shizukuPermission: Boolean = false,
    val writeSecure: Boolean = false,
    val usageAccess: Boolean = false,
    val notifications: Boolean = false,
    val settingValue: Int = 1,
) {
    val ready: Boolean get() = writeSecure && usageAccess
    val allGood: Boolean get() = ready && notifications
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    status: SetupStatus,
    running: Boolean,
    lockedPkg: String?,
    lockedApps: Set<String>,
    overlayApps: Set<String>,
    startOnBoot: Boolean,
    granting: Boolean,
    versionName: String,
    snackbar: SnackbarHostState,
    onToggle: () -> Unit,
    onGrant: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onAskNotifications: () -> Unit,
    onPickLocked: () -> Unit,
    onRemoveLocked: (String) -> Unit,
    onPickOverlay: () -> Unit,
    onStartOnBoot: (Boolean) -> Unit,
    onRestore: () -> Unit,
    onRefreshValue: () -> Unit,
    canAddTile: Boolean,
    onAddTile: () -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Status Bar Lock") },
                scrollBehavior = scroll,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                HeroCard(
                    running = running,
                    lockedLabel = lockedPkg?.let { AppRepository.label(context, it) },
                    appCount = lockedApps.size,
                    onToggle = onToggle,
                )
            }

            if (!status.allGood) {
                item {
                    SetupCard(status, granting, onGrant, onOpenUsageAccess, onAskNotifications)
                }
            }

            item {
                SectionTitle("Locked apps") {
                    TextButton(onClick = onPickLocked) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (lockedApps.isEmpty()) "Add" else "Edit")
                    }
                }
                GroupCard {
                    if (lockedApps.isEmpty()) {
                        ListItem(
                            headlineContent = { Text("No apps yet") },
                            supportingContent = { Text("Pick the games where the top edge should stay locked") },
                            leadingContent = { Icon(Icons.Rounded.Add, null) },
                            colors = transparentItem(),
                            modifier = Modifier.clickable(onClick = onPickLocked),
                        )
                    }
                    lockedApps.forEach { pkg ->
                        val isLockedNow = pkg == lockedPkg
                        ListItem(
                            leadingContent = { AppIcon(pkg) },
                            headlineContent = {
                                Text(
                                    AppRepository.label(context, pkg),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text(
                                    if (isLockedNow) "Locked now" else pkg,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isLockedNow) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { onRemoveLocked(pkg) }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove")
                                }
                            },
                            colors = transparentItem(),
                        )
                    }
                }
            }

            item {
                SectionTitle("Options")
                GroupCard {
                    if (canAddTile) {
                        ListItem(
                            leadingContent = { Icon(Icons.Rounded.Dashboard, null) },
                            headlineContent = { Text("Add Quick Settings tile") },
                            supportingContent = { Text("Start or stop from the status bar without opening the app") },
                            trailingContent = { Icon(Icons.Rounded.Add, null) },
                            colors = transparentItem(),
                            modifier = Modifier.clickable(onClick = onAddTile),
                        )
                    }
                    ListItem(
                        leadingContent = { Icon(Icons.Rounded.PowerSettingsNew, null) },
                        headlineContent = { Text("Start after reboot") },
                        supportingContent = { Text("Bring the watcher back when the phone restarts") },
                        trailingContent = { Switch(checked = startOnBoot, onCheckedChange = onStartOnBoot) },
                        colors = transparentItem(),
                        modifier = Modifier.clickable { onStartOnBoot(!startOnBoot) },
                    )
                    ListItem(
                        leadingContent = { Icon(Icons.Rounded.Layers, null) },
                        headlineContent = { Text("Overlay apps") },
                        supportingContent = {
                            Text("Keep the lock while these pop up over a game · ${overlayApps.size}")
                        },
                        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                        colors = transparentItem(),
                        modifier = Modifier.clickable(onClick = onPickOverlay),
                    )
                }
            }

            item {
                SectionTitle("Advanced")
                GroupCard {
                    ListItem(
                        leadingContent = { Icon(Icons.Rounded.Tune, null) },
                        headlineContent = { Text("user_setup_complete") },
                        supportingContent = {
                            Text(if (status.settingValue == 0) "0 · top edge blocked" else "1 · normal")
                        },
                        trailingContent = { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh") },
                        colors = transparentItem(),
                        modifier = Modifier.clickable(onClick = onRefreshValue),
                    )
                    if (status.settingValue == 0) {
                        ListItem(
                            leadingContent = {
                                Icon(
                                    Icons.Rounded.SettingsBackupRestore, null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            headlineContent = { Text("Restore to 1") },
                            supportingContent = { Text("Stuck at 0. Tap to put the system back to normal") },
                            colors = transparentItem(),
                            modifier = Modifier.clickable(onClick = onRestore),
                        )
                    }
                }
            }

            item {
                Text(
                    "v$versionName",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun HeroCard(running: Boolean, lockedLabel: String?, appCount: Int, onToggle: () -> Unit) {
    val container by animateColorAsState(
        if (running) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "hero",
    )
    val onContainer = if (running) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface
    val badge by animateColorAsState(
        if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        label = "badge",
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).background(badge, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (running) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        contentDescription = null,
                        tint = if (running) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        if (running) "Active" else "Off",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        when {
                            running && lockedLabel != null -> "Top edge locked in $lockedLabel"
                            running -> "Waiting for one of your $appCount app(s)"
                            else -> "Start to block the top-edge swipe in your games"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            if (running) {
                Button(
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(Icons.Rounded.Stop, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Button(
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun SetupCard(
    s: SetupStatus,
    granting: Boolean,
    onGrant: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onAskNotifications: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                "Finish setup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(4.dp))
            SetupRow(
                "Shizuku",
                s.shizukuRunning && s.shizukuPermission,
                when {
                    !s.shizukuRunning -> "Not running. Start it in the Shizuku app"
                    !s.shizukuPermission -> "Running. Tap Grant to allow this app"
                    else -> "Connected"
                },
            )
            SetupRow("Change secure setting", s.writeSecure, "WRITE_SECURE_SETTINGS")
            SetupRow("Usage access", s.usageAccess, "To see which app is in front")
            SetupRow("Notifications", s.notifications, "Shows the watcher's status")

            AnimatedVisibility(visible = true) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!s.ready) {
                        Button(onClick = onGrant, enabled = s.shizukuRunning && !granting) {
                            Text(if (granting) "Granting…" else "Grant with Shizuku")
                        }
                    }
                    if (!s.usageAccess) {
                        TextButton(onClick = onOpenUsageAccess) { Text("Usage settings") }
                    }
                    if (s.ready && !s.notifications) {
                        Button(onClick = onAskNotifications) { Text("Allow notifications") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupRow(title: String, ok: Boolean, detail: String) {
    ListItem(
        leadingContent = {
            Icon(
                if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                contentDescription = if (ok) "Done" else "Missing",
                tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        colors = transparentItem(),
    )
}

@Composable
private fun SectionTitle(text: String, action: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        action()
    }
}

@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun transparentItem() = ListItemDefaults.colors(containerColor = Color.Transparent)
