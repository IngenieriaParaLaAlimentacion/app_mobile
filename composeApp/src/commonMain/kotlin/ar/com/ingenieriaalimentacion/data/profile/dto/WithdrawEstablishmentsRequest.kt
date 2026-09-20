package ar.com.ingenieriaalimentacion.data.profile.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WithdrawEstablishmentsRequest(
    @SerialName("establishment_ids") val establishmentIds: List<String>,
)
