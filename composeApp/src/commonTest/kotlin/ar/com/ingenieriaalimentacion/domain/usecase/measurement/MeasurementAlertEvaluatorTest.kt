package ar.com.ingenieriaalimentacion.domain.usecase.measurement

import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MeasurementAlertEvaluatorTest {
    private val evaluator = MeasurementAlertEvaluator()

    @Test
    fun matchesBackendCases() {
        anthropometryParityCases.forEachIndexed { index, case ->
            assertEquals(
                case.requiresConfirmation,
                evaluator.requiresConfirmation(
                    case.bmi, case.sex,
                    LocalDate.parse(case.birthDate), LocalDate.parse(case.measuredDate),
                ),
                "Backend fixture $index: $case",
            )
        }
    }

    @Test
    fun coversExactlyTheBackendAgeRange() {
        Sex.entries.forEach { sex ->
            (0..228).forEach { month -> assertNotNull(WhoBmiLms.get(sex, month)) }
            assertNull(WhoBmiLms.get(sex, -1))
            assertNull(WhoBmiLms.get(sex, 229))
        }
    }

    @Test
    fun completedMonthsUsesDayOfMonthWithoutCalendarClamping() {
        val birth = LocalDate(2007, 2, 28)
        assertEquals(228, evaluator.completedMonths(birth, LocalDate(2026, 3, 27)))
        assertEquals(229, evaluator.completedMonths(birth, LocalDate(2026, 3, 28)))
        assertEquals(0, evaluator.completedMonths(birth, LocalDate(2007, 2, 27)))
        assertEquals(11, evaluator.completedMonths(LocalDate(2024, 2, 29), LocalDate(2025, 2, 28)))
    }

    @Test
    fun adultBoundsMatchBackend() {
        val birth = LocalDate(1990, 1, 1)
        val measured = LocalDate(2026, 10, 17)
        listOf(
            15.0 to true, 16.0 to true, 18.499999 to true,
            18.5 to false, 24.999999 to false, 25.0 to true, 30.0 to true,
        ).forEach { (bmi, expected) ->
            assertEquals(expected, evaluator.requiresConfirmation(bmi, Sex.Female, birth, measured))
        }
    }
    @Test
    fun childThresholdsAreStrict() {
        listOf(-3.0 to true, -2.000001 to true, -2.0 to false,
            2.0 to false, 2.000001 to true, 3.0 to true).forEach { (z, expected) ->
            assertEquals(expected, requiresConfirmationForZ(z))
        }
    }

    @Test
    fun logarithmicAndPowerBranchesHaveIndependentExpectations() {
        assertEquals(1.0, zScore(17.6, WhoBmiLms.Lms(1.0, 16.0, 0.1)), 1e-12)
        assertEquals(0.0, zScore(16.0, WhoBmiLms.Lms(0.0, 16.0, 0.1)), 1e-12)
        assertEquals(0.0, zScore(16.0, WhoBmiLms.Lms(1e-12, 16.0, 0.1)), 1e-12)
    }
}
