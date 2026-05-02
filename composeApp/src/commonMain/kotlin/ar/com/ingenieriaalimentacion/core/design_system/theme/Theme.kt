package ar.com.ingenieriaalimentacion.core.design_system.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

private val lightColors = AlColors(
    surfaceBase = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFF4F4F5),
    textBase = Color(0xFF18181B),
    textMuted = Color(0xFF71717A),
    textInverted = Color(0xFFFFFFFF),
    accent = Color(0xFF15803D),
    border = Color(0xFFE4E4E7),
)

@Composable
fun AlTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAlColors provides lightColors,
        LocalAlTypography provides defaultAlTypography(),
        LocalAlSpacing provides AlSpacing(),
        LocalAlCorners provides AlCorners(),
        LocalAlBorders provides AlBorders(),
    ) {
        content()
    }
}

object AlTheme {
    val colors @Composable @ReadOnlyComposable get() = LocalAlColors.current
    val typography @Composable @ReadOnlyComposable get() = LocalAlTypography.current
    val spacing @Composable @ReadOnlyComposable get() = LocalAlSpacing.current
    val corners @Composable @ReadOnlyComposable get() = LocalAlCorners.current
    val borders @Composable @ReadOnlyComposable get() = LocalAlBorders.current
}
