package com.qoffee.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Espresso,
    onPrimary = Foam,
    secondary = Copper,
    onSecondary = Graphite,
    tertiary = Sage,
    background = Linen,
    onBackground = Onyx,
    primaryContainer = Color(0xFF6A4025),
    onPrimaryContainer = Foam,
    secondaryContainer = Color(0xFFEED7BC),
    onSecondaryContainer = Onyx,
    tertiaryContainer = Color(0xFFD7E4D9),
    onTertiaryContainer = Graphite,
    surface = Foam,
    onSurface = Onyx,
    surfaceVariant = Color(0xFFEBDCCB),
    onSurfaceVariant = Dust,
    outline = Color(0xFFD0BDA7),
    outlineVariant = Color(0xFFE0D3C6),
    error = Ember,
    errorContainer = Color(0xFFF2DBD3),
)

private val DarkColors = darkColorScheme(
    primary = CopperBright,
    onPrimary = Graphite,
    secondary = Copper,
    onSecondary = Graphite,
    tertiary = Sage,
    primaryContainer = Mocha,
    onPrimaryContainer = Foam,
    secondaryContainer = Color(0xFF403025),
    onSecondaryContainer = Foam,
    tertiaryContainer = Color(0xFF314238),
    onTertiaryContainer = Foam,
    background = Graphite,
    onBackground = Foam,
    surface = Color(0xFF1B1715),
    onSurface = Foam,
    surfaceVariant = Color(0xFF27211D),
    onSurfaceVariant = Smoke,
    outline = Color(0xFF65574D),
    outlineVariant = Color(0xFF433832),
    error = Color(0xFFF0A38E),
    errorContainer = Color(0xFF5A2B23),
)

@Composable
fun QoffeeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    ProvideQoffeeDashboardTokens(darkTheme = darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = QoffeeTypography,
            shapes = QoffeeShapes,
            content = content,
        )
    }
}
