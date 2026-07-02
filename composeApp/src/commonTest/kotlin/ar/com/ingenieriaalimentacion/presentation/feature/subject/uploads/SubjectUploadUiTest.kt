package ar.com.ingenieriaalimentacion.presentation.feature.subject.uploads

import ar.com.ingenieriaalimentacion.core.design_system.components.badge.AlBadgeTone
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryEntry
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubjectUploadUiTest {

    private fun entry(
        syncState: SyncState,
        canonicalId: String? = null,
        errorMessage: String? = null,
        isLocal: Boolean = true,
        dni: Int = 42,
    ) = SubjectHistoryEntry(
        id = "s1",
        dni = dni,
        sex = Sex.Female,
        name = "Ana",
        lastName = "García",
        syncState = syncState,
        canonicalId = canonicalId,
        errorMessage = errorMessage,
        isLocal = isLocal,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `pending local row allows delete but not retry`() {
        val ui = entry(SyncState.Pending).toUi()
        assertEquals("Pendiente", ui.badgeLabel)
        assertEquals(AlBadgeTone.Neutral, ui.badgeTone)
        assertFalse(ui.showSpinner)
        assertFalse(ui.canRetry)
        assertTrue(ui.canDelete)
    }

    @Test
    fun `syncing local row shows spinner with no actions`() {
        val ui = entry(SyncState.Syncing).toUi()
        assertEquals("Sincronizando", ui.badgeLabel)
        assertTrue(ui.showSpinner)
        assertFalse(ui.canRetry)
        assertFalse(ui.canDelete)
    }

    @Test
    fun `failed local row allows retry and delete, with the error appended to the subtitle`() {
        val ui = entry(SyncState.Failed, errorMessage = "422 Unprocessable").toUi()
        assertEquals("Error", ui.badgeLabel)
        assertEquals(AlBadgeTone.Danger, ui.badgeTone)
        assertTrue(ui.canRetry)
        assertTrue(ui.canDelete)
        assertTrue(ui.subtitle.contains("422 Unprocessable"))
    }

    @Test
    fun `merged local row allows delete but not retry, with the canonical id appended`() {
        val ui = entry(SyncState.Merged, canonicalId = "canon-1").toUi()
        assertEquals("Ya existía", ui.badgeLabel)
        assertEquals(AlBadgeTone.Warning, ui.badgeTone)
        assertFalse(ui.canRetry)
        assertTrue(ui.canDelete)
        assertTrue(ui.subtitle.contains("canon-1"))
    }

    @Test
    fun `a remote-sourced Synced entry never allows retry or delete regardless of syncState`() {
        val synced = entry(SyncState.Synced, isLocal = false).toUi()
        assertFalse(synced.canRetry)
        assertFalse(synced.canDelete)

        val merged = entry(SyncState.Merged, canonicalId = "canon-1", isLocal = false).toUi()
        assertFalse(merged.canRetry)
        assertFalse(merged.canDelete)
    }

    @Test
    fun `subtitle always leads with the dni`() {
        val ui = entry(SyncState.Synced, dni = 99).toUi()
        assertEquals("DNI 99", ui.subtitle)
    }
}
