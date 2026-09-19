package ar.com.ingenieriaalimentacion.data.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StaleEstablishmentPushParserTest {
    @Test
    fun `parses the agreed payload`() {
        val push = StaleEstablishmentPushParser.parse(
            mapOf(
                "type" to "stale_establishment",
                "establishment_id" to "e-1",
                "stale_days" to "14",
            ),
        )
        assertEquals("e-1", push?.establishmentId)
        assertEquals(14, push?.staleDays)
    }

    @Test
    fun `rejects malformed and unknown payloads`() {
        assertNull(StaleEstablishmentPushParser.parse(mapOf("type" to "other")))
        assertNull(
            StaleEstablishmentPushParser.parse(
                mapOf("type" to "stale_establishment", "stale_days" to "7"),
            ),
        )
        assertNull(
            StaleEstablishmentPushParser.parse(
                mapOf(
                    "type" to "stale_establishment",
                    "establishment_id" to " ",
                    "stale_days" to "7",
                ),
            ),
        )
        assertNull(
            StaleEstablishmentPushParser.parse(
                mapOf(
                    "type" to "stale_establishment",
                    "establishment_id" to "e",
                    "stale_days" to "8",
                ),
            ),
        )
    }
}
