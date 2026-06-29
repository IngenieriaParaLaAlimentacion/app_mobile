package ar.com.ingenieriaalimentacion.presentation.feature.home

import ar.com.ingenieriaalimentacion.core.design_system.components.badge.AlBadgeTone
import ar.com.ingenieriaalimentacion.domain.model.measurement.ClinicalAlert
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertIs

class HomeStateMapperTest {

    private val tz = TimeZone.UTC

    // 2026-06-29T12:00:00Z so "today" math is stable regardless of the host clock.
    private val now = 1_782_000_000_000L
    private val startOfToday = startOfTodayMillis(now, tz)

    private fun subject(
        localId: String = "s1",
        syncState: SyncState = SyncState.Pending,
        createdAt: Long = now,
        updatedAt: Long = now,
        name: String = "Ana",
        lastName: String = "García",
        dni: Int = 42,
    ) = SubjectUpload(
        localId = localId,
        dni = dni,
        sex = Sex.Female,
        name = name,
        lastName = lastName,
        birthDate = LocalDate(2000, 1, 1),
        isRural = false,
        isIndigenous = false,
        syncState = syncState,
        canonicalId = null,
        errorMessage = null,
        attemptCount = 0,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun measurement(
        localId: String = "m1",
        syncState: SyncState = SyncState.Pending,
        createdAt: Long = now,
        updatedAt: Long = now,
        bmi: Double? = null,
        alerts: List<ClinicalAlert> = emptyList(),
        subjectName: String = "Juan",
        subjectLastName: String = "Pérez",
    ) = MeasurementUpload(
        localId = localId,
        subjectId = "s1",
        subjectName = subjectName,
        subjectLastName = subjectLastName,
        subjectDni = 42,
        establishmentId = "e1",
        establishmentName = "Escuela 1",
        weightKg = 70.0,
        heightCm = 172.0,
        measuredAt = "2026-06-29T10:00:00Z",
        syncState = syncState,
        errorMessage = null,
        attemptCount = 0,
        bmi = bmi,
        alerts = alerts,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    @Test
    fun `all synced online yields AllSynced with a formatted last-synced label`() {
        val state = deriveState(
            subjects = listOf(subject(syncState = SyncState.Synced, updatedAt = now)),
            measurements = listOf(measurement(syncState = SyncState.Synced, updatedAt = now - 60_000)),
            online = true,
            now = now,
            timeZone = tz,
        )
        val sync = assertIs<SyncStatusUi.AllSynced>(state.sync)
        assertTrue(sync.lastSyncedLabel != null && sync.lastSyncedLabel.contains("Última subida"))
    }

    @Test
    fun `all done offline yields OfflineClean`() {
        val state = deriveState(
            subjects = listOf(subject(syncState = SyncState.Synced)),
            measurements = emptyList(),
            online = false,
            now = now,
            timeZone = tz,
        )
        assertIs<SyncStatusUi.OfflineClean>(state.sync)
    }

    @Test
    fun `a syncing row wins regardless of online`() {
        val online = deriveState(
            subjects = listOf(subject(syncState = SyncState.Syncing)),
            measurements = listOf(measurement(syncState = SyncState.Pending)),
            online = true,
            now = now,
            timeZone = tz,
        )
        assertEquals(SyncStatusUi.Syncing(1), online.sync) // counts only the Syncing row

        val offline = deriveState(
            subjects = listOf(subject(syncState = SyncState.Syncing)),
            measurements = emptyList(),
            online = false,
            now = now,
            timeZone = tz,
        )
        assertEquals(SyncStatusUi.Syncing(1), offline.sync)
    }

    @Test
    fun `pending rows map to PendingOnline or PendingOffline by connectivity`() {
        val subjects = listOf(subject(syncState = SyncState.Pending))
        val measurements = listOf(measurement(syncState = SyncState.Pending))
        assertEquals(
            SyncStatusUi.PendingOnline(2),
            deriveState(subjects, measurements, online = true, now = now, timeZone = tz).sync,
        )
        assertEquals(
            SyncStatusUi.PendingOffline(2),
            deriveState(subjects, measurements, online = false, now = now, timeZone = tz).sync,
        )
    }

    @Test
    fun `a failed row wins over pending`() {
        val state = deriveState(
            subjects = listOf(
                subject(localId = "s1", syncState = SyncState.Failed),
                subject(localId = "s2", syncState = SyncState.Pending),
            ),
            measurements = emptyList(),
            online = true,
            now = now,
            timeZone = tz,
        )
        assertEquals(SyncStatusUi.Failed(failedCount = 1, pendingCount = 1), state.sync)
    }

    @Test
    fun `today counts respect the start-of-day boundary`() {
        val yesterday2359 = startOfToday - 60_000 // one minute before today
        val state = deriveState(
            subjects = listOf(
                subject(localId = "today", createdAt = startOfToday),
                subject(localId = "yesterday", createdAt = yesterday2359),
            ),
            measurements = listOf(
                measurement(localId = "m-today", createdAt = startOfToday + 1, alerts = listOf(alert("high"))),
                measurement(localId = "m-yesterday", createdAt = yesterday2359),
            ),
            online = true,
            now = now,
            timeZone = tz,
        )
        assertEquals(1, state.todaySubjects)
        assertEquals(1, state.todayMeasurements)
        assertEquals(1, state.todayAlerts)
    }

    @Test
    fun `recent merges both sources, sorts desc, caps at 4`() {
        val subjects = (1..3).map { subject(localId = "s$it", createdAt = now - it * 1_000L) }
        val measurements = (1..3).map { measurement(localId = "m$it", createdAt = now - (it * 1_000L - 500)) }
        val state = deriveState(subjects, measurements, online = true, now = now, timeZone = tz)
        assertEquals(4, state.recent.size)
        val timestamps = state.recent.map { it.createdAt }
        assertEquals(timestamps.sortedDescending(), timestamps)
    }

    @Test
    fun `measurement with alerts shows the alert badge, without shows the sync badge`() {
        val withAlert = deriveState(
            subjects = emptyList(),
            measurements = listOf(
                measurement(
                    syncState = SyncState.Synced,
                    bmi = 24.1,
                    alerts = listOf(alert("moderate", type = "IMC"), alert("high", type = "Tensión")),
                ),
            ),
            online = true,
            now = now,
            timeZone = tz,
        ).recent.single()
        assertEquals("Tensión", withAlert.badgeText) // most severe (high -> Danger) wins
        assertEquals(AlBadgeTone.Danger, withAlert.badgeTone)
        assertEquals("IMC 24,1", withAlert.value)

        val withoutAlert = deriveState(
            subjects = emptyList(),
            measurements = listOf(measurement(syncState = SyncState.Synced, bmi = 24.1)),
            online = true,
            now = now,
            timeZone = tz,
        ).recent.single()
        assertEquals("Sincronizado", withoutAlert.badgeText)
        assertEquals(AlBadgeTone.Success, withoutAlert.badgeTone)
    }

    @Test
    fun `subject recent row carries dni subtitle and a sync badge`() {
        val row = deriveState(
            subjects = listOf(subject(syncState = SyncState.Merged, dni = 99)),
            measurements = emptyList(),
            online = true,
            now = now,
            timeZone = tz,
        ).recent.single()
        assertEquals(RecentActivityUi.Kind.Subject, row.kind)
        assertEquals("DNI 99", row.subtitle)
        assertNull(row.value)
        assertEquals("Ya existía", row.badgeText)
        assertEquals(AlBadgeTone.Warning, row.badgeTone)
    }

    private fun alert(severity: String, type: String = "IMC") =
        ClinicalAlert(alertType = type, severity = severity, detail = "detalle")
}
