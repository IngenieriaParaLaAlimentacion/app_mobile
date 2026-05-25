package ar.com.ingenieriaalimentacion.core.design_system.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.geist_bold
import ingenieria_alimentacion.composeapp.generated.resources.geist_medium
import ingenieria_alimentacion.composeapp.generated.resources.geist_regular
import ingenieria_alimentacion.composeapp.generated.resources.geist_semibold
import org.jetbrains.compose.resources.Font

data class AlTypography(
    val title: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
)

@Composable
fun geistFontFamily(): FontFamily = FontFamily(
    Font(Res.font.geist_regular, weight = FontWeight.Normal),
    Font(Res.font.geist_medium, weight = FontWeight.Medium),
    Font(Res.font.geist_semibold, weight = FontWeight.SemiBold),
    Font(Res.font.geist_bold, weight = FontWeight.Bold),
)

@Composable
fun defaultAlTypography(): AlTypography {
    val geist = geistFontFamily()
    return AlTypography(
        title = TextStyle(fontFamily = geist, fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
        body = TextStyle(fontFamily = geist, fontSize = 16.sp, fontWeight = FontWeight.Normal),
        caption = TextStyle(fontFamily = geist, fontSize = 12.sp, fontWeight = FontWeight.Normal),
    )
}

val LocalAlTypography = compositionLocalOf<AlTypography> {
    error("AlTypography not provided. Wrap your content in AlTheme.")
}
