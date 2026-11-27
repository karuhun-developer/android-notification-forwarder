package com.karuhundeveloper.notification.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karuhundeveloper.notification.ui.theme.RelayMonoSmall
import com.karuhundeveloper.notification.ui.theme.RelayTheme

@Composable
fun MethodLabel(
    method: String,
    modifier: Modifier = Modifier
) {
    val colors = RelayTheme.colors

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, colors.hairline)
    ) {
        Text(
            text = method.uppercase(),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = RelayMonoSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.inkMuted
        )
    }
}
