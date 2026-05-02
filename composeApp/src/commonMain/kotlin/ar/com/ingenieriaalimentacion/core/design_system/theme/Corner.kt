package ar.com.ingenieriaalimentacion.core.design_system.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class AlCorners(
    val sm: Dp = 4.dp,
    val md: Dp = 8.dp,
    val lg: Dp = 16.dp,
)

val LocalAlCorners = compositionLocalOf { AlCorners() }
