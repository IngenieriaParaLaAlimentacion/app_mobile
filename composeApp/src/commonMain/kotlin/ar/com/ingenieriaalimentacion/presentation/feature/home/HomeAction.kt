package ar.com.ingenieriaalimentacion.presentation.feature.home

sealed interface HomeAction {
    data object NewMeasurementClick : HomeAction
    data object NewSubjectClick : HomeAction
    data object SyncClick : HomeAction          // "Sincronizar" / "Revisar"
    data object SeeAllActivityClick : HomeAction
    data object AlertsClick : HomeAction        // tap the "alertas hoy" stat
}
