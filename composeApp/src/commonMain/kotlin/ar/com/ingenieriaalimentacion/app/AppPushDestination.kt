package ar.com.ingenieriaalimentacion.app

sealed interface AppPushDestination {
    data object FoodStock : AppPushDestination
}
