package ar.com.ingenieriaalimentacion.data.subject.mapper

import ar.com.ingenieriaalimentacion.data.db.PendingSubject
import ar.com.ingenieriaalimentacion.data.subject.dto.SubjectRequest
import ar.com.ingenieriaalimentacion.domain.model.subject.NewSubject
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import kotlinx.datetime.LocalDate

/** DB row → domain. */
internal fun PendingSubject.toDomain(): SubjectUpload = SubjectUpload(
    localId = localId,
    dni = dni.toInt(),
    sex = sex.toDomainSex(),
    name = name,
    lastName = lastName,
    birthDate = LocalDate.parse(birthDate),
    isRural = isRural == 1L,
    isIndigenous = isIndigenous == 1L,
    syncState = syncState.toSyncState(),
    canonicalId = canonicalId,
    errorMessage = errorMessage,
    attemptCount = attemptCount.toInt(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Form input → a fresh PENDING upload row with the client-generated [id]. */
internal fun NewSubject.toPendingUpload(id: String, now: Long): SubjectUpload = SubjectUpload(
    localId = id,
    dni = dni,
    sex = sex,
    name = name,
    lastName = lastName,
    birthDate = birthDate,
    isRural = isRural,
    isIndigenous = isIndigenous,
    syncState = SyncState.Pending,
    canonicalId = null,
    errorMessage = null,
    attemptCount = 0,
    createdAt = now,
    updatedAt = now,
)

/** Queued upload → POST body. The [SubjectUpload.localId] doubles as the idempotency `id`. */
internal fun SubjectUpload.toRequest(): SubjectRequest = SubjectRequest(
    id = localId,
    dni = dni,
    sex = sex.toDto(),
    name = name,
    lastName = lastName,
    birthDate = birthDate.toString(),
    isRural = isRural,
    isIndigenous = isIndigenous,
)

internal fun Sex.toDbString(): String = when (this) {
    Sex.Male -> "male"
    Sex.Female -> "female"
}

private fun String.toDomainSex(): Sex = when (this) {
    "male" -> Sex.Male
    "female" -> Sex.Female
    else -> error("Unknown sex stored: $this")
}

internal fun SyncState.toDbString(): String = when (this) {
    SyncState.Pending -> "PENDING"
    SyncState.Syncing -> "SYNCING"
    SyncState.Synced -> "SYNCED"
    SyncState.Merged -> "MERGED"
    SyncState.Failed -> "FAILED"
}

private fun String.toSyncState(): SyncState = when (this) {
    "PENDING" -> SyncState.Pending
    "SYNCING" -> SyncState.Syncing
    "SYNCED" -> SyncState.Synced
    "MERGED" -> SyncState.Merged
    "FAILED" -> SyncState.Failed
    else -> error("Unknown syncState stored: $this")
}
