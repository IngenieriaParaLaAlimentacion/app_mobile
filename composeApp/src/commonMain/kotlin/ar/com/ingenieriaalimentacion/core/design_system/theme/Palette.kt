package ar.com.ingenieriaalimentacion.core.design_system.theme

import androidx.compose.ui.graphics.Color

/**
 * Raw brand palette shared with the web app. These are the only literal colors
 * in the codebase; everything else consumes them through [AlColors].
 *
 * Values marked "derived" are not in the web spec — the web app has surfaces
 * (a sidebar, table rows) that mobile does not, and mobile needs container
 * tints that the spec only defines for the alert row. They are tinted from the
 * paper/line pair so they stay in the same warm family.
 */
internal object AlPalette {
    /** Tierra oscura — sidebar on web, bottom nav here. */
    val EarthDark = Color(0xFF26362A)
    /** Tierra media — hover/active on top of [EarthDark]. */
    val EarthMid = Color(0xFF33452F)

    /** Papel cálido — content background. */
    val Paper = Color(0xFFF6F4EC)
    /** Derived: a half-step above [Paper] for cards, so they lift off the page. */
    val PaperRaised = Color(0xFFFDFBF4)
    /** Derived: a half-step below [Paper], between paper and [PaperLine]. */
    val PaperSunken = Color(0xFFEDEADD)
    /** Línea papel — separators and borders. */
    val PaperLine = Color(0xFFDED9C8)

    /** Tinta — body text. */
    val Ink = Color(0xFF26251F)
    /** Tinta suave — supporting text. */
    val InkSoft = Color(0xFF6B6759)

    /** Salvia — soft accents, avatars. */
    val Sage = Color(0xFF7A8F5C)
    /** Salvia profunda — icons, eyebrows, status text. */
    val SageDeep = Color(0xFF4E6339)
    /** Derived: pale sage container behind [SageDeep] text. */
    val SagePale = Color(0xFFE6EBDC)

    /** Mostaza — primary action and brand accent. */
    val Mustard = Color(0xFFC99A2E)
    /** Mostaza oscura — pressed state, and mustard-toned text on paper. */
    val MustardDark = Color(0xFF8A6614)
    /** Derived: pale mustard container, matching the brick/light-brick pairing. */
    val MustardPale = Color(0xFFF7EAD0)

    /** Ladrillo — critical alerts. */
    val Brick = Color(0xFFA9432E)
    /** Ladrillo claro — alert row background. */
    val BrickPale = Color(0xFFF1E1DA)
}
