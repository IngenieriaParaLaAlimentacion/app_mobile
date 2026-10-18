package ar.com.ingenieriaalimentacion.presentation.feature.profile.notify_establishment

import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationFormAction

sealed interface NotifyEstablishmentAction {
    data class NameChanged(val value: String) : NotifyEstablishmentAction
    data class NameFocusChanged(val focused: Boolean) : NotifyEstablishmentAction
    data class AddressChanged(val value: String) : NotifyEstablishmentAction
    data class AddressFocusChanged(val focused: Boolean) : NotifyEstablishmentAction
    data class CityChanged(val value: String) : NotifyEstablishmentAction
    data class CityFocusChanged(val focused: Boolean) : NotifyEstablishmentAction
    data class ProvinceChanged(val value: String) : NotifyEstablishmentAction
    data class ProvinceFocusChanged(val focused: Boolean) : NotifyEstablishmentAction
    data class Characterization(val action: CharacterizationFormAction) : NotifyEstablishmentAction

    /** Step 1 → 2, once name and address are valid. */
    data object NextClick : NotifyEstablishmentAction

    /** Step 2: send the request with the ficha. */
    data object SubmitClick : NotifyEstablishmentAction

    /** Step 2: send the request without a ficha ("Contestar luego"). */
    data object AnswerLaterClick : NotifyEstablishmentAction

    /** Top bar or system back: step 2 returns to step 1, step 1 leaves the screen. */
    data object BackClick : NotifyEstablishmentAction
}
