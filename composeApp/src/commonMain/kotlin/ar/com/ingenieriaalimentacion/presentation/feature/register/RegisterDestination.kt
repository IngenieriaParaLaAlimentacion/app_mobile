package ar.com.ingenieriaalimentacion.presentation.feature.register

sealed class RegisterDestination {
    data object Success : RegisterDestination()
    data class SelectEstablishment(val preselectedJson: String) : RegisterDestination()
    data object Back : RegisterDestination()
}
