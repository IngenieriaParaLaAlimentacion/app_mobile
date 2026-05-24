package ar.com.ingenieriaalimentacion.data.subject.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SexDto {
    @SerialName("male") MALE,
    @SerialName("female") FEMALE,
}
