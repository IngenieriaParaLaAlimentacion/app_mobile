package ar.com.ingenieriaalimentacion.app

sealed interface AppPushDestination {
    val generation: String

    data class FoodStock(override val generation: String) : AppPushDestination
}
