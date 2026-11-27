package com.karuhundeveloper.notification.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karuhundeveloper.notification.ui.theme.RelayTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppTag(
    packageName: String,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val colors = RelayTheme.colors
    val context = LocalContext.current
    val shape = RoundedCornerShape(4.dp)

    val resolvedLabel by produceState(initialValue = label, packageName) {
        if (label.isNullOrBlank()) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val manager = context.packageManager
                    manager.getApplicationLabel(
                        manager.getApplicationInfo(packageName, 0)
                    ).toString()
                }.getOrNull()
            }
        }
    }
    val text = resolvedLabel ?: packageName.substringAfterLast('.')

    Row(
        modifier = modifier
            .clip(shape)
            .border(1.dp, colors.hairline, shape)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(
            packageName = packageName,
            label = text,
            size = 16.dp
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
