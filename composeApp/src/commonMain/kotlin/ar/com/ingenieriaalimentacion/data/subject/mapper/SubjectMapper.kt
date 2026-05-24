package ar.com.ingenieriaalimentacion.data.subject.mapper

import ar.com.ingenieriaalimentacion.data.subject.dto.SexDto
import ar.com.ingenieriaalimentacion.data.subject.dto.SubjectRequest
import ar.com.ingenieriaalimentacion.data.subject.dto.SubjectResponse
import ar.com.ingenieriaalimentacion.domain.model.subject.NewSubject
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.Subject
import kotlinx.datetime.LocalDate

internal fun NewSubject.toRequest(): SubjectRequest = SubjectRequest(
    dni = dni,
    sex = sex.toDto(),
    name = name,
    lastName = lastName,
    birthDate = birthDate.toString(),
    isRural = isRural,
    isIndigenous = isIndigenous,
)

internal fun SubjectResponse.toDomain(): Subject = Subject(
    id = id,
    dni = dni,
    sex = sex.toDomain(),
    name = name,
    lastName = lastName,
    birthDate = LocalDate.parse(birthDate),
    isRural = isRural,
    isIndigenous = isIndigenous,
)

private fun Sex.toDto(): SexDto = when (this) {
    Sex.Male -> SexDto.MALE
    Sex.Female -> SexDto.FEMALE
}

private fun SexDto.toDomain(): Sex = when (this) {
    SexDto.MALE -> Sex.Male
    SexDto.FEMALE -> Sex.Female
}
