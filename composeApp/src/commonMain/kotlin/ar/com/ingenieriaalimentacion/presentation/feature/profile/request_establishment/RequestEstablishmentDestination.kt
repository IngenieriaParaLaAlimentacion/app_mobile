package ar.com.ingenieriaalimentacion.presentation.feature.profile.request_establishment

sealed class RequestEstablishmentDestination {
    data object Back : RequestEstablishmentDestination()
    data object Submitted : RequestEstablishmentDestination()
}
