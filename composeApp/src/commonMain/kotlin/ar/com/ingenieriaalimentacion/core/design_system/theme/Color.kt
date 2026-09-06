package ar.com.ingenieriaalimentacion.core.design_system.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic color roles for the app, mirroring the web palette ("Raíz"): warm
 * paper surfaces, ink text, a dark-earth brand anchor, sage greens for status
 * and selection, and mustard as the primary action accent.
 *
 * Screens must never hardcode a hex — every color comes from here.
 */
data class AlColors(
    /** Warm paper — the background of every screen. */
    val surfaceBase: Color,
    /** One step above the paper: cards, sheets, popovers. */
    val surfaceRaised: Color,
    /** Subtle fill for neutral chips, segmented tracks and icon buttons. */
    val surfaceMuted: Color,
    /** Dark earth — the brand anchor. On web it is the sidebar; here, the bottom nav. */
    val brandSurface: Color,
    /** Mid earth — the hover/selected state on top of [brandSurface]. */
    val brandSurfaceRaised: Color,
    /** Ink — body text. */
    val textBase: Color,
    /** Soft ink — subtitles and supporting text. */
    val textMuted: Color,
    /** Paper, for text and icons sitting on [brandSurface]. */
    val textInverted: Color,
    /** Mustard — primary button and brand accent. */
    val primary: Color,
    /** Dark mustard — pressed state, and mustard-toned text on paper. */
    val primaryPressed: Color,
    /** Ink on mustard: white fails contrast on [primary], ink clears 6:1. */
    val onPrimary: Color,
    /** Deep sage — icons, eyebrows, success/status text and selected states. */
    val accent: Color,
    /** Sage — soft accents and avatars. */
    val accentSoft: Color,
    /** Pale sage — the container behind [accent] text. */
    val accentContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    /** Brick — critical alerts. */
    val danger: Color,
    /** Light brick — the background of an alert row. */
    val dangerContainer: Color,
    /** Paper line — separators, table and input borders. */
    val border: Color,
)

val LocalAlColors = compositionLocalOf<AlColors> {
    error("AlColors not provided. Wrap your content in AlTheme.")
}
