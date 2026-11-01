package ar.com.ingenieriaalimentacion.data.survey.mapper

import ar.com.ingenieriaalimentacion.data.survey.dto.SurveyResponseActivityEstablishmentResponse
import ar.com.ingenieriaalimentacion.data.survey.dto.SurveyResponseActivitySubjectResponse
import ar.com.ingenieriaalimentacion.data.survey.dto.SurveyResponseActivitySummaryResponse
import ar.com.ingenieriaalimentacion.data.survey.dto.SurveyResponseActivitySurveyResponse
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseTarget
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseUpload
import ar.com.ingenieriaalimentacion.domain.model.survey.ResponseTargetRef
import ar.com.ingenieriaalimentacion.domain.model.survey.needsRequiredResponseNumber
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SurveyResponseHistoryMapperTest {

    private fun item(
        responseTarget: String? = null,
        establishment: SurveyResponseActivityEstablishmentResponse? = null,
        subject: SurveyResponseActivitySubjectResponse? = null,
        responseNumber: Int? = null,
    ) = SurveyResponseActivitySummaryResponse(
        id = "r1",
        eventAt = "2026-01-01T00:00:00Z",
        survey = SurveyResponseActivitySurveyResponse(id = "sv-1", name = "Encuesta FIES"),
        responseTarget = responseTarget,
        establishment = establishment,
        subject = subject,
        responseNumber = responseNumber,
    )

    private val establishment =
        SurveyResponseActivityEstablishmentResponse(id = "est-1", name = "Comedor Norte", city = "CABA")

    private val subject = SurveyResponseActivitySubjectResponse(
        id = "subj-1",
        dni = 12345678,
        sex = "female",
        name = "Ana",
        lastName = "García",
        birthDate = "1990-01-01",
    )

    @Test
    fun `an establishment item derives its target and label from the establishment ref`() {
        val summary = item(establishment = establishment).toSummary()

        assertEquals(SurveyResponseTarget.Establishment, summary.responseTarget)
        assertEquals("Comedor Norte", summary.targetLabel)
        assertEquals("sv-1", summary.surveyId)
    }

    @Test
    fun `a subject item derives its target and joins name and last name`() {
        val summary = item(subject = subject).toSummary()

        assertEquals(SurveyResponseTarget.Subject, summary.responseTarget)
        assertEquals("Ana García", summary.targetLabel)
    }

    @Test
    fun `an item with neither ref is anonymous with no label`() {
        val summary = item().toSummary()

        assertEquals(SurveyResponseTarget.Anonymous, summary.responseTarget)
        // The row renders `targetLabel ?: "Anónima"`, so a null here is the anonymous case, not
        // missing data.
        assertNull(summary.targetLabel)
    }

    @Test
    fun `an explicit response_target wins over the derivation`() {
        // Belt and braces for the day the backend starts sending the field.
        val summary = item(responseTarget = "anonymous", establishment = establishment).toSummary()

        assertEquals(SurveyResponseTarget.Anonymous, summary.responseTarget)
        assertNull(summary.targetLabel)
    }

    @Test
    fun `response number is retained in history summary`() {
        assertEquals(42, item(responseNumber = 42).toSummary().responseNumber)
    }

    @Test
    fun `local history retains number and narrows required-number recovery`() {
        val base = SurveyResponseUpload(
            localId = "local-1",
            surveyId = "sv-1",
            surveyName = "Encuesta",
            target = ResponseTargetRef.Anonymous,
            answersJson = "{}",
            eventAt = "2026-01-01T00:00:00Z",
            syncState = SyncState.Failed,
            canonicalId = null,
            errorMessage = "Falta el número de respuesta.",
            conflict = null,
            attemptCount = 1,
            createdAt = 1L,
            updatedAt = 2L,
            responseNumber = null,
        ).toSummary()

        assertTrue(base.needsRequiredResponseNumber)
        assertFalse(base.copy(errorMessage = "Otro error").needsRequiredResponseNumber)
        assertFalse(base.copy(responseNumber = 9).needsRequiredResponseNumber)
        assertEquals(9, base.copy(responseNumber = 9).responseNumber)
    }

    @Test
    fun `an unrecognized response_target falls back to the derivation`() {
        val summary = item(responseTarget = "household", subject = subject).toSummary()

        assertEquals(SurveyResponseTarget.Subject, summary.responseTarget)
        assertEquals("Ana García", summary.targetLabel)
    }
}
