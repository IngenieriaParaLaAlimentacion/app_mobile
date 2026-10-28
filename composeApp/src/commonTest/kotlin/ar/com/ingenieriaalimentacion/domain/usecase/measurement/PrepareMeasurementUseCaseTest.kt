package ar.com.ingenieriaalimentacion.domain.usecase.measurement

import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class PrepareMeasurementUseCaseTest {
    private val captured = Instant.parse("2026-10-17T00:30:00Z")
    private val prepare = PrepareMeasurementUseCase(MeasurementAlertEvaluator()) { captured }
    private val adult = SubjectSummary(
        id = "subject", dni = 42, name = "Ana", lastName = "García",
        sex = Sex.Female, birthDate = LocalDate(1990, 1, 1),
    )

    @Test
    fun capturesTheSameInstantForPreviewAndQueue() {
        val result = prepare(adult, "establishment", "Escuela", 120.0, 160.0)
        assertEquals(captured, result.measurement.measuredAt)
        assertEquals(120.0, result.measurement.weightKg)
        assertEquals(160.0, result.measurement.heightCm)
        assertTrue(result.requiresConfirmation)
        assertTrue(result.demographicsKnown)
    }

    @Test
    fun ordinaryAdultMeasurementDoesNotNeedConfirmation() {
        assertFalse(prepare(adult, "establishment", "Escuela", 72.5, 175.0).requiresConfirmation)
    }

    @Test
    fun oldNavigationSummaryRequiresConservativeConfirmation() {
        val result = prepare(adult.copy(birthDate = null), "establishment", "Escuela", 72.5, 170.0)
        assertTrue(result.requiresConfirmation)
        assertFalse(result.demographicsKnown)
    }

    @Test
    fun matchesExistingExclusiveLowerBounds() {
        assertFailsWith<IllegalArgumentException> {
            prepare(adult, "establishment", "Escuela", 0.5, 170.0)
        }
        assertFailsWith<IllegalArgumentException> {
            prepare(adult, "establishment", "Escuela", 72.5, 20.0)
        }
        assertFailsWith<IllegalArgumentException> {
            prepare(adult, "establishment", "Escuela", Double.NaN, 170.0)
        }
    }

    @Test
    fun evaluationUsesCapturedUtcDayRatherThanDeviceCalendar() {
        val evaluator = MeasurementAlertEvaluator()
        val boundary = adult.copy(birthDate = LocalDate(2007, 9, 17))
        val previousDate = captured.toLocalDateTime(TimeZone.of("America/Argentina/Buenos_Aires")).date
        assertEquals(LocalDate(2026, 10, 16), previousDate)
        assertEquals(228, evaluator.completedMonths(boundary.birthDate!!, previousDate))
        assertEquals(229, evaluator.completedMonths(boundary.birthDate!!, LocalDate(2026, 10, 17)))
        val result = prepare(boundary, "e", "Escuela", 96.0, 200.0)
        assertEquals(evaluator.requiresConfirmation(24.0, boundary.sex, boundary.birthDate!!,
            LocalDate(2026, 10, 17)), result.requiresConfirmation)
        assertEquals(captured, result.measurement.measuredAt)
    }

    @Test
    fun rejectsNonFiniteValuesAndUpperBoundsAndAcceptsInclusiveMaxima() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 300.01).forEach { weight ->
            assertFailsWith<IllegalArgumentException> { prepare(adult, "e", "Escuela", weight, 170.0) }
        }
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 250.01).forEach { height ->
            assertFailsWith<IllegalArgumentException> { prepare(adult, "e", "Escuela", 72.5, height) }
        }
        assertEquals(300.0, prepare(adult, "e", "Escuela", 300.0, 250.0).measurement.weightKg)
        val child = adult.copy(birthDate = LocalDate(2018, 1, 1))
        assertTrue(prepare(child, "e", "Escuela", 90.0, 140.0).requiresConfirmation)
    }
    @Test
    fun normalizesOptionalObservationAndEnforcesLimit() {
        assertEquals("Contexto\nsegunda línea", prepare(adult, "e", "Escuela", 70.0, 170.0,
            "  Contexto\nsegunda línea  ").measurement.observation)
        assertEquals(null, prepare(adult, "e", "Escuela", 70.0, 170.0, " \n ").measurement.observation)
        assertEquals(500, prepare(adult, "e", "Escuela", 70.0, 170.0, "a".repeat(500)).measurement.observation?.length)
        assertFailsWith<IllegalArgumentException> {
            prepare(adult, "e", "Escuela", 70.0, 170.0, "a".repeat(501))
        }
    }

    @Test
    fun acceptsExplicitPastInstant() {
        val selected = Instant.parse("2024-05-02T13:45:00Z")
        assertEquals(selected, prepare(adult, "e", "Escuela", 72.5, 175.0, measuredAt = selected).measurement.measuredAt)
    }

    @Test
    fun rejectsFutureArgentinaDateButAllowsLaterTimeToday() {
        assertFailsWith<FutureMeasurementDateException> {
            prepare(adult, "e", "Escuela", 72.5, 175.0, measuredAt = Instant.parse("2026-10-17T03:00:00Z"))
        }
        val laterToday = Instant.parse("2026-10-17T02:59:00Z")
        assertEquals(laterToday, prepare(adult, "e", "Escuela", 72.5, 175.0, measuredAt = laterToday).measurement.measuredAt)
    }

    @Test
    fun explicitSelectedInstantControlsAgeRatherThanCurrentClockOrArgentinaDay() {
        val current = Instant.parse("2026-10-18T12:00:00Z")
        val evaluator = MeasurementAlertEvaluator()
        val useCase = PrepareMeasurementUseCase(evaluator) { current }
        val boundary = adult.copy(birthDate = LocalDate(2007, 9, 17))
        val childDate = Instant.parse("2026-10-16T23:00:00Z")
        val child = useCase(boundary, "e", "Escuela", 104.0, 200.0, measuredAt = childDate)
        assertEquals(evaluator.requiresConfirmation(26.0, boundary.sex, boundary.birthDate!!, LocalDate(2026, 10, 16)), child.requiresConfirmation)
        assertFalse(child.requiresConfirmation)
        assertTrue(useCase(boundary, "e", "Escuela", 104.0, 200.0).requiresConfirmation)
        val utcNextDay = useCase(boundary, "e", "Escuela", 104.0, 200.0,
            measuredAt = Instant.parse("2026-10-17T01:30:00Z"))
        assertTrue(utcNextDay.requiresConfirmation)
    }

    @Test
    fun readsClockOnceForDefaultAndExplicitMeasurement() {
        var clockReads = 0
        val useCase = PrepareMeasurementUseCase(MeasurementAlertEvaluator()) { clockReads++; captured }
        assertEquals(captured, useCase(adult, "e", "Escuela", 70.0, 170.0).measurement.measuredAt)
        assertEquals(1, clockReads)
        val selected = Instant.parse("2024-05-02T13:45:00Z")
        assertEquals(selected, useCase(adult, "e", "Escuela", 70.0, 170.0, measuredAt = selected).measurement.measuredAt)
        assertEquals(2, clockReads)
    }

}
