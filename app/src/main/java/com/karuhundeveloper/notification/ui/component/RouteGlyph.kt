package com.karuhundeveloper.notification.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karuhundeveloper.notification.ui.theme.RelayTheme

/**
 * The product's route mark: a source node, a line, and an endpoint.
 * Used only where the interface needs to show the relay idea, not as page decoration.
 */
@Composable
fun RouteGlyph(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val colors = RelayTheme.colors

    Canvas(modifier.size(size)) {
        val width = this.size.width
        val height = this.size.height
        val nodeRadius = width * 0.11f
        val left = Offset(width * 0.22f, height * 0.5f)
        val right = Offset(width * 0.78f, height * 0.5f)

        drawLine(
            color = colors.hairline,
            start = left,
            end = right,
            strokeWidth = width * 0.035f,
            cap = StrokeCap.Round
        )
        drawCircle(color = colors.ink, radius = nodeRadius, center = left)
        drawCircle(color = colors.accent, radius = nodeRadius * 1.2f, center = right)
        drawCircle(color = colors.background, radius = nodeRadius * 0.45f, center = right)
    }
}
