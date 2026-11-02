package dev.sk.focuslock.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class AppEntry(val pkg: String, val label: String, val system: Boolean)

object AppRepository {
    @Volatile
    private var cache: List<AppEntry>? = null
    private val icons = ConcurrentHashMap<String, ImageBitmap>()
    private val labels = ConcurrentHashMap<String, String>()

    /** Every app with a launcher icon, sorted by name. */
    suspend fun launchable(context: Context, refresh: Boolean = false): List<AppEntry> =
        withContext(Dispatchers.IO) {
            cache?.takeIf { !refresh }?.let { return@withContext it }
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            val list = pm.queryIntentActivities(intent, 0)
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != context.packageName }
                .map { ai ->
                    val label = ai.loadLabel(pm).toString()
                    labels[ai.packageName] = label
                    AppEntry(
                        pkg = ai.packageName,
                        label = label,
                        system = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                            ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0,
                    )
                }
                .sortedBy { it.label.lowercase() }
            cache = list
            list
        }

    fun label(context: Context, pkg: String): String = labels[pkg] ?: try {
        val pm = context.packageManager
        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString().also { labels[pkg] = it }
    } catch (_: Exception) {
        pkg
    }

    fun icon(context: Context, pkg: String): ImageBitmap? = icons[pkg] ?: try {
        context.packageManager.getApplicationIcon(pkg)
            .toBitmap(128, 128)
            .asImageBitmap()
            .also { icons[pkg] = it }
    } catch (_: Exception) {
        null
    }
}

/** App icon loaded off the main thread, with a neutral placeholder. */
@Composable
fun AppIcon(pkg: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, pkg) {
        value = withContext(Dispatchers.IO) { AppRepository.icon(context, pkg) }
    }
    val b = bitmap
    if (b != null) {
        Image(bitmap = b, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(
            modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.6f),
            )
        }
    }
}
