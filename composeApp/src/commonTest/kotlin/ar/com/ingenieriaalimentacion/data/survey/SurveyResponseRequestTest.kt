package ar.com.ingenieriaalimentacion.data.survey

import ar.com.ingenieriaalimentacion.data.survey.dto.SurveyResponseEditRequest
import ar.com.ingenieriaalimentacion.data.survey.dto.SurveyResponseRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SurveyResponseRequestTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `create omits a null response number`() {
        val encoded = json.encodeToString(
            SurveyResponseRequest(
                id = "r1",
                answers = emptyMap(),
                eventAt = "2026-01-01T00:00:00Z",
            ),
        )

        assertFalse(encoded.contains("response_number"), encoded)
    }

    @Test
    fun `edit omits a null response number`() {
        val encoded = json.encodeToString(
            SurveyResponseEditRequest(
                id = "e1",
                answers = emptyMap(),
                eventAt = "2026-01-01T00:00:00Z",
            ),
        )

        assertFalse(encoded.contains("response_number"), encoded)
    }

    @Test
    fun `edit serializes a supplied response number`() {
        val encoded = json.encodeToString(
            SurveyResponseEditRequest(
                id = "e1",
                responseNumber = 42,
                answers = emptyMap(),
                eventAt = "2026-01-01T00:00:00Z",
            ),
        )

        assertTrue(encoded.contains("\"response_number\":42"), encoded)
    }
}
