package ar.com.ingenieriaalimentacion.core.design_system.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

data class AlColors(
    val surfaceBase: Color,
    val surfaceMuted: Color,
    val textBase: Color,
    val textMuted: Color,
    val textInverted: Color,
    val accent: Color,
    val accentContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val border: Color,
)

val LocalAlColors = compositionLocalOf<AlColors> {
    error("AlColors not provided. Wrap your content in AlTheme.")
}
