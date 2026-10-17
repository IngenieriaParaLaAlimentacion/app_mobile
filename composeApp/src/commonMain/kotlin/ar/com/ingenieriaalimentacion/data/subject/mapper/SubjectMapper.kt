package ar.com.ingenieriaalimentacion.data.subject.mapper

import ar.com.ingenieriaalimentacion.data.subject.dto.PaginatedSubjectsResponse
import ar.com.ingenieriaalimentacion.data.subject.dto.SexDto
import ar.com.ingenieriaalimentacion.data.subject.dto.SubjectResponse
import ar.com.ingenieriaalimentacion.data.subject.dto.SubjectSummaryResponse
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.Subject
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectPage
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectUpload
import kotlinx.datetime.LocalDate

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

internal fun Sex.toDto(): SexDto = when (this) {
    Sex.Male -> SexDto.MALE
    Sex.Female -> SexDto.FEMALE
}

internal fun SexDto.toDomain(): Sex = when (this) {
    SexDto.MALE -> Sex.Male
    SexDto.FEMALE -> Sex.Female
}

internal fun SubjectSummaryResponse.toDomain(): SubjectSummary = SubjectSummary(
    id = id,
    dni = dni,
    name = name,
    lastName = lastName,
    sex = sex.toDomain(),
    birthDate = LocalDate.parse(birthDate),
)

internal fun PaginatedSubjectsResponse.toDomain(): SubjectPage = SubjectPage(
    items = items.map { it.toDomain() },
    page = page,
    totalPages = totalPages,
)

internal fun SubjectUpload.toHistoryEntry(): SubjectHistoryEntry = SubjectHistoryEntry(
    id = localId,
    dni = dni,
    sex = sex,
    name = name,
    lastName = lastName,
    syncState = syncState,
    canonicalId = canonicalId,
    errorMessage = errorMessage,
    isLocal = true,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
