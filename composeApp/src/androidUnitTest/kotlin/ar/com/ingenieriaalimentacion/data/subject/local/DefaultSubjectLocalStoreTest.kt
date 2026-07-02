package ar.com.ingenieriaalimentacion.data.subject.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ar.com.ingenieriaalimentacion.data.db.AppDatabase
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultSubjectLocalStoreTest {

    private lateinit var store: DefaultSubjectLocalStore
    private var time = 100L

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val queries = AppDatabase(driver).pendingSubjectQueries
        store = DefaultSubjectLocalStore(queries, now = { time }, dispatcher = Dispatchers.Unconfined)
    }

    private fun row(
        localId: String,
        syncState: SyncState = SyncState.Pending,
        attemptCount: Int = 0,
    ) = SubjectUpload(
        localId = localId,
        dni = 42,
        sex = Sex.Female,
        name = "Ana",
        lastName = "García",
        birthDate = LocalDate.parse("1990-01-01"),
        isRural = true,
        isIndigenous = false,
        syncState = syncState,
        canonicalId = null,
        errorMessage = null,
        attemptCount = attemptCount,
        createdAt = 10L,
        updatedAt = 10L,
    )

    @Test
    fun `insert then observeAll emits the row mapped back to domain`() = runTest {
        store.insert(row("r1"))

        val all = store.observeAll().first()

        assertEquals(1, all.size)
        val upload = all.first()
        assertEquals("r1", upload.localId)
        assertEquals(42, upload.dni)
        assertEquals(Sex.Female, upload.sex)
        assertTrue(upload.isRural)
        assertEquals(SyncState.Pending, upload.syncState)
    }

    @Test
    fun `selectSyncable returns only PENDING rows`() = runTest {
        store.insert(row("pending"))
        store.insert(row("syncing", syncState = SyncState.Syncing))
        store.insert(row("synced", syncState = SyncState.Synced))

        val syncable = store.syncable()

        assertEquals(listOf("pending"), syncable.map { it.localId })
    }

    @Test
    fun `markSynced sets state and canonical id`() = runTest {
        store.insert(row("r1"))

        store.markSynced("r1", SyncState.Merged, "canon-1")

        val upload = store.observeAll().first().first()
        assertEquals(SyncState.Merged, upload.syncState)
        assertEquals("canon-1", upload.canonicalId)
    }

    @Test
    fun `bumpRetry from SYNCING returns the row to PENDING and counts the attempt`() = runTest {
        store.insert(row("r1"))
        store.markSyncing("r1")

        store.bumpRetry("r1", "retry")

        val upload = store.observeAll().first().first()
        assertEquals(SyncState.Pending, upload.syncState)
        assertEquals(1, upload.attemptCount)
    }

    @Test
    fun `markFailed sets FAILED and resetForRetry clears it`() = runTest {
        store.insert(row("r1"))

        store.markFailed("r1", "boom")
        assertEquals(SyncState.Failed, store.observeAll().first().first().syncState)

        store.resetForRetry("r1")
        val upload = store.observeAll().first().first()
        assertEquals(SyncState.Pending, upload.syncState)
        assertEquals(0, upload.attemptCount)
        assertNull(upload.errorMessage)
    }

    @Test
    fun `resetStuckSyncing flips SYNCING rows to PENDING`() = runTest {
        store.insert(row("r1"))
        store.markSyncing("r1")

        store.resetStuckSyncing()

        assertEquals(SyncState.Pending, store.observeAll().first().first().syncState)
    }

    @Test
    fun `pruneTerminalOlderThan deletes old SYNCED rows only`() = runTest {
        store.insert(row("r1"))
        time = 50L
        store.markSynced("r1", SyncState.Synced, "r1") // updatedAt = 50
        store.insert(row("r2")) // PENDING, updatedAt = 10

        store.pruneTerminalOlderThan(cutoff = 100L)

        val ids = store.observeAll().first().map { it.localId }
        assertEquals(listOf("r2"), ids)
    }

    @Test
    fun `pruneTerminalOlderThan exempts MERGED rows even when old`() = runTest {
        store.insert(row("merged"))
        time = 50L
        store.markSynced("merged", SyncState.Merged, "canon-1") // updatedAt = 50
        store.insert(row("synced"))
        store.markSynced("synced", SyncState.Synced, "synced") // updatedAt = 50

        store.pruneTerminalOlderThan(cutoff = 100L)

        val ids = store.observeAll().first().map { it.localId }
        assertEquals(listOf("merged"), ids)
    }

    @Test
    fun `delete removes the row`() = runTest {
        store.insert(row("r1"))

        store.delete("r1")

        assertTrue(store.observeAll().first().isEmpty())
    }
}
