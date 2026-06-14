package ar.com.ingenieriaalimentacion.core.design_system.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

private val lightColors = AlColors(
    surfaceBase = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFF4F4F5),
    textBase = Color(0xFF18181B),
    textMuted = Color(0xFF71717A),
    textInverted = Color(0xFFFFFFFF),
    accent = Color(0xFF15803D),
    accentContainer = Color(0xFFDCFCE7),
    warning = Color(0xFFB45309),
    warningContainer = Color(0xFFFEF3C7),
    danger = Color(0xFFB91C1C),
    dangerContainer = Color(0xFFFEE2E2),
    border = Color(0xFFE4E4E7),
)

@Composable
fun AlTheme(content: @Composable () -> Unit) {
    val geist = geistFontFamily()
    val outerTypography = MaterialTheme.typography
    val geistTypography = remember(geist, outerTypography) {
        Typography(
            displayLarge = outerTypography.displayLarge.copy(fontFamily = geist),
            displayMedium = outerTypography.displayMedium.copy(fontFamily = geist),
            displaySmall = outerTypography.displaySmall.copy(fontFamily = geist),
            headlineLarge = outerTypography.headlineLarge.copy(fontFamily = geist),
            headlineMedium = outerTypography.headlineMedium.copy(fontFamily = geist),
            headlineSmall = outerTypography.headlineSmall.copy(fontFamily = geist),
            titleLarge = outerTypography.titleLarge.copy(fontFamily = geist),
            titleMedium = outerTypography.titleMedium.copy(fontFamily = geist),
            titleSmall = outerTypography.titleSmall.copy(fontFamily = geist),
            bodyLarge = outerTypography.bodyLarge.copy(fontFamily = geist),
            bodyMedium = outerTypography.bodyMedium.copy(fontFamily = geist),
            bodySmall = outerTypography.bodySmall.copy(fontFamily = geist),
            labelLarge = outerTypography.labelLarge.copy(fontFamily = geist),
            labelMedium = outerTypography.labelMedium.copy(fontFamily = geist),
            labelSmall = outerTypography.labelSmall.copy(fontFamily = geist),
        )
    }
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = geistTypography,
        shapes = MaterialTheme.shapes,
    ) {
        CompositionLocalProvider(
            LocalAlColors provides lightColors,
            LocalAlTypography provides defaultAlTypography(),
            LocalAlSpacing provides AlSpacing(),
            LocalAlCorners provides AlCorners(),
            LocalAlBorders provides AlBorders(),
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = geist),
        ) {
            content()
        }
    }
}

object AlTheme {
    val colors @Composable @ReadOnlyComposable get() = LocalAlColors.current
    val typography @Composable @ReadOnlyComposable get() = LocalAlTypography.current
    val spacing @Composable @ReadOnlyComposable get() = LocalAlSpacing.current
    val corners @Composable @ReadOnlyComposable get() = LocalAlCorners.current
    val borders @Composable @ReadOnlyComposable get() = LocalAlBorders.current
}
