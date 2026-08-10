package ar.com.ingenieriaalimentacion.presentation.feature.profile.notify_establishment

sealed class NotifyEstablishmentDestination {
    data object Back : NotifyEstablishmentDestination()
    data object Submitted : NotifyEstablishmentDestination()
}
