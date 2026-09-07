package ar.com.ingenieriaalimentacion.data.survey.mapper

import ar.com.ingenieriaalimentacion.data.survey.dto.PaginatedSurveysResponse
import ar.com.ingenieriaalimentacion.data.survey.dto.SurveyDetailResponse
import ar.com.ingenieriaalimentacion.data.survey.dto.SurveyListItemResponse
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class SurveyMapperTest {

    private fun listItem(
        id: String,
        responseTarget: String,
        requiresResponseNumber: Boolean? = null,
    ) = SurveyListItemResponse(
        id = id,
        name = "Survey $id",
        responseTarget = responseTarget,
        isActive = true,
        createdById = "user-1",
        questionCount = 3,
        createdAt = "2026-01-01T00:00:00Z",
        requiresResponseNumber = requiresResponseNumber,
    )

    @Test
    fun `each known response_target maps to its domain target`() {
        assertEquals(SurveyResponseTarget.Subject, listItem("s1", "subject").toDomainOrNull()!!.responseTarget)
        assertEquals(
            SurveyResponseTarget.Establishment,
            listItem("s2", "establishment").toDomainOrNull()!!.responseTarget,
        )
        assertEquals(SurveyResponseTarget.Anonymous, listItem("s3", "anonymous").toDomainOrNull()!!.responseTarget)
    }

    @Test
    fun `an unknown response_target yields null instead of throwing`() {
        assertNull(listItem("s4", "household").toDomainOrNull())
    }

    @Test
    fun `nullable response number policy maps without coercion`() {
        assertEquals(true, listItem("s1", "anonymous", true).toDomainOrNull()?.requiresResponseNumber)
        assertEquals(false, listItem("s2", "anonymous", false).toDomainOrNull()?.requiresResponseNumber)
        assertNull(listItem("s3", "anonymous", null).toDomainOrNull()?.requiresResponseNumber)
    }

    @Test
    fun `old cached survey detail JSON defaults response number policy to null`() {
        val decoded = Json.decodeFromString<ar.com.ingenieriaalimentacion.domain.model.survey.SurveyDetail>(
            """{"id":"s1","name":"Legacy","responseTarget":"Anonymous","isActive":true,"questions":[]}""",
        )

        assertNull(decoded.requiresResponseNumber)
    }

    @Test
    fun `a page drops rows with an unknown target instead of failing the whole page`() {
        val page = PaginatedSurveysResponse(
            items = listOf(
                listItem("s1", "subject"),
                listItem("s2", "household"),   // a target this build can't answer
                listItem("s3", "anonymous"),
            ),
            total = 3,
            page = 1,
            pageSize = 20,
            totalPages = 1,
        )

        assertEquals(listOf("s1", "s3"), page.toDomain().items.map { it.id })
    }

    @Test
    fun `an unknown response_target on the detail read is fatal`() {
        val detail = SurveyDetailResponse(
            id = "s1",
            name = "Survey",
            responseTarget = "household",
            isActive = true,
            questions = emptyList(),
        )

        assertFailsWith<IllegalStateException> { detail.toDomain() }
    }
}
