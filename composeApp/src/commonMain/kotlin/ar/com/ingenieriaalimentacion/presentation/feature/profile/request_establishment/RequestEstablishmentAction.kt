package ar.com.ingenieriaalimentacion.presentation.feature.profile.request_establishment

sealed interface RequestEstablishmentAction {
    data class QueryChanged(val value: String) : RequestEstablishmentAction
    data object LoadMore : RequestEstablishmentAction
    data class EstablishmentToggled(val id: String) : RequestEstablishmentAction
    data object SubmitClick : RequestEstablishmentAction
    data object RetryClick : RequestEstablishmentAction
    data object BackClick : RequestEstablishmentAction
}
