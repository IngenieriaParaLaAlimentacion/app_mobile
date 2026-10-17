package ar.com.ingenieriaalimentacion.domain.model.measurement

import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary
import kotlin.time.Instant

/** Anthropometric values captured at a measurement instant. */
data class NewMeasurement(
    val subject: SubjectSummary,
    val establishmentId: String,
    val establishmentName: String,
    val weightKg: Double,
    val heightCm: Double,
    val measuredAt: Instant,
)
