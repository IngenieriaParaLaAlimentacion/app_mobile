package ar.com.ingenieriaalimentacion.data.profile.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Strict PATCH body. The global Json has `encodeDefaults = true`, which would serialize
 * `null` fields and trip the `extra="forbid"` server validation. `@EncodeDefault(NEVER)`
 * omits any field still at its `null` default, so only changed fields are sent.
 */
@Serializable
data class UpdateProfileRequest(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val name: String? = null,
    @SerialName("last_name") @EncodeDefault(EncodeDefault.Mode.NEVER) val lastName: String? = null,
)
