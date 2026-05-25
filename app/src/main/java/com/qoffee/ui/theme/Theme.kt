package com.qoffee.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.qoffee.core.model.AppThemeStyle

private val LightColors = lightColorScheme(
    primary = Marine,
    onPrimary = Porcelain,
    secondary = Pine,
    onSecondary = Porcelain,
    tertiary = CopperMuted,
    onTertiary = Porcelain,
    background = Paper,
    onBackground = Ink,
    primaryContainer = MarineSoft,
    onPrimaryContainer = Marine,
    secondaryContainer = PineSoft,
    onSecondaryContainer = Pine,
    tertiaryContainer = AmberSoft,
    onTertiaryContainer = Onyx,
    surface = Porcelain,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF2F5F6),
    onSurfaceVariant = Zinc,
    surfaceContainer = Color(0xFFF5F7F8),
    surfaceContainerLow = Porcelain,
    surfaceContainerHigh = Color(0xFFEAF1F2),
    outline = Color(0xFFD7DFE3),
    outlineVariant = Mist,
    error = Ember,
    errorContainer = BerrySoft,
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

private val MinimalLightColors = lightColorScheme(
    primary = GoogleBlueRefined,
    onPrimary = Color.White,
    secondary = GoogleGreenRefined,
    onSecondary = Color.White,
    tertiary = GoogleRedRefined,
    onTertiary = Color.White,
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF202124),
    primaryContainer = GoogleBlueSoft,
    onPrimaryContainer = Color(0xFF174EA6),
    secondaryContainer = GoogleGreenSoft,
    onSecondaryContainer = Color(0xFF0D652D),
    tertiaryContainer = GoogleRedSoft,
    onTertiaryContainer = Color(0xFFA50E0E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF202124),
    surfaceVariant = Color(0xFFEFF3F8),
    onSurfaceVariant = Color(0xFF5F6368),
    surfaceContainer = Color(0xFFF7F9FC),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEEF4FF),
    outline = Color(0xFFD6DDE6),
    outlineVariant = Color(0xFFE6EAF0),
    error = GoogleRedRefined,
    errorContainer = GoogleRedSoft,
)

private val MinimalDarkColors = darkColorScheme(
    primary = GoogleBlueDark,
    onPrimary = Color(0xFF062E6F),
    secondary = GoogleGreenDark,
    onSecondary = Color(0xFF0D3B1E),
    tertiary = GoogleRedDark,
    onTertiary = Color(0xFF601410),
    background = Color(0xFF101318),
    onBackground = Color(0xFFE8EAED),
    primaryContainer = Color(0xFF174EA6),
    onPrimaryContainer = Color(0xFFD2E3FC),
    secondaryContainer = Color(0xFF0D652D),
    onSecondaryContainer = Color(0xFFCEEAD6),
    tertiaryContainer = Color(0xFFA50E0E),
    onTertiaryContainer = Color(0xFFFAD2CF),
    surface = Color(0xFF171B22),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF252B34),
    onSurfaceVariant = Color(0xFFBDC1C6),
    surfaceContainer = Color(0xFF171B22),
    surfaceContainerLow = Color(0xFF11151B),
    surfaceContainerHigh = Color(0xFF202632),
    outline = Color(0xFF3C4043),
    outlineVariant = Color(0xFF303640),
    error = GoogleRedDark,
    errorContainer = Color(0xFF601410),
)

@Composable
fun QoffeeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeStyle: AppThemeStyle = AppThemeStyle.CLASSIC,
    content: @Composable () -> Unit,
) {
    ProvideQoffeeDashboardTokens(darkTheme = darkTheme, themeStyle = themeStyle) {
        MaterialTheme(
            colorScheme = when (themeStyle) {
                AppThemeStyle.CLASSIC -> if (darkTheme) DarkColors else LightColors
                AppThemeStyle.MINIMAL -> if (darkTheme) MinimalDarkColors else MinimalLightColors
            },
            typography = when (themeStyle) {
                AppThemeStyle.CLASSIC -> QoffeeTypography
                AppThemeStyle.MINIMAL -> QoffeeMinimalTypography
            },
            shapes = when (themeStyle) {
                AppThemeStyle.CLASSIC -> QoffeeShapes
                AppThemeStyle.MINIMAL -> QoffeeMinimalShapes
            },
        ) {
            ProvideTextStyle(
                value = MaterialTheme.typography.bodyLarge,
                content = content,
            )
        }
    }
}
