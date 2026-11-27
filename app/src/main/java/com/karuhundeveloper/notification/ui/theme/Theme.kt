package com.karuhundeveloper.notification.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class RelayColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val ink: Color,
    val inkMuted: Color,
    val hairline: Color,
    val accent: Color,
    val error: Color,
    val onError: Color,
    val warning: Color
)

private val LightRelayColors = RelayColors(
    background = Color(0xFFF4F1EA),
    surface = Color(0xFFFBFAF6),
    surfaceRaised = Color(0xFFEFEBE1),
    ink = Color(0xFF17181A),
    inkMuted = Color(0xFF5E5B54),
    hairline = Color(0xFFD9D4C8),
    accent = Color(0xFF1E6B4E),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    warning = Color(0xFF8A5A00)
)

private val DarkRelayColors = RelayColors(
    background = Color(0xFF121316),
    surface = Color(0xFF191B1F),
    surfaceRaised = Color(0xFF21242A),
    ink = Color(0xFFEDEAE2),
    inkMuted = Color(0xFFA7A399),
    hairline = Color(0xFF2B2E34),
    accent = Color(0xFF6FD3A3),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF2B0A08),
    warning = Color(0xFFE0B15C)
)

private val LightScheme = lightColorScheme(
    primary = LightRelayColors.ink,
    onPrimary = LightRelayColors.background,
    primaryContainer = LightRelayColors.surfaceRaised,
    onPrimaryContainer = LightRelayColors.ink,
    secondary = LightRelayColors.ink,
    onSecondary = LightRelayColors.background,
    secondaryContainer = LightRelayColors.surfaceRaised,
    onSecondaryContainer = LightRelayColors.ink,
    background = LightRelayColors.background,
    onBackground = LightRelayColors.ink,
    surface = LightRelayColors.surface,
    onSurface = LightRelayColors.ink,
    surfaceVariant = LightRelayColors.surfaceRaised,
    onSurfaceVariant = LightRelayColors.inkMuted,
    outline = LightRelayColors.hairline,
    outlineVariant = LightRelayColors.hairline,
    error = LightRelayColors.error,
    onError = LightRelayColors.onError,
    errorContainer = Color(0xFFF7DDDA),
    onErrorContainer = LightRelayColors.error
)

private val DarkScheme = darkColorScheme(
    primary = DarkRelayColors.ink,
    onPrimary = DarkRelayColors.background,
    primaryContainer = DarkRelayColors.surfaceRaised,
    onPrimaryContainer = DarkRelayColors.ink,
    secondary = DarkRelayColors.ink,
    onSecondary = DarkRelayColors.background,
    secondaryContainer = DarkRelayColors.surfaceRaised,
    onSecondaryContainer = DarkRelayColors.ink,
    background = DarkRelayColors.background,
    onBackground = DarkRelayColors.ink,
    surface = DarkRelayColors.surface,
    onSurface = DarkRelayColors.ink,
    surfaceVariant = DarkRelayColors.surfaceRaised,
    onSurfaceVariant = DarkRelayColors.inkMuted,
    outline = DarkRelayColors.hairline,
    outlineVariant = DarkRelayColors.hairline,
    error = DarkRelayColors.error,
    onError = DarkRelayColors.onError,
    errorContainer = Color(0xFF3A1512),
    onErrorContainer = DarkRelayColors.error
)

val RelayShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

val LocalRelayColors = staticCompositionLocalOf { LightRelayColors }

object RelayTheme {
    val colors: RelayColors
        @Composable
        @ReadOnlyComposable
        get() = LocalRelayColors.current
}

@Composable
fun NotificationInterceptorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkRelayColors else LightRelayColors
    val scheme = if (darkTheme) DarkScheme else LightScheme

    CompositionLocalProvider(LocalRelayColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = RelayTypography,
            shapes = RelayShapes,
            content = content
        )
    }
}
