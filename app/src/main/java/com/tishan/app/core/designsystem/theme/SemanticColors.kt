package com.tishan.app.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class TishanSemanticPalette(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val infoContainer: Color,
)

internal val LightSemanticColors = TishanSemanticPalette(
    success = Color(0xFF5C7A55),
    onSuccess = Color.White,
    successContainer = Color(0xFFE1EDDD),
    onSuccessContainer = Color(0xFF36572E),
    warning = Color(0xFF8B681F),
    onWarning = Color.White,
    warningContainer = Color(0xFFF4E7C9),
    onWarningContainer = Color(0xFF72541A),
    info = Color(0xFF566B82),
    infoContainer = Color(0xFFD9E4F0),
)

internal val DarkSemanticColors = TishanSemanticPalette(
    success = Color(0xFFA6C3A1),
    onSuccess = Color(0xFF173313),
    successContainer = Color(0xFF3A4E37),
    onSuccessContainer = Color(0xFFE1EDDD),
    warning = Color(0xFFE0BE7A),
    onWarning = Color(0xFF3D2F08),
    warningContainer = Color(0xFF4A3E1F),
    onWarningContainer = Color(0xFFF4E7C9),
    info = Color(0xFFC0D0E1),
    infoContainer = Color(0xFF435669),
)

internal val LocalTishanSemanticColors = staticCompositionLocalOf { LightSemanticColors }

object TishanSemanticColors {
    val current: TishanSemanticPalette
        @Composable get() = LocalTishanSemanticColors.current
}
