package ar.com.ingenieriaalimentacion.presentation.feature.home

import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus

sealed interface HomeAction {
    data object NewMeasurementClick : HomeAction
    data object NewSubjectClick : HomeAction
    data object SyncClick : HomeAction          // "Sincronizar" / "Revisar"
    data object SeeAllActivityClick : HomeAction
    data object EstablishmentPickerOpen : HomeAction
    data object EstablishmentPickerDismiss : HomeAction
    data class EstablishmentSelected(val establishment: EstablishmentWithStatus) : HomeAction
    data object RespondSurveyClick : HomeAction
}
