package ar.com.ingenieriaalimentacion.domain.usecase.measurement

import ar.com.ingenieriaalimentacion.domain.model.measurement.NewMeasurement
import ar.com.ingenieriaalimentacion.domain.model.measurement.PreparedMeasurement
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.pow
import kotlin.time.Instant

class PrepareMeasurementUseCase(
    private val evaluator: MeasurementAlertEvaluator,
    private val now: () -> Instant,
) {
    operator fun invoke(
        subject: SubjectSummary,
        establishmentId: String,
        establishmentName: String,
        weightKg: Double,
        heightCm: Double,
    ): PreparedMeasurement {
        require(weightKg.isFinite() && weightKg > 0.5 && weightKg <= 300.0)
        require(heightCm.isFinite() && heightCm > 20.0 && heightCm <= 250.0)
        val capturedAt = now()
        val measurement = NewMeasurement(
            subject, establishmentId, establishmentName, weightKg, heightCm, measuredAt = capturedAt,
        )
        val birthDate = subject.birthDate
        val needsConfirmation = birthDate == null || evaluator.requiresConfirmation(
            bmi = weightKg / (heightCm / 100.0).pow(2),
            sex = subject.sex,
            birthDate = birthDate,
            measuredDate = capturedAt.toLocalDateTime(TimeZone.UTC).date,
        )
        return PreparedMeasurement(measurement, needsConfirmation, birthDate != null)
    }
}
