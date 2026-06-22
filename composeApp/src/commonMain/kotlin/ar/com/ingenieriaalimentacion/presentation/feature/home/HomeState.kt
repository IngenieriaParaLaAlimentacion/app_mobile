package ar.com.ingenieriaalimentacion.presentation.feature.home

data class HomeState(
    val destination: HomeDestination? = null,
    val title: String = "Ingeniería en Alimentación",
    val subtitle: String = "Hola, mundo",
)
