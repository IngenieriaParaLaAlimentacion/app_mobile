package ar.com.ingenieriaalimentacion.data.survey.mapper

import ar.com.ingenieriaalimentacion.data.survey.dto.SurveyResponseActivitySummaryResponse
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseTarget
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseUpload
import ar.com.ingenieriaalimentacion.domain.model.survey.kind
import ar.com.ingenieriaalimentacion.domain.model.survey.label

/**
 * Prefer the server's own `response_target`; fall back to deriving it from which ref is populated.
 *
 * The derivation is safe by construction server-side: `ck_survey_responses_single_target` forbids
 * both refs being set, the service rejects a payload omitting a required entity, `response_target`
 * is locked once a survey has responses, and both FKs are `ON DELETE RESTRICT` — so a deleted
 * entity cannot silently null a ref out. What it does *not* cover is a future fourth target that
 * populates neither ref: that would render as "Anónima".
 */
private fun SurveyResponseActivitySummaryResponse.target(): SurveyResponseTarget =
    responseTarget?.toDomainResponseTargetOrNull()
        ?: when {
            subject != null -> SurveyResponseTarget.Subject
            establishment != null -> SurveyResponseTarget.Establishment
            else -> SurveyResponseTarget.Anonymous
        }

internal fun SurveyResponseActivitySummaryResponse.toSummary(): SurveyResponseSummary {
    val target = target()
    return SurveyResponseSummary(
        id = id,
        surveyId = survey.id,
        surveyName = survey.name,
        responseTarget = target,
        targetLabel = when (target) {
            SurveyResponseTarget.Establishment -> establishment?.name
            SurveyResponseTarget.Subject ->
                subject?.let { "${it.name.orEmpty()} ${it.lastName.orEmpty()}".trim() }?.takeIf { it.isNotBlank() }
            SurveyResponseTarget.Anonymous -> null
        },
        eventAt = eventAt,
        syncState = SyncState.Synced,
        isLocal = false,
        responseNumber = responseNumber,
    )
}

internal fun SurveyResponseUpload.toSummary(): SurveyResponseSummary = SurveyResponseSummary(
    id = canonicalId ?: localId,
    surveyId = surveyId,
    surveyName = surveyName,
    responseTarget = target.kind,
    targetLabel = target.label,
    eventAt = eventAt,
    syncState = syncState,
    isLocal = true,
    conflict = conflict,
    errorMessage = errorMessage,
    responseNumber = responseNumber,
)
