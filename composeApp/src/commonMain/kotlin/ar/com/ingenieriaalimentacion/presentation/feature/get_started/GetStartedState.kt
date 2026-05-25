package ar.com.ingenieriaalimentacion.presentation.feature.get_started

data class GetStartedState(
    val destination: GetStartedDestination? = null,
    val title: String = "Ingeniería en Alimentación",
    val subtitle: String = "Gestioná tu establecimiento desde tu teléfono.",
    val signUpLabel: String = "Crear cuenta",
    val logInLabel: String = "Iniciar sesión",
    val isLoginEnabled: Boolean = true,
)
