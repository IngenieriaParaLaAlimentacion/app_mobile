package ar.com.ingenieriaalimentacion.presentation.feature.measurement.uploads

import ar.com.ingenieriaalimentacion.core.design_system.components.badge.AlBadgeTone
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
    fun `pending maps to neutral badge with no actions but delete`() {
        val ui = upload(SyncState.Pending).toUi()
        assertEquals("Pendiente", ui.badgeLabel)
        assertEquals(AlBadgeTone.Neutral, ui.badgeTone)
        assertFalse(ui.showSpinner)
        assertFalse(ui.canRetry)
        assertTrue(ui.canDelete)
    }

    @Test
    fun `syncing maps to neutral badge with spinner and no actions`() {
        val ui = upload(SyncState.Syncing).toUi()
        assertEquals("Sincronizando", ui.badgeLabel)
        assertEquals(AlBadgeTone.Neutral, ui.badgeTone)
        assertTrue(ui.showSpinner)
        assertFalse(ui.canRetry)
        assertFalse(ui.canDelete)
    }

    @Test
    fun `synced maps to success badge, allows delete, no retry`() {
        val ui = upload(SyncState.Synced, bmi = 23.7).toUi()
        assertEquals("Sincronizado", ui.badgeLabel)
        assertEquals(AlBadgeTone.Success, ui.badgeTone)
        assertFalse(ui.canRetry)
        assertTrue(ui.canDelete)
    }

    @Test
    fun `failed maps to danger badge, allows retry and delete`() {
        val ui = upload(SyncState.Failed, errorMessage = "422 Unprocessable").toUi()
        assertEquals("Error", ui.badgeLabel)
        assertEquals(AlBadgeTone.Danger, ui.badgeTone)
        assertTrue(ui.canRetry)
        assertTrue(ui.canDelete)
    }

    @Test
    fun `synced row with bmi and alerts populates bmiLabel and alerts`() {
        val ui = upload(
            SyncState.Synced,
            bmi = 23.7,
            alerts = listOf(
                ClinicalAlert(alertType = "BMI", severity = "high", detail = "Sobrepeso"),
                ClinicalAlert(alertType = "BMI", severity = "moderate", detail = "Vigilar"),
                ClinicalAlert(alertType = "BMI", severity = "info", detail = "Nota"),
            ),
        ).toUi()
        assertEquals("IMC 23,7", ui.bmiLabel)
        assertEquals(3, ui.alerts.size)
        assertEquals("BMI · high", ui.alerts[0].label)
        assertEquals("Sobrepeso", ui.alerts[0].detail)
        assertEquals(AlPillTone.Danger, ui.alerts[0].tone)
        assertEquals(AlPillTone.Warning, ui.alerts[1].tone)
        assertEquals(AlPillTone.Neutral, ui.alerts[2].tone)
    }

    @Test
    fun `non-synced row never shows bmi or alerts even when present`() {
        val ui = upload(
            SyncState.Pending,
            bmi = 23.7,
            alerts = listOf(ClinicalAlert("BMI", "high", "Sobrepeso")),
        ).toUi()
        assertNull(ui.bmiLabel)
        assertTrue(ui.alerts.isEmpty())
    }

    @Test
    fun `subtitle carries dni, weight, height and date`() {
        val ui = upload(SyncState.Pending).toUi()
        assertTrue(ui.subtitle.startsWith("DNI 42 · 70 kg · 172 cm · "))
        // 18:30 UTC; date/time portion depends on TZ but the dd/MM HH:mm shape is present.
        assertTrue(Regex("""\d{2}/\d{2} \d{2}:\d{2}""").containsMatchIn(ui.subtitle))
    }

    @Test
    fun `subtitle appends error only on failed rows`() {
        val failed = upload(SyncState.Failed, errorMessage = "boom").toUi()
        assertTrue(failed.subtitle.endsWith("· boom"))
        val syncedWithError = upload(SyncState.Synced, errorMessage = "boom").toUi()
        assertFalse(syncedWithError.subtitle.contains("boom"))
    }

    @Test
    fun `decimal weights format with comma and drop trailing zeros`() {
        val ui = upload(SyncState.Pending, weightKg = 72.5, heightCm = 170.0).toUi()
        assertTrue(ui.subtitle.contains("72,5 kg"))
        assertTrue(ui.subtitle.contains("170 cm"))
    }

    @Test
    fun `garbage measuredAt falls back to the raw string`() {
        val ui = upload(SyncState.Pending, measuredAt = "not-a-date").toUi()
        assertTrue(ui.subtitle.contains("not-a-date"))
    }
}
