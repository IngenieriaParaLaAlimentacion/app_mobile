package ar.com.ingenieriaalimentacion.core.design_system.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class AlBorders(
    val thin: Dp = 1.dp,
    val medium: Dp = 2.dp,
)

val LocalAlBorders = compositionLocalOf { AlBorders() }
