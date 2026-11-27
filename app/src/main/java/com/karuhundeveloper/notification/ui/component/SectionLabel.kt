package com.karuhundeveloper.notification.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karuhundeveloper.notification.ui.theme.RelayTheme

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null
) {
    val colors = RelayTheme.colors

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = colors.ink,
                modifier = Modifier.weight(1f)
            )
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = colors.hairline)
    }
}
