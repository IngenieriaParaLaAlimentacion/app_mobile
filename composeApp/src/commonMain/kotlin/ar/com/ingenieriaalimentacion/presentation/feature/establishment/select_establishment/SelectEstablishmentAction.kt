package ar.com.ingenieriaalimentacion.presentation.feature.establishment.select_establishment

import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment

sealed interface SelectEstablishmentAction {
    data class QueryChanged(val value: String) : SelectEstablishmentAction
    data class EstablishmentToggled(val establishment: Establishment) : SelectEstablishmentAction
    data object ConfirmClick : SelectEstablishmentAction
    data object BackClick : SelectEstablishmentAction
    data object RetryClick : SelectEstablishmentAction
}
