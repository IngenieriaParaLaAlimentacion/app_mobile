package ar.com.ingenieriaalimentacion.data.measurement.mapper

import ar.com.ingenieriaalimentacion.data.measurement.dto.AlertDto
import ar.com.ingenieriaalimentacion.data.measurement.dto.MeasurementRequest
import ar.com.ingenieriaalimentacion.domain.model.measurement.ClinicalAlert
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementUpload

/** Queued upload → POST body. The [MeasurementUpload.localId] doubles as the idempotency `id`. */
internal fun MeasurementUpload.toRequest(): MeasurementRequest = MeasurementRequest(
    id = localId,
    subjectId = subjectId,
    establishmentId = establishmentId,
    weightKg = weightKg,
    heightCm = heightCm,
    measuredAt = measuredAt,
    observation = observation,
)

/** Response alert DTO → domain. */
internal fun AlertDto.toDomain(): ClinicalAlert = ClinicalAlert(
    alertType = alertType,
    severity = severity,
    detail = detail,
)
