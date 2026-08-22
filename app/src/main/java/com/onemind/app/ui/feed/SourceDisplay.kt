package com.onemind.app.ui.feed

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.model.SourceType

/**
 * Shows the source of a Memory (app name + icon, or fallback label) on cards
 * and detail view.
 *
 * The source is resolved at most once per package per composition, and cached
 * by [remember] for the lifecycle of the composable. PackageManager calls are
 * cheap (metadata only, no I/O), so a separate caching layer is not needed.
 */
@Composable
fun SourceRow(
    memory: Memory,
    modifier: Modifier = Modifier
) {
    val sourceInfo = resolveSource(memory)
    if (sourceInfo == null) return

    Row(
        modifier = modifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        sourceInfo.icon?.let { icon ->
            Image(
                bitmap = icon.toBitmap(width = 14, height = 14).asImageBitmap(),
                contentDescription = "Source app icon",
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = sourceInfo.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Resolved source information for display.
 */
data class SourceInfo(
    val label: String,
    val icon: Drawable?
)

/**
 * Resolve the display name and icon for a Memory's source.
 *
 * Returns null when there is nothing worth showing (MANUAL with no
 * sourcePackage — the user already knows they typed it themselves).
 */
@Composable
fun resolveSource(memory: Memory): SourceInfo? {
    val context = LocalContext.current

    return remember(memory.sourceType, memory.sourcePackage) {
        resolveSourceImpl(context, memory.sourceType, memory.sourcePackage)
    }
}

/**
 * Pure logic, testable without Compose.
 */
fun resolveSourceImpl(
    context: Context,
    sourceType: SourceType,
    sourcePackage: String?
): SourceInfo? {
    // MANUAL with no package has nothing to say — the user knows they typed it.
    if (sourceType == SourceType.MANUAL && sourcePackage == null) return null

    // Try resolving the package to an app name + icon.
    if (sourcePackage != null) {
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(sourcePackage, 0)
            val label = pm.getApplicationLabel(appInfo).toString()
            val icon = pm.getApplicationIcon(appInfo)
            return SourceInfo(label = label, icon = icon)
        } catch (e: PackageManager.NameNotFoundException) {
            // App uninstalled — fall through to type-based label.
        }
    }

    // Fallback by source type.
    val label = when (sourceType) {
        SourceType.SCREENSHOT -> "Screenshot"
        SourceType.CLIPBOARD -> "Clipboard"
        SourceType.SHARE -> "Shared"
        SourceType.MANUAL -> "Manual"
    }

    return SourceInfo(label = label, icon = null)
}
