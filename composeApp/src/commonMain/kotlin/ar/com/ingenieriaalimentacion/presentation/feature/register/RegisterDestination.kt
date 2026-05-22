package ar.com.ingenieriaalimentacion.presentation.feature.register

sealed class RegisterDestination {
    data object Success : RegisterDestination()
}
