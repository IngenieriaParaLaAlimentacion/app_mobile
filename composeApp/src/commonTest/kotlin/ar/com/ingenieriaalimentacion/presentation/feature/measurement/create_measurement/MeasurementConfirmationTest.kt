package ar.com.ingenieriaalimentacion.presentation.feature.measurement.create_measurement

import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.MeasurementAlertEvaluator
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.PrepareMeasurementUseCase
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class MeasurementConfirmationTest {
    private val prepare = PrepareMeasurementUseCase(MeasurementAlertEvaluator()) {
        Instant.parse("2026-10-17T00:30:00Z")
    }
    private val subject = SubjectSummary("s", 42, "Ana", "García", Sex.Female,
        LocalDate(1990, 1, 1))

    @Test
    fun retainsEnteredPrecisionAndExplicitUnits() {
        val prepared = prepare(subject, "e", "Escuela", 120.75, 160.25)
        val text = MeasurementConfirmation(1, prepared, "120.7500", "160.25").description
        assertContains(text, "Peso: 120,7500 kg")
        assertContains(text, "Talla: 160,25 cm")
        assertContains(text, "generarían una alerta")
    }

    @Test
    fun missingDateDoesNotClaimAComputedAlert() {
        val prepared = prepare(subject.copy(birthDate = null), "e", "Escuela", 72.5, 170.0)
        val text = MeasurementConfirmation(1, prepared, "72.5", "170").description
        assertContains(text, "Peso: 72,5 kg")
        assertContains(text, "Talla: 170 cm")
        assertFalse(text.contains("generarían una alerta"))
    }
}
