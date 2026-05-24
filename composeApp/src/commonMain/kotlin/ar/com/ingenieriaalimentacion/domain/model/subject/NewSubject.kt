package ar.com.ingenieriaalimentacion.domain.model.subject

import kotlinx.datetime.LocalDate

data class NewSubject(
    val dni: Int,
    val sex: Sex,
    val name: String,
    val lastName: String,
    val birthDate: LocalDate,
    val isRural: Boolean,
    val isIndigenous: Boolean,
)
