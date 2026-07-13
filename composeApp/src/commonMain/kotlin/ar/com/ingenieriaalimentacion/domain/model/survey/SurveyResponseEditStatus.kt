package ar.com.ingenieriaalimentacion.domain.model.survey

import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState

/** The most recent edit attempt for a response — lets the detail screen surface a dead-lettered
 *  (FAILED) edit instead of it silently vanishing after the 7-day prune window. */
data class SurveyResponseEditStatus(
    val editId: String,
    val syncState: SyncState,
    val errorMessage: String?,
)
