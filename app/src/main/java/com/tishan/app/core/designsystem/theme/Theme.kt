package com.tishan.app.core.designsystem.theme

import android.app.Activity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.tishan.app.data.settings.ThemeMode

private val MorandiLightColors = lightColorScheme(
    primary = Color(0xFF566B82),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E4F0),
    onPrimaryContainer = Color(0xFF17293B),
    secondary = Color(0xFF8B6267),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF4DDE0),
    onSecondaryContainer = Color(0xFF614248),
    tertiary = Color(0xFF60745E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDDEBD8),
    onTertiaryContainer = Color(0xFF1B2B1B),
    background = Color(0xFFFAF5ED),
    onBackground = Color(0xFF282620),
    surface = Color(0xFFFFFCF7),
    onSurface = Color(0xFF282620),
    surfaceVariant = Color(0xFFF0EBE2),
    onSurfaceVariant = Color(0xFF655F55),
    outline = Color(0xFF6E675E),
    outlineVariant = Color(0xFFDDD5C8),
    error = Color(0xFF984C4C),
    onError = Color.White,
    errorContainer = Color(0xFFF6DFDC),
    onErrorContainer = Color(0xFF873F3C),
)

private val MorandiDarkColors = darkColorScheme(
    primary = Color(0xFFC0D0E1),
    onPrimary = Color(0xFF273847),
    primaryContainer = Color(0xFF435669),
    onPrimaryContainer = Color(0xFFE1EBF4),
    secondary = Color(0xFFE0BEC2),
    onSecondary = Color(0xFF493237),
    secondaryContainer = Color(0xFF624B50),
    onSecondaryContainer = Color(0xFFF5E2E4),
    tertiary = Color(0xFFC2D2BD),
    onTertiary = Color(0xFF2D3B2C),
    tertiaryContainer = Color(0xFF485948),
    onTertiaryContainer = Color(0xFFE1EDDD),
    background = Color(0xFF252932),
    onBackground = Color(0xFFF0ECE5),
    surface = Color(0xFF2D323C),
    onSurface = Color(0xFFF0ECE5),
    surfaceVariant = Color(0xFF3A414D),
    onSurfaceVariant = Color(0xFFD8D5CE),
    outline = Color(0xFFC2BFB6),
    outlineVariant = Color(0xFF505764),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF653A39),
    onErrorContainer = Color(0xFFFFD9D4),
)

private val TishanTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 64.sp,
        lineHeight = 74.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)

private val TishanShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TishanTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (darkTheme) MorandiDarkColors else MorandiLightColors
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, (density.fontScale * fontScale.coerceIn(0.85f, 1.3f)).coerceAtMost(3f)),
        LocalTishanSemanticColors provides semanticColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TishanTypography,
            shapes = TishanShapes,
            content = content,
        )
    }
}
