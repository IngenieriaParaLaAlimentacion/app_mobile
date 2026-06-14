package ar.com.ingenieriaalimentacion.data.profile

import ar.com.ingenieriaalimentacion.data.profile.dto.UpdateProfileRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Even with the production `encodeDefaults = true` Json, `@EncodeDefault(NEVER)` must omit
 * fields still at their null default so the PATCH body only carries changed fields.
 */
class UpdateProfileRequestTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun `only name omits last_name key`() {
        assertEquals("""{"name":"X"}""", json.encodeToString(UpdateProfileRequest(name = "X")))
    }

    @Test
    fun `only last name omits name key`() {
        assertEquals("""{"last_name":"Y"}""", json.encodeToString(UpdateProfileRequest(lastName = "Y")))
    }

    @Test
    fun `both fields are serialized`() {
        assertEquals(
            """{"name":"X","last_name":"Y"}""",
            json.encodeToString(UpdateProfileRequest(name = "X", lastName = "Y")),
        )
    }

    @Test
    fun `no changes serialize to empty object`() {
        assertEquals("{}", json.encodeToString(UpdateProfileRequest()))
    }
}
