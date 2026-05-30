package ar.com.ingenieriaalimentacion.presentation.feature.register

import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment

sealed interface RegisterAction {
    data class EmailChanged(val value: String) : RegisterAction
    data class PasswordChanged(val value: String) : RegisterAction
    data class NameChanged(val value: String) : RegisterAction
    data class LastNameChanged(val value: String) : RegisterAction
    data object OpenEstablishmentSelector : RegisterAction
    data class EstablishmentsSelected(val establishments: List<Establishment>) : RegisterAction
    data object Submit : RegisterAction
    data object DismissError : RegisterAction
    data object Back : RegisterAction
}
