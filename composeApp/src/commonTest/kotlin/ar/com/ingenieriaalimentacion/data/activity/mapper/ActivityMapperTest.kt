package ar.com.ingenieriaalimentacion.data.activity.mapper

import ar.com.ingenieriaalimentacion.data.activity.dto.PaginatedActivityResponse
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.serialization.json.Json

class ActivityMapperTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val subjectItemJson = """
        {
            "type": "subject",
            "occurred_at": "2026-06-01T12:00:00Z",
            "subject": { "id": "subj-1", "dni": 42, "name": "Ana", "last_name": "García", "birth_date":"1990-01-01","sex": "female" }
        }
    """.trimIndent()

    private val measurementItemJson = """
        {
            "type": "measurement",
            "occurred_at": "2026-06-02T08:30:00Z",
            "measurement": {
                "id": "meas-1",
                "measured_at": "2026-06-02T08:30:00Z",
                "weight_kg": 70.5,
                "height_cm": 172.0,
                "bmi": 23.8,
                "subject": {
                    "id": "subj-2", "dni": 99, "sex": "male", "name": "Juan", "last_name": "Pérez",
                    "birth_date": "1990-01-01"
                },
                "establishment": { "id": "est-1", "name": "Escuela 1", "city": "Ciudad", "province": "Provincia" },
                "alerts": [ { "alert_type": "overweight", "severity": "high", "detail": "Sobrepeso" } ]
            }
        }
    """.trimIndent()

    private fun page(vararg items: String): PaginatedActivityResponse =
        json.decodeFromString(
            """{ "items": [${items.joinToString(",")}], "total": ${items.size}, "page": 1, "page_size": 20, "total_pages": 1 }""",
        )

    @Test
    fun `decodes a subject item into a SubjectEntry`() {
        val parsed = page(subjectItemJson).items.single().toParsedEntry()

        val subject = assertIs<ParsedActivityItem.SubjectEntry>(parsed).entry
        assertEquals("subj-1", subject.id)
        assertEquals(42, subject.dni)
        assertEquals(Sex.Female, subject.sex)
        assertEquals("Ana", subject.name)
        assertEquals("García", subject.lastName)
        assertEquals(SyncState.Synced, subject.syncState)
        assertEquals(false, subject.isLocal)
    }

    @Test
    fun `decodes a measurement item into a MeasurementEntry with alerts mapped via AlertDto`() {
        val parsed = page(measurementItemJson).items.single().toParsedEntry()

        val measurement = assertIs<ParsedActivityItem.MeasurementEntry>(parsed).entry
        assertEquals("meas-1", measurement.id)
        assertEquals("Juan", measurement.subjectName)
        assertEquals("Pérez", measurement.subjectLastName)
        assertEquals(99, measurement.subjectDni)
        assertEquals("Escuela 1", measurement.establishmentName)
        assertEquals(70.5, measurement.weightKg)
        assertEquals(172.0, measurement.heightCm)
        assertEquals(23.8, measurement.bmi)
        assertEquals(SyncState.Synced, measurement.syncState)
        assertEquals(false, measurement.isLocal)
        assertEquals(1, measurement.alerts.size)
        assertEquals("overweight", measurement.alerts.single().alertType)
        assertEquals("high", measurement.alerts.single().severity)
    }

    @Test
    fun `a subject-typed item with no subject payload throws`() {
        val malformed = """
            { "type": "subject", "occurred_at": "2026-06-01T12:00:00Z" }
        """.trimIndent()

        assertFailsWith<IllegalStateException> {
            page(malformed).items.single().toParsedEntry()
        }
    }

    @Test
    fun `a food item this build does not model is skipped, not thrown on`() {
        // The hardening guard: `/me/activity` gained food_intake/food_outflow, and an already
        // installed build must render the feed partially rather than crash on an unknown type.
        val foodIntake = """
            {
                "type": "food_intake",
                "occurred_at": "2026-06-01T12:00:00Z",
                "food_intake": { "id": "fi-1" }
            }
        """.trimIndent()

        assertNull(page(foodIntake).items.single().toParsedEntry())
    }

    @Test
    fun `an unknown type returns null rather than throwing`() {
        val unknown = """
            { "type": "unknown", "occurred_at": "2026-06-01T12:00:00Z" }
        """.trimIndent()

        assertNull(page(unknown).items.single().toParsedEntry())
    }
}
