package ar.com.ingenieriaalimentacion.app

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController

val LocalOuterNavController = staticCompositionLocalOf<NavHostController> {
    error("LocalOuterNavController not provided. Wrap with CompositionLocalProvider in AppNavHost.")
}
