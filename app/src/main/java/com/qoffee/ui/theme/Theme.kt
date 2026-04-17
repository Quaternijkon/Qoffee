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
    onSecondary = Mocha,
    tertiary = Sage,
    background = Foam,
    onBackground = Onyx,
    surface = ColorTokens.SurfaceLight,
    onSurface = Onyx,
    surfaceVariant = ColorTokens.SurfaceVariantLight,
    onSurfaceVariant = Dust,
)

private val DarkColors = darkColorScheme(
    primary = Latte,
    onPrimary = Mocha,
    secondary = Copper,
    onSecondary = Onyx,
    tertiary = Sage,
    background = ColorTokens.BackgroundDark,
    onBackground = Foam,
    surface = ColorTokens.SurfaceDark,
    onSurface = Foam,
    surfaceVariant = ColorTokens.SurfaceVariantDark,
    onSurfaceVariant = Crema,
)

private object ColorTokens {
    val SurfaceLight = Crema
    val SurfaceVariantLight = Latte
    val BackgroundDark = Color(0xFF16110E)
    val SurfaceDark = Color(0xFF261B16)
    val SurfaceVariantDark = Color(0xFF3A2B24)
}

@Composable
fun QoffeeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = QoffeeTypography,
        content = content,
    )
}
