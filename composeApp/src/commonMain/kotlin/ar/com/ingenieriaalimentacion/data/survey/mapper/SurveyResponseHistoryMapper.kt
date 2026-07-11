package ar.com.ingenieriaalimentacion.data.survey.mapper

import ar.com.ingenieriaalimentacion.data.survey.dto.SurveyResponseActivitySummaryResponse
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseUpload

internal fun SurveyResponseActivitySummaryResponse.toSummary(): SurveyResponseSummary = SurveyResponseSummary(
    id = id,
    surveyName = survey.name,
    establishmentName = establishment.name,
    eventAt = eventAt,
    syncState = SyncState.Synced,
    isLocal = false,
)

internal fun SurveyResponseUpload.toSummary(): SurveyResponseSummary = SurveyResponseSummary(
    id = canonicalId ?: localId,
    surveyName = surveyName,
    establishmentName = establishmentName,
    eventAt = eventAt,
    syncState = syncState,
    isLocal = true,
)
