package ar.com.ingenieriaalimentacion.core.design_system.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import com.komoui.themes.KomoStyles
import com.komoui.themes.LightStyles

/**
 * The brand palette expressed as KomoUI's style contract, so the KomoUI
 * primitives the design system wraps (Button, Input, Card, Checkbox, Dialog…)
 * render in the same colors as [AlColors] instead of KomoUI's neutral defaults.
 *
 * Pass it to `KomoTheme(komoLightColors = AlKomoStyles)`. Shadows are inherited
 * from [LightStyles]; they are drawn from `border`, which resolves back to this
 * object through KomoUI's composition local.
 */
internal object AlKomoStyles : KomoStyles by LightStyles {
    override val background = AlPalette.Paper
    override val foreground = AlPalette.Ink
    override val card = AlPalette.PaperRaised
    override val cardForeground = AlPalette.Ink
    override val popover = AlPalette.PaperRaised
    override val popoverForeground = AlPalette.Ink

    // Primary action = mostaza with ink on top; white on mostaza is only 2.6:1.
    override val primary = AlPalette.Mustard
    override val primaryForeground = AlPalette.Ink

    override val secondary = AlPalette.PaperSunken
    override val secondaryForeground = AlPalette.Ink
    override val muted = AlPalette.PaperSunken
    override val mutedForeground = AlPalette.InkSoft

    // KomoUI's `accent` pair is a hover surface, not the brand green.
    override val accent = AlPalette.SagePale
    override val accentForeground = AlPalette.SageDeep

    override val destructive = AlPalette.Brick
    override val destructiveForeground = AlPalette.Paper

    override val border = AlPalette.PaperLine
    override val input = AlPalette.PaperLine
    override val ring = AlPalette.SageDeep

    override val chart1 = AlPalette.Sage
    override val chart2 = AlPalette.SageDeep
    override val chart3 = AlPalette.Mustard
    override val chart4 = AlPalette.MustardDark
    override val chart5 = AlPalette.EarthDark

    override val sidebar = AlPalette.EarthDark
    override val sidebarForeground = AlPalette.Paper
    override val sidebarPrimary = AlPalette.Mustard
    override val sidebarPrimaryForeground = AlPalette.Ink
    override val sidebarAccent = AlPalette.EarthMid
    override val sidebarAccentForeground = AlPalette.Paper
    override val sidebarBorder = AlPalette.EarthMid
    override val sidebarRing = AlPalette.Sage

    override val snackbar = AlPalette.PaperRaised
}

/**
 * Material 3 fallback scheme, for the Material widgets that slip through
 * without an explicit color (progress indicators, ripples, text selection,
 * and the `surfaceContainer*` ramp that Material components such as
 * `ModalBottomSheet` default their container to).
 */
internal val AlMaterialColorScheme: ColorScheme = lightColorScheme(
    primary = AlPalette.Mustard,
    onPrimary = AlPalette.Ink,
    primaryContainer = AlPalette.MustardPale,
    onPrimaryContainer = AlPalette.MustardDark,
    secondary = AlPalette.SageDeep,
    onSecondary = AlPalette.Paper,
    secondaryContainer = AlPalette.SagePale,
    onSecondaryContainer = AlPalette.SageDeep,
    tertiary = AlPalette.EarthDark,
    onTertiary = AlPalette.Paper,
    background = AlPalette.Paper,
    onBackground = AlPalette.Ink,
    surface = AlPalette.Paper,
    onSurface = AlPalette.Ink,
    surfaceVariant = AlPalette.PaperSunken,
    onSurfaceVariant = AlPalette.InkSoft,
    // Material hands this ramp to sheets, menus and search bars. Left undefined it
    // falls back to M3's lavender-grey, which is how ModalBottomSheet escaped the palette.
    surfaceContainerLowest = AlPalette.PaperRaised,
    surfaceContainerLow = AlPalette.PaperRaised,
    surfaceContainer = AlPalette.Paper,
    surfaceContainerHigh = AlPalette.PaperSunken,
    surfaceContainerHighest = AlPalette.PaperSunken,
    surfaceBright = AlPalette.PaperRaised,
    surfaceDim = AlPalette.PaperSunken,
    surfaceTint = AlPalette.Mustard,
    error = AlPalette.Brick,
    onError = AlPalette.Paper,
    errorContainer = AlPalette.BrickPale,
    onErrorContainer = AlPalette.Brick,
    outline = AlPalette.PaperLine,
    outlineVariant = AlPalette.PaperLine,
)
