package ar.com.ingenieriaalimentacion.domain.model.subject

import kotlinx.serialization.Serializable

@Serializable
data class SubjectSummary(
    val id: String,
    val dni: Int,
    val name: String,
    val lastName: String,
    val sex: Sex,
)
