package com.karuhundeveloper.notification.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.karuhundeveloper.notification.ui.theme.RelayShapes
import com.karuhundeveloper.notification.ui.theme.RelayTheme

enum class NoticeTone { Info, Warning, Error }

@Composable
fun InlineNotice(
    title: String,
    body: String,
    tone: NoticeTone,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = RelayTheme.colors
    val toneColor = when (tone) {
        NoticeTone.Info -> colors.inkMuted
        NoticeTone.Warning -> colors.warning
        NoticeTone.Error -> colors.error
    }
    val icon: ImageVector = when (tone) {
        NoticeTone.Info -> Icons.Outlined.Info
        NoticeTone.Warning -> Icons.Outlined.Warning
        NoticeTone.Error -> Icons.Outlined.ErrorOutline
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RelayShapes.medium,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.hairline)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = toneColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.ink
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted
                )
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.size(6.dp))
                    TextButton(
                        onClick = onAction,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = toneColor
                        )
                    }
                }
            }
        }
    }
}
