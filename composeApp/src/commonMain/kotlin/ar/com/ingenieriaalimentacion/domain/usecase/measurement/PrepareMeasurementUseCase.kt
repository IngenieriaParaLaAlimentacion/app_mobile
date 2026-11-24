package ar.com.ingenieriaalimentacion.domain.usecase.measurement

import ar.com.ingenieriaalimentacion.domain.model.measurement.NewMeasurement
import ar.com.ingenieriaalimentacion.domain.model.measurement.PreparedMeasurement
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary
import ar.com.ingenieriaalimentacion.domain.model.measurement.StatureMethod
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
        observation: String? = null,
        measuredAt: Instant? = null,
        statureMethod: StatureMethod? = null,
    ): PreparedMeasurement {
        require(weightKg.isFinite() && weightKg > 0.5 && weightKg <= 300.0)
        require(heightCm.isFinite() && heightCm > 20.0 && heightCm <= 250.0)
        val normalizedObservation = observation?.trim()?.takeIf { it.isNotEmpty() }
        require(normalizedObservation == null || normalizedObservation.length <= 500)
        val current = now()
        val capturedAt = measuredAt ?: current
        if (capturedAt.toLocalDateTime(ARGENTINA_TIME_ZONE).date >
            current.toLocalDateTime(ARGENTINA_TIME_ZONE).date
        ) throw FutureMeasurementDateException()
        val measurement = NewMeasurement(
            subject, establishmentId, establishmentName, weightKg, heightCm, measuredAt = capturedAt,
            observation = normalizedObservation,
            statureMethod = statureMethod,
        )
        val birthDate = subject.birthDate
        val measuredDay = capturedAt.toLocalDateTime(ARGENTINA_TIME_ZONE).date
        if (birthDate == null || measuredDay < birthDate) {
            return PreparedMeasurement(measurement, requiresConfirmation = true, demographicsKnown = false)
        }
        val assessments = evaluator.evaluate(measurement, measuredDay)
        if (assessments != null) {
            val alerts = assessments.filter { it.alert != null }.map { it.indicator }
            return PreparedMeasurement(measurement, alerts.isNotEmpty(), demographicsKnown = true, reviewIndicators = alerts)
        }

        // The legacy preview shares the server's current UTC day until a SAP bundle is available.
        val requiresConfirmation = evaluator.requiresConfirmation(
            bmi = measurement.bmi(),
            sex = subject.sex,
            birthDate = birthDate,
            measuredDate = capturedAt.toLocalDateTime(TimeZone.UTC).date,
        )
        return PreparedMeasurement(measurement, requiresConfirmation, demographicsKnown = true)
    }

    companion object {
        val ARGENTINA_TIME_ZONE: TimeZone = TimeZone.of("America/Argentina/Buenos_Aires")
    }
}

class FutureMeasurementDateException : IllegalArgumentException("Measurement date cannot be in the future")
