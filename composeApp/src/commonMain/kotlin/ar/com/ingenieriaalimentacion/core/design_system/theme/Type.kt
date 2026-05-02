package ar.com.ingenieriaalimentacion.core.design_system.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class AlTypography(
    val title: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
)

@Composable
fun defaultAlTypography() = AlTypography(
    title = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    body = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
)

val LocalAlTypography = compositionLocalOf<AlTypography> {
    error("AlTypography not provided. Wrap your content in AlTheme.")
}
