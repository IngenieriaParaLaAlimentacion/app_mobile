package ar.com.ingenieriaalimentacion.domain.model.establishment

import kotlinx.serialization.Serializable

@Serializable
data class Establishment(
    val id: String,
    val name: String,
    val address: String,
)
