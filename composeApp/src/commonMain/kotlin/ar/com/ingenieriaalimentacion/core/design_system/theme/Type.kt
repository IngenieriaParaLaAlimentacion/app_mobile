package ar.com.ingenieriaalimentacion.core.design_system.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.fraunces_medium
import ingenieria_alimentacion.composeapp.generated.resources.fraunces_regular
import ingenieria_alimentacion.composeapp.generated.resources.fraunces_semibold
import ingenieria_alimentacion.composeapp.generated.resources.ibm_plex_mono_medium
import ingenieria_alimentacion.composeapp.generated.resources.work_sans_medium
import ingenieria_alimentacion.composeapp.generated.resources.work_sans_regular
import ingenieria_alimentacion.composeapp.generated.resources.work_sans_semibold
import org.jetbrains.compose.resources.Font

/**
 * The three type roles of the brand: Fraunces for display, Work Sans for body
 * and UI, IBM Plex Mono for data (indicator numbers, timestamps).
 */
data class AlFontFamilies(
    val display: FontFamily,
    val body: FontFamily,
    val data: FontFamily,
)

data class AlTypography(
    /** Fraunces — brand wordmark and hero headings. */
    val display: TextStyle,
    /** Fraunces — screen headings (h1). */
    val title: TextStyle,
    /** Work Sans — section headings inside a screen. */
    val subtitle: TextStyle,
    /** Work Sans — default body copy. */
    val body: TextStyle,
    /** Work Sans — emphasized body, e.g. a row's primary line. */
    val bodyStrong: TextStyle,
    /** Work Sans — buttons, menu entries and field labels. */
    val label: TextStyle,
    /** Work Sans — supporting text and pills. */
    val caption: TextStyle,
    /** IBM Plex Mono — headline indicator numbers (dashboard counters). */
    val dataLarge: TextStyle,
    /** IBM Plex Mono — inline measurement values. */
    val data: TextStyle,
    /** IBM Plex Mono — supporting readings and stamped dates. */
    val dataSmall: TextStyle,
)

@Composable
fun frauncesFontFamily(): FontFamily = FontFamily(
    Font(Res.font.fraunces_regular, weight = FontWeight.Normal),
    Font(Res.font.fraunces_medium, weight = FontWeight.Medium),
    Font(Res.font.fraunces_semibold, weight = FontWeight.SemiBold),
)

@Composable
fun workSansFontFamily(): FontFamily = FontFamily(
    Font(Res.font.work_sans_regular, weight = FontWeight.Normal),
    Font(Res.font.work_sans_medium, weight = FontWeight.Medium),
    Font(Res.font.work_sans_semibold, weight = FontWeight.SemiBold),
)

@Composable
fun ibmPlexMonoFontFamily(): FontFamily = FontFamily(
    Font(Res.font.ibm_plex_mono_medium, weight = FontWeight.Medium),
)

@Composable
fun alFontFamilies(): AlFontFamilies = AlFontFamilies(
    display = frauncesFontFamily(),
    body = workSansFontFamily(),
    data = ibmPlexMonoFontFamily(),
)

/**
 * Every role below sets a `lineHeight` larger than its `fontSize`, and Compose splits the surplus
 * evenly above and below *every* line — including the block's outer edges, which reads as a
 * top-heavy list row. [Trimmed] removes the half-leading from the first line's top and the last
 * line's bottom only; interline spacing is untouched.
 *
 * It does not fix everything: the gap between a line's layout top and its cap height comes from
 * the font's own ascent and is not adjustable from Compose. Call sites needing true optical
 * balance pair this with asymmetric vertical padding (see `AlListRow`).
 */
private val Trimmed = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

fun alTypography(families: AlFontFamilies): AlTypography = AlTypography(
    display = TextStyle(
        fontFamily = families.display,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeightStyle = Trimmed,
    ),
    title = TextStyle(
        fontFamily = families.display,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeightStyle = Trimmed,
    ),
    subtitle = TextStyle(
        fontFamily = families.body,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeightStyle = Trimmed,
    ),
    body = TextStyle(
        fontFamily = families.body,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
        lineHeightStyle = Trimmed,
    ),
    bodyStrong = TextStyle(
        fontFamily = families.body,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
        lineHeightStyle = Trimmed,
    ),
    label = TextStyle(
        fontFamily = families.body,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        lineHeightStyle = Trimmed,
    ),
    caption = TextStyle(
        fontFamily = families.body,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeightStyle = Trimmed,
    ),
    dataLarge = TextStyle(
        fontFamily = families.data,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Medium,
        lineHeightStyle = Trimmed,
    ),
    data = TextStyle(
        fontFamily = families.data,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
        lineHeightStyle = Trimmed,
    ),
    dataSmall = TextStyle(
        fontFamily = families.data,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        lineHeightStyle = Trimmed,
    ),
)

val LocalAlTypography = compositionLocalOf<AlTypography> {
    error("AlTypography not provided. Wrap your content in AlTheme.")
}
