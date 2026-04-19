package com.qoffee.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class QoffeeDashboardColors(
    val pageTop: Color,
    val pageBottom: Color,
    val ambientGlow: Color,
    val ambientGlowSecondary: Color,
    val panel: Color,
    val panelMuted: Color,
    val panelStrong: Color,
    val panelStroke: Color,
    val panelStrokeStrong: Color,
    val accentSoft: Color,
    val accentGlow: Color,
    val success: Color,
    val warning: Color,
    val shell: Color,
    val shellElevated: Color,
    val shellDivider: Color,
    val titleText: Color,
    val titleShadow: Color,
    val titleScrim: Color,
)

@Immutable
data class QoffeeSpacing(
    val pageHorizontal: Dp = 20.dp,
    val pageVertical: Dp = 20.dp,
    val section: Dp = 18.dp,
    val block: Dp = 14.dp,
    val item: Dp = 10.dp,
    val chip: Dp = 8.dp,
)

private val LocalDashboardColors = staticCompositionLocalOf<QoffeeDashboardColors> {
    error("Qoffee dashboard colors are not provided")
}

private val LocalDashboardSpacing = staticCompositionLocalOf { QoffeeSpacing() }

internal val LightDashboardColors = QoffeeDashboardColors(
    pageTop = Color(0xFFF7EDE2),
    pageBottom = Color(0xFFECE0D4),
    ambientGlow = Copper.copy(alpha = 0.28f),
    ambientGlowSecondary = Sage.copy(alpha = 0.18f),
    panel = Color(0xFFF7F0E7),
    panelMuted = Color(0xFFF1E6DA),
    panelStrong = Color(0xFFEEE0D0),
    panelStroke = Color(0xFFD9C6B1),
    panelStrokeStrong = Color(0xFFC9B198),
    accentSoft = Color(0xFFEAD4BC),
    accentGlow = Copper.copy(alpha = 0.22f),
    success = Forest,
    warning = Ember,
    shell = Color(0xFFF1E6DA),
    shellElevated = Color(0xFFF7EFE5),
    shellDivider = Color(0xFFD1BCA5),
    titleText = Onyx,
    titleShadow = CopperMuted.copy(alpha = 0.16f),
    titleScrim = Color(0xFFFFFBF7).copy(alpha = 0.54f),
)

internal val DarkDashboardColors = QoffeeDashboardColors(
    pageTop = Color(0xFF13100E),
    pageBottom = Color(0xFF1A1512),
    ambientGlow = Copper.copy(alpha = 0.24f),
    ambientGlowSecondary = Sage.copy(alpha = 0.16f),
    panel = Color(0xFF1C1714),
    panelMuted = Color(0xFF241E1A),
    panelStrong = Color(0xFF2C241F),
    panelStroke = Color(0xFF41362F),
    panelStrokeStrong = Color(0xFF57463C),
    accentSoft = Color(0xFF3A2A20),
    accentGlow = CopperBright.copy(alpha = 0.22f),
    success = Sage,
    warning = Color(0xFFD68D72),
    shell = Color(0xFF1B1714),
    shellElevated = Color(0xFF221C18),
    shellDivider = Color(0xFF3A312B),
    titleText = Foam,
    titleShadow = Color(0xFF050403).copy(alpha = 0.72f),
    titleScrim = Color(0xFF0C0A08).copy(alpha = 0.42f),
)

object QoffeeDashboardTheme {
    val colors: QoffeeDashboardColors
        @Composable get() = LocalDashboardColors.current

    val spacing: QoffeeSpacing
        @Composable get() = LocalDashboardSpacing.current
}

@Composable
internal fun ProvideQoffeeDashboardTokens(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalDashboardColors provides if (darkTheme) DarkDashboardColors else LightDashboardColors,
        LocalDashboardSpacing provides QoffeeSpacing(),
        content = content,
    )
}

@Composable
fun qoffeePageBackgroundBrush(): Brush {
    val colors = QoffeeDashboardTheme.colors
    return Brush.verticalGradient(
        colors = listOf(colors.pageTop, colors.pageBottom),
    )
}

@Composable
fun qoffeePanelBrush(strong: Boolean = false): Brush {
    val colors = QoffeeDashboardTheme.colors
    return Brush.verticalGradient(
        colors = if (strong) {
            listOf(colors.panelStrong, colors.panel)
        } else {
            listOf(colors.panel, colors.panelMuted)
        },
    )
}

@Composable
fun qoffeeBottomShellBrush(): Brush {
    val colors = QoffeeDashboardTheme.colors
    return Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            colors.shell.copy(alpha = 0.82f),
            colors.shellElevated,
        ),
    )
}
