package ar.com.ingenieriaalimentacion.domain.usecase.measurement

import ar.com.ingenieriaalimentacion.data.measurement.dto.AssessmentDto
import ar.com.ingenieriaalimentacion.data.measurement.mapper.toDomain
import ar.com.ingenieriaalimentacion.domain.model.measurement.AssessmentStatus
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementIndicator
import ar.com.ingenieriaalimentacion.domain.model.measurement.NewMeasurement
import ar.com.ingenieriaalimentacion.domain.model.measurement.StatureMethod
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class SapAssessmentEvaluatorTest {
    private val reference = SapRuleSet(
        schemaVersion = 1,
        version = "synthetic-test",
        pediatricMaxMonth = 24,
        references = listOf(
            testReference(MeasurementIndicator.LengthForAge, 23, 80.0, "recumbent", 1.0),
            testReference(MeasurementIndicator.WeightForAge, 23, 10.0),
            testReference(MeasurementIndicator.BmiForAge, 24, 16.0),
            testReference(MeasurementIndicator.HeightForAge, 24, 90.0, "standing"),
        ),
    )
    private val evaluator = SapAssessmentEvaluator(reference)

    @Test
    fun separatesInfantIndicatorsAndFlagsBothWhenAppropriate() {
        val measured = Instant.parse("2026-06-12T02:59:00Z")
        val prepare = PrepareMeasurementUseCase(MeasurementAlertEvaluator(evaluator)) { measured }
        val result = prepare(
            subject = subject(LocalDate(2024, 6, 12)),
            establishmentId = "establishment",
            establishmentName = "Escuela",
            weightKg = 7.0,
            heightCm = 77.0,
            statureMethod = StatureMethod.Standing,
        )
        assertEquals(
            listOf(MeasurementIndicator.LengthForAge, MeasurementIndicator.WeightForAge),
            result.reviewIndicators,
        )
        assertEquals(StatureMethod.Standing, result.measurement.statureMethod)
    }

    @Test
    fun switchesIndicatorsAtArgentinaMidnight() {
        val after = Instant.parse("2026-06-12T03:00:00Z")
        val measurement = measurement(
            subject(LocalDate(2024, 6, 12)), after, weight = 10.0, height = 90.0,
        )
        val results = evaluator.evaluate(measurement, after.argentinaDay()).associateBy { it.indicator }
        assertEquals(AssessmentStatus.NotApplicable, results.getValue(MeasurementIndicator.WeightForAge).status)
        assertEquals(AssessmentStatus.Evaluated, results.getValue(MeasurementIndicator.BmiForAge).status)
        assertEquals(AssessmentStatus.Evaluated, results.getValue(MeasurementIndicator.HeightForAge).status)
    }

    @Test
    fun unknownHistoricalPostureDoesNotBlockWeight() {
        val before = Instant.parse("2026-06-12T02:59:00Z")
        val original = measurement(subject(LocalDate(2024, 6, 12)), before, weight = 8.0, height = 80.0)
        val results = evaluator.evaluate(original, before.argentinaDay()).associateBy { it.indicator }
        val length = results.getValue(MeasurementIndicator.LengthForAge)
        val weight = results.getValue(MeasurementIndicator.WeightForAge)
        assertEquals(AssessmentStatus.InsufficientData, length.status)
        assertNull(length.alert)
        assertEquals(AssessmentStatus.Evaluated, weight.status)
        assertEquals(-2.0, weight.zScore)
        assertNotNull(weight.alert)
        val aboveCutoff = evaluator.evaluate(original.copy(weightKg = 8.0001), before.argentinaDay())
            .first { it.indicator == MeasurementIndicator.WeightForAge }
        assertNull(aboveCutoff.alert)
    }

    @Test
    fun matchesEveryExpectedResultInBundledBackendExamples() {
        val rawRuleset = BundledSapReference.rulesetJson ?: return
        val ruleset = sapReferenceJson.decodeFromString<SapRuleSet>(rawRuleset)
        val cases = sapReferenceJson.decodeFromString<List<SapParityCase>>(checkNotNull(bundledSapParityCasesJson))
        val bundledEvaluator = SapAssessmentEvaluator(ruleset)
        for (example in cases) {
            val instant = Instant.parse(example.measuredAt)
            val posture = StatureMethod.entries.firstOrNull { it.wire == example.statureMethod }
            val input = measurement(
                subject(LocalDate.parse(example.birthDate), example.sex), instant, example.weightKg, example.heightCm,
            ).copy(statureMethod = posture)
            val actual = bundledEvaluator.evaluate(input, instant.argentinaDay()).associateBy { it.indicator }
            assertEquals(example.assessments.size, actual.size)
            for (expectedDto in example.assessments) {
                val expected = expectedDto.toDomain()
                val result = actual.getValue(expected.indicator)
                assertEquals(expected.status, result.status)
                assertEquals(expected.classification, result.classification)
                assertEquals(expected.referenceVersion, result.referenceVersion)
                assertEquals(expected.alert?.severity, result.alert?.severity)
                val z = expected.zScore
                if (z == null) assertNull(result.zScore) else assertEquals(z, assertNotNull(result.zScore), 1e-9)
            }
        }
    }

    private fun testReference(
        indicator: MeasurementIndicator,
        month: Int,
        median: Double,
        method: String? = null,
        offset: Double? = null,
    ) = SapReference(
        indicator = indicator.wire,
        referenceVersion = "synthetic-test",
        firstMonth = month,
        lastMonth = month,
        ageUnit = "months",
        formula = "standard",
        statureMethod = method,
        alternateMethodOffsetCm = offset,
        bands = listOf(
            SapBand(null, -2.0, false, true, "test_low", "medium"),
            SapBand(-2.0, null, false, false, "test_expected", null),
        ),
        rows = listOf(SapRow("female", month.toLong(), 0.0, median, 1.0)),
    )

    private fun subject(birthDate: LocalDate, sex: String = "female") = SubjectSummary(
        id = "subject", dni = 42, name = "Ana", lastName = "García",
        sex = if (sex == "female") Sex.Female else Sex.Male, birthDate = birthDate,
    )

    private fun measurement(
        subject: SubjectSummary,
        measured: Instant,
        weight: Double,
        height: Double,
    ) = NewMeasurement(subject, "establishment", "Escuela", weight, height, measured)

    private fun Instant.argentinaDay() = toLocalDateTime(PrepareMeasurementUseCase.ARGENTINA_TIME_ZONE).date
}

@Serializable
private data class SapParityCase(
    val sex: String,
    @SerialName("birth_date") val birthDate: String,
    @SerialName("measured_at") val measuredAt: String,
    @SerialName("weight_kg") val weightKg: Double,
    @SerialName("height_cm") val heightCm: Double,
    @SerialName("stature_method") val statureMethod: String?,
    val assessments: List<AssessmentDto>,
)
