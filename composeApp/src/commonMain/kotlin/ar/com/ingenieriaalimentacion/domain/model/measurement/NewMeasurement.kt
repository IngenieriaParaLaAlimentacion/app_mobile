package ar.com.ingenieriaalimentacion.domain.model.measurement

import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary

/**
 * Form output for a new measurement. The [subject] carries the server id plus display fields. The
 * `measuredAt`, `localId`, and timestamps are stamped in the repository at enqueue (not by the VM),
 * mirroring how subjects stamp `id`/`createdAt`.
 */
data class NewMeasurement(
    val subject: SubjectSummary,
    val establishmentId: String,
    val establishmentName: String,
    val weightKg: Double,
    val heightCm: Double,
)
