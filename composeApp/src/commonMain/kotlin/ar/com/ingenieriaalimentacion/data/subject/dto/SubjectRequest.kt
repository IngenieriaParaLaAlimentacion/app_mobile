package ar.com.ingenieriaalimentacion.data.subject.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubjectRequest(
    val id: String,                              // client-generated UUID (idempotency key)
    val dni: Int,
    val sex: SexDto,
    val name: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("birth_date") val birthDate: String,
    @SerialName("is_rural") val isRural: Boolean,
    @SerialName("is_indigenous") val isIndigenous: Boolean,
)
