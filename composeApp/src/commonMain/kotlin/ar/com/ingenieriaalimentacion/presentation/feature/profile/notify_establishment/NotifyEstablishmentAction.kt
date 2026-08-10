package ar.com.ingenieriaalimentacion.presentation.feature.profile.notify_establishment

sealed interface NotifyEstablishmentAction {
    data class NameChanged(val value: String) : NotifyEstablishmentAction
    data class NameFocusChanged(val focused: Boolean) : NotifyEstablishmentAction
    data class AddressChanged(val value: String) : NotifyEstablishmentAction
    data class AddressFocusChanged(val focused: Boolean) : NotifyEstablishmentAction
    data class CityChanged(val value: String) : NotifyEstablishmentAction
    data class CityFocusChanged(val focused: Boolean) : NotifyEstablishmentAction
    data class ProvinceChanged(val value: String) : NotifyEstablishmentAction
    data class ProvinceFocusChanged(val focused: Boolean) : NotifyEstablishmentAction
    data object SubmitClick : NotifyEstablishmentAction
    data object BackClick : NotifyEstablishmentAction
}
