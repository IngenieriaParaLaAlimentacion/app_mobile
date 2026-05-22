package ar.com.ingenieriaalimentacion.presentation.feature.establishment.select_establishment

sealed class SelectEstablishmentDestination {
    data object Back : SelectEstablishmentDestination()
    data class Confirm(val selectedJson: String) : SelectEstablishmentDestination()
}
