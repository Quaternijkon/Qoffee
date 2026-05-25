package com.qoffee.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qoffee.core.model.AppThemeStyle

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
    val pageHorizontal: Dp = 16.dp,
    val pageVertical: Dp = 14.dp,
    val section: Dp = 12.dp,
    val block: Dp = 10.dp,
    val item: Dp = 8.dp,
    val chip: Dp = 6.dp,
)

private val LocalDashboardColors = staticCompositionLocalOf<QoffeeDashboardColors> {
    error("Qoffee dashboard colors are not provided")
}

private val LocalDashboardSpacing = staticCompositionLocalOf { QoffeeSpacing() }

private val LocalDashboardThemeStyle = staticCompositionLocalOf { AppThemeStyle.CLASSIC }

internal val LightDashboardColors = QoffeeDashboardColors(
    pageTop = Paper,
    pageBottom = Color(0xFFF0F3F5),
    ambientGlow = Marine.copy(alpha = 0.08f),
    ambientGlowSecondary = Copper.copy(alpha = 0.10f),
    panel = Porcelain,
    panelMuted = Color(0xFFF3F6F7),
    panelStrong = Color(0xFFEAF1F2),
    panelStroke = Mist,
    panelStrokeStrong = Marine.copy(alpha = 0.28f),
    accentSoft = MarineSoft,
    accentGlow = Copper.copy(alpha = 0.14f),
    success = Pine,
    warning = CopperMuted,
    shell = Porcelain,
    shellElevated = Color(0xFFF8FAFA),
    shellDivider = Mist,
    titleText = Ink,
    titleShadow = Marine.copy(alpha = 0.08f),
    titleScrim = Porcelain.copy(alpha = 0.84f),
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

internal val MinimalLightDashboardColors = QoffeeDashboardColors(
    pageTop = Color(0xFFF7F9FC),
    pageBottom = Color(0xFFFFFFFF),
    ambientGlow = Color.Transparent,
    ambientGlowSecondary = Color.Transparent,
    panel = Color(0xFFFFFFFF),
    panelMuted = Color(0xFFF3F6FB),
    panelStrong = Color(0xFFEEF4FF),
    panelStroke = Color(0xFFE0E5EC),
    panelStrokeStrong = GoogleBlueRefined.copy(alpha = 0.22f),
    accentSoft = GoogleBlueSoft,
    accentGlow = GoogleBlueRefined.copy(alpha = 0.10f),
    success = GoogleGreenRefined,
    warning = GoogleYellowRefined,
    shell = Color(0xFFFFFFFF),
    shellElevated = Color(0xFFFAFBFF),
    shellDivider = Color(0xFFE1E6EE),
    titleText = Color(0xFF202124),
    titleShadow = GoogleBlueRefined.copy(alpha = 0.06f),
    titleScrim = Color(0xFFFFFFFF).copy(alpha = 0.90f),
)

internal val MinimalDarkDashboardColors = QoffeeDashboardColors(
    pageTop = Color(0xFF101318),
    pageBottom = Color(0xFF131821),
    ambientGlow = Color.Transparent,
    ambientGlowSecondary = Color.Transparent,
    panel = Color(0xFF171B22),
    panelMuted = Color(0xFF1F2630),
    panelStrong = Color(0xFF243044),
    panelStroke = Color(0xFF313946),
    panelStrokeStrong = GoogleBlueDark.copy(alpha = 0.36f),
    accentSoft = Color(0xFF17345F),
    accentGlow = GoogleBlueDark.copy(alpha = 0.16f),
    success = GoogleGreenDark,
    warning = GoogleYellowDark,
    shell = Color(0xFF11151B),
    shellElevated = Color(0xFF171B22),
    shellDivider = Color(0xFF2E3642),
    titleText = Color(0xFFE8EAED),
    titleShadow = Color.Transparent,
    titleScrim = Color(0xFF11151B).copy(alpha = 0.78f),
)

object QoffeeDashboardTheme {
    val colors: QoffeeDashboardColors
        @Composable get() = LocalDashboardColors.current

    val spacing: QoffeeSpacing
        @Composable get() = LocalDashboardSpacing.current

    val themeStyle: AppThemeStyle
        @Composable get() = LocalDashboardThemeStyle.current
}

@Composable
internal fun ProvideQoffeeDashboardTokens(
    darkTheme: Boolean,
    themeStyle: AppThemeStyle,
    content: @Composable () -> Unit,
) {
    val colors = when (themeStyle) {
        AppThemeStyle.CLASSIC -> if (darkTheme) DarkDashboardColors else LightDashboardColors
        AppThemeStyle.MINIMAL -> if (darkTheme) MinimalDarkDashboardColors else MinimalLightDashboardColors
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalDashboardColors provides colors,
        LocalDashboardSpacing provides QoffeeSpacing(),
        LocalDashboardThemeStyle provides themeStyle,
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
