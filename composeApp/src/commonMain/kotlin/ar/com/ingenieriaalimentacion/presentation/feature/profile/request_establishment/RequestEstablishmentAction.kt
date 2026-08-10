package ar.com.ingenieriaalimentacion.presentation.feature.profile.request_establishment

sealed interface RequestEstablishmentAction {
    data class QueryChanged(val value: String) : RequestEstablishmentAction
    data object LoadMore : RequestEstablishmentAction
    data class EstablishmentToggled(val id: String) : RequestEstablishmentAction
    data object SubmitClick : RequestEstablishmentAction
    data object RetryClick : RequestEstablishmentAction
    data object BackClick : RequestEstablishmentAction
    data object NotifyAbsenceClick : RequestEstablishmentAction

    /** The notify-absence child popped back after a 201 — show the confirmation toast. */
    data object AbsenceNoticeSent : RequestEstablishmentAction
    data object SuccessToastDismissed : RequestEstablishmentAction
}
