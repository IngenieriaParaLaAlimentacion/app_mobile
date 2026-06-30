package ar.com.ingenieriaalimentacion.presentation.feature.measurement.uploads

import ar.com.ingenieriaalimentacion.core.design_system.components.pill.AlPillTone
import ar.com.ingenieriaalimentacion.domain.model.measurement.ClinicalAlert
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeasurementUploadUiTest {

    private fun upload(
        syncState: SyncState,
        errorMessage: String? = null,
        bmi: Double? = null,
        alerts: List<ClinicalAlert> = emptyList(),
        weightKg: Double = 70.0,
        heightCm: Double = 172.0,
        measuredAt: String = "2026-06-21T18:30:00Z",
    ) = MeasurementUpload(
        localId = "m1",
        subjectId = "s1",
        subjectName = "Ana",
        subjectLastName = "García",
        subjectDni = 42,
        establishmentId = "e1",
        establishmentName = "Escuela 1",
        weightKg = weightKg,
        heightCm = heightCm,
        measuredAt = measuredAt,
        syncState = syncState,
        errorMessage = errorMessage,
        attemptCount = 0,
        bmi = bmi,
        alerts = alerts,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `pending maps to warning pill with no actions but delete`() {
        val ui = upload(SyncState.Pending).toUi()
        assertEquals("Pendiente", ui.badgeLabel)
        assertEquals(AlPillTone.Warning, ui.badgeTone)
        assertFalse(ui.isSynced)
        assertFalse(ui.showSpinner)
        assertFalse(ui.canRetry)
        assertTrue(ui.canDelete)
    }

    @Test
    fun `syncing maps to neutral pill with spinner and no actions`() {
        val ui = upload(SyncState.Syncing).toUi()
        assertEquals("Sincronizando", ui.badgeLabel)
        assertEquals(AlPillTone.Neutral, ui.badgeTone)
        assertFalse(ui.isSynced)
        assertTrue(ui.showSpinner)
        assertFalse(ui.canRetry)
        assertFalse(ui.canDelete)
    }

    @Test
    fun `synced maps to success pill, allows delete, no retry`() {
        val ui = upload(SyncState.Synced, bmi = 23.7).toUi()
        assertEquals("Sincronizado", ui.badgeLabel)
        assertEquals(AlPillTone.Success, ui.badgeTone)
        assertTrue(ui.isSynced)
        assertFalse(ui.canRetry)
        assertTrue(ui.canDelete)
    }

    @Test
    fun `failed maps to danger pill, allows retry and delete`() {
        val ui = upload(SyncState.Failed, errorMessage = "422 Unprocessable").toUi()
        assertEquals("Error", ui.badgeLabel)
        assertEquals(AlPillTone.Danger, ui.badgeTone)
        assertTrue(ui.canRetry)
        assertTrue(ui.canDelete)
    }

    @Test
    fun `synced row surfaces only the primary alert as the category, localized and toned`() {
        val ui = upload(
            SyncState.Synced,
            bmi = 23.7,
            alerts = listOf(
                ClinicalAlert(alertType = "overweight", severity = "high", detail = "Sobrepeso"),
                ClinicalAlert(alertType = "underweight", severity = "medium", detail = "Vigilar"),
                ClinicalAlert(alertType = "obese", severity = "critical", detail = "Derivar"),
            ),
        ).toUi()
        assertEquals("23,7", ui.bmiValue)
        // Only the first alert is the clinical anchor; the rest no longer surface on the card.
        assertEquals("Sobrepeso", ui.bmiCategory)
        assertEquals(AlPillTone.Danger, ui.bmiCategoryTone)   // high
    }

    @Test
    fun `unknown alert type and severity fall back to a readable category and neutral tone`() {
        val ui = upload(
            SyncState.Synced,
            bmi = 23.7,
            alerts = listOf(ClinicalAlert(alertType = "future_code", severity = "weird", detail = "x")),
        ).toUi()
        assertEquals("Future code", ui.bmiCategory)
        assertEquals(AlPillTone.Neutral, ui.bmiCategoryTone)
    }

    @Test
    fun `synced row with no alert derives a Normal category with a success tone`() {
        val ui = upload(SyncState.Synced, bmi = 23.7).toUi()
        assertEquals("Normal", ui.bmiCategory)
        assertEquals(AlPillTone.Success, ui.bmiCategoryTone)
    }

    @Test
    fun `synced row with an alert uses the primary alert as category with its severity tone`() {
        val ui = upload(
            SyncState.Synced,
            bmi = 28.0,
            alerts = listOf(ClinicalAlert(alertType = "overweight", severity = "high", detail = "Sobrepeso")),
        ).toUi()
        assertEquals("Sobrepeso", ui.bmiCategory)
        assertEquals(AlPillTone.Danger, ui.bmiCategoryTone)
    }

    @Test
    fun `non-synced row never shows bmi or category even when present`() {
        val ui = upload(
            SyncState.Pending,
            bmi = 23.7,
            alerts = listOf(ClinicalAlert("overweight", "high", "Sobrepeso")),
        ).toUi()
        assertNull(ui.bmiValue)
        assertNull(ui.bmiCategory)
        assertNull(ui.bmiCategoryTone)
    }

    @Test
    fun `identity, measurements and date are split into discrete fields`() {
        val ui = upload(SyncState.Pending).toUi()
        assertEquals("DNI 42", ui.identity)
        assertEquals("70 kg · 172 cm", ui.measurements)
        // 18:30 UTC; the date/time portion depends on TZ but the dd/MM HH:mm shape is present.
        assertTrue(Regex("""\d{2}/\d{2} \d{2}:\d{2}""").matches(ui.measuredAtLabel))
    }

    @Test
    fun `error message is populated only on failed rows`() {
        val failed = upload(SyncState.Failed, errorMessage = "boom").toUi()
        assertEquals("boom", failed.errorMessage)
        val syncedWithError = upload(SyncState.Synced, errorMessage = "boom").toUi()
        assertNull(syncedWithError.errorMessage)
    }

    @Test
    fun `decimal weights format with comma and drop trailing zeros`() {
        val ui = upload(SyncState.Pending, weightKg = 72.5, heightCm = 170.0).toUi()
        assertEquals("72,5 kg · 170 cm", ui.measurements)
    }

    @Test
    fun `garbage measuredAt falls back to the raw string`() {
        val ui = upload(SyncState.Pending, measuredAt = "not-a-date").toUi()
        assertEquals("not-a-date", ui.measuredAtLabel)
    }
}
