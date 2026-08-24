package ar.com.ingenieriaalimentacion.core.design_system.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import com.komoui.themes.KomoTheme

private val alLightColors = AlColors(
    surfaceBase = AlPalette.Paper,
    surfaceRaised = AlPalette.PaperRaised,
    surfaceMuted = AlPalette.PaperSunken,
    brandSurface = AlPalette.EarthDark,
    brandSurfaceRaised = AlPalette.EarthMid,
    textBase = AlPalette.Ink,
    textMuted = AlPalette.InkSoft,
    textInverted = AlPalette.Paper,
    primary = AlPalette.Mustard,
    primaryPressed = AlPalette.MustardDark,
    onPrimary = AlPalette.Ink,
    accent = AlPalette.SageDeep,
    accentSoft = AlPalette.Sage,
    accentContainer = AlPalette.SagePale,
    warning = AlPalette.MustardDark,
    warningContainer = AlPalette.MustardPale,
    danger = AlPalette.Brick,
    dangerContainer = AlPalette.BrickPale,
    border = AlPalette.PaperLine,
)

/**
 * Maps the brand families onto Material's type scale, so Material and KomoUI
 * widgets pick up the right face without every call site restating it:
 * display and headline roles are Fraunces, everything else is Work Sans.
 * Sizes stay at the Material defaults; only the family changes.
 */
private fun alMaterialTypography(families: AlFontFamilies): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = families.display),
        displayMedium = base.displayMedium.copy(fontFamily = families.display),
        displaySmall = base.displaySmall.copy(fontFamily = families.display),
        headlineLarge = base.headlineLarge.copy(fontFamily = families.display),
        headlineMedium = base.headlineMedium.copy(fontFamily = families.display),
        headlineSmall = base.headlineSmall.copy(fontFamily = families.display),
        titleLarge = base.titleLarge.copy(fontFamily = families.body),
        titleMedium = base.titleMedium.copy(fontFamily = families.body),
        titleSmall = base.titleSmall.copy(fontFamily = families.body),
        bodyLarge = base.bodyLarge.copy(fontFamily = families.body),
        bodyMedium = base.bodyMedium.copy(fontFamily = families.body),
        bodySmall = base.bodySmall.copy(fontFamily = families.body),
        labelLarge = base.labelLarge.copy(fontFamily = families.body),
        labelMedium = base.labelMedium.copy(fontFamily = families.body),
        labelSmall = base.labelSmall.copy(fontFamily = families.body),
    )
}

/**
 * The app's single theme entry point. It also configures KomoTheme, so the
 * KomoUI primitives underneath the `Al*` components share this palette and
 * type scale — wrap the app in [AlTheme] only, never in `KomoTheme` directly.
 *
 * The palette is light-only (warm paper), so the system dark mode is pinned
 * off rather than falling back to KomoUI's dark neutrals.
 */
@Composable
fun AlTheme(content: @Composable () -> Unit) {
    val families = alFontFamilies()
    val typography = remember(families) { alTypography(families) }
    val materialTypography = remember(families) { alMaterialTypography(families) }
    KomoTheme(
        isDarkTheme = false,
        komoLightColors = AlKomoStyles,
        materialLightColors = AlMaterialColorScheme,
        typography = materialTypography,
    ) {
        CompositionLocalProvider(
            LocalAlColors provides alLightColors,
            LocalAlTypography provides typography,
            LocalAlSpacing provides AlSpacing(),
            LocalAlCorners provides AlCorners(),
            LocalAlBorders provides AlBorders(),
            content = content,
        )
    }
}

object AlTheme {
    val colors @Composable @ReadOnlyComposable get() = LocalAlColors.current
    val typography @Composable @ReadOnlyComposable get() = LocalAlTypography.current
    val spacing @Composable @ReadOnlyComposable get() = LocalAlSpacing.current
    val corners @Composable @ReadOnlyComposable get() = LocalAlCorners.current
    val borders @Composable @ReadOnlyComposable get() = LocalAlBorders.current
}
