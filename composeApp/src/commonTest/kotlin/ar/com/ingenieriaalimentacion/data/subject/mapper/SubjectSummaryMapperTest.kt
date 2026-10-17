package ar.com.ingenieriaalimentacion.data.subject.mapper

import ar.com.ingenieriaalimentacion.data.subject.dto.SubjectSummaryResponse
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubjectSummaryMapperTest {
    private val birth = LocalDate(2018, 2, 28)

    @Test
    fun keepsRemoteBirthDate() {
        val dto = Json.decodeFromString<SubjectSummaryResponse>("""
            {"id":"s","dni":42,"name":"Ana","last_name":"García",
             "sex":"female","birth_date":"2018-02-28"}
        """)
        assertEquals(birth, dto.toDomain().birthDate)
    }

    @Test
    fun keepsQueuedBirthDate() {
        val queued = SubjectUpload(
            localId = "s", dni = 42, sex = Sex.Female, name = "Ana", lastName = "García",
            birthDate = birth, isRural = false, isIndigenous = false,
            syncState = SyncState.Pending, canonicalId = null, errorMessage = null,
            attemptCount = 0, createdAt = 1, updatedAt = 1,
        )
        assertEquals(birth, queued.toSummary().birthDate)
    }

    @Test
    fun serializesNewAndRestoresOldNavigationSummaries() {
        val current = SubjectSummary("s", 42, "Ana", "García", Sex.Female, birth)
        assertEquals(current, Json.decodeFromString<SubjectSummary>(Json.encodeToString(current)))
        val old = """{"id":"s","dni":42,"name":"Ana","lastName":"García","sex":"Female"}"""
        assertNull(Json.decodeFromString<SubjectSummary>(old).birthDate)
    }
}
