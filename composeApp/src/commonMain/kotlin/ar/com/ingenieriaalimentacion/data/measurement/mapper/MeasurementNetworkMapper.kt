package ar.com.ingenieriaalimentacion.data.measurement.mapper

import ar.com.ingenieriaalimentacion.data.measurement.dto.AlertDto
import ar.com.ingenieriaalimentacion.data.measurement.dto.AssessmentDto
import ar.com.ingenieriaalimentacion.data.measurement.dto.MeasurementRequest
import ar.com.ingenieriaalimentacion.domain.model.measurement.AssessmentAlert
import ar.com.ingenieriaalimentacion.domain.model.measurement.AssessmentStatus
import ar.com.ingenieriaalimentacion.domain.model.measurement.ClinicalAlert
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementAssessment
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementIndicator
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementUpload
import ar.com.ingenieriaalimentacion.domain.model.measurement.StatureMethod

/** Queued upload → POST body. The [MeasurementUpload.localId] doubles as the idempotency `id`. */
internal fun MeasurementUpload.toRequest(): MeasurementRequest = MeasurementRequest(
    id = localId,
    subjectId = subjectId,
    establishmentId = establishmentId,
    weightKg = weightKg,
    heightCm = heightCm,
    measuredAt = measuredAt,
    observation = observation,
    statureMethod = statureMethod?.wire,
)

/** Response alert DTO → domain. */
internal fun AlertDto.toDomain(): ClinicalAlert = ClinicalAlert(
    alertType = alertType,
    severity = severity,
    detail = detail,
)

internal fun AssessmentDto.toDomain(): MeasurementAssessment = MeasurementAssessment(
    indicator = MeasurementIndicator.entries.firstOrNull { it.wire == indicator } ?: MeasurementIndicator.Unknown,
    status = AssessmentStatus.entries.firstOrNull { it.wire == status } ?: AssessmentStatus.Unknown,
    classification = classification,
    zScore = zScore,
    referenceVersion = referenceVersion,
    alert = alert?.let { AssessmentAlert(it.severity, it.detail) },
)

internal fun String?.toStatureMethodOrNull(): StatureMethod? =
    StatureMethod.entries.firstOrNull { it.wire == this }
