package ar.com.ingenieriaalimentacion.data.measurement.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ar.com.ingenieriaalimentacion.data.db.AppDatabase
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultMeasurementLocalStoreTest {

    private lateinit var store: DefaultMeasurementLocalStore
    private var time = 100L

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val queries = AppDatabase(driver).pendingMeasurementQueries
        store = DefaultMeasurementLocalStore(queries, now = { time }, dispatcher = Dispatchers.Unconfined)
    }

    private fun row(
        localId: String,
        syncState: SyncState = SyncState.Pending,
    ) = MeasurementUpload(
        localId = localId,
        subjectId = "s",
        subjectName = "Ana",
        subjectLastName = "García",
        subjectDni = 42,
        establishmentId = "e",
        establishmentName = "Escuela",
        weightKg = 72.5,
        heightCm = 170.0,
        measuredAt = "2026-06-21T18:30:00Z",
        syncState = syncState,
        errorMessage = null,
        attemptCount = 0,
        bmi = null,
        alerts = emptyList(),
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
        assertEquals(72.5, upload.weightKg)
        assertEquals(170.0, upload.heightCm)
        assertEquals(42, upload.subjectDni)
        assertEquals(SyncState.Pending, upload.syncState)
        assertTrue(upload.alerts.isEmpty())
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
    fun `markSynced stores bmi and serialized alerts`() = runTest {
        store.insert(row("r1"))

        val alertsJson = """[{"alertType":"bmi_high","severity":"warning","detail":"Sobrepeso"}]"""
        store.markSynced("r1", bmi = 25.1, alertsJson = alertsJson)

        val upload = store.observeAll().first().first()
        assertEquals(SyncState.Synced, upload.syncState)
        assertEquals(25.1, upload.bmi)
        assertEquals(1, upload.alerts.size)
        assertEquals("bmi_high", upload.alerts.first().alertType)
    }

    @Test
    fun `resetSyncingToPending flips SYNCING rows to PENDING`() = runTest {
        store.insert(row("r1"))
        store.markSyncing("r1")

        store.resetStuckSyncing()

        assertEquals(SyncState.Pending, store.observeAll().first().first().syncState)
    }

    @Test
    fun `pruneTerminalOlderThan deletes old SYNCED rows only`() = runTest {
        store.insert(row("r1"))
        time = 50L
        store.markSynced("r1", bmi = null, alertsJson = null) // updatedAt = 50
        store.insert(row("r2")) // PENDING, updatedAt = 10

        store.pruneTerminalOlderThan(cutoff = 100L)

        val ids = store.observeAll().first().map { it.localId }
        assertEquals(listOf("r2"), ids)
    }
    @Test
    fun `observation survives queue writes failure retry and sync`() = runTest {
        val note = "Se repitió la medición.\nSin calzado."
        store.insert(row("noted").copy(observation = note))
        assertEquals(note, store.all().single().observation)
        store.markSyncing("noted")
        store.bumpRetry("noted", "Sin conexión")
        assertEquals(note, store.syncable().single().observation)
        store.markFailed("noted", "Reintentar")
        store.resetForRetry("noted")
        assertEquals(note, store.syncable().single().observation)
        store.markSynced("noted", null, null)
        assertEquals(note, store.all().single().observation)
    }

    @Test
    fun `v10 queue upgrade preserves existing queued rows with null observation`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, """
            CREATE TABLE PendingMeasurement (
                localId TEXT NOT NULL PRIMARY KEY, subjectId TEXT NOT NULL,
                subjectName TEXT NOT NULL, subjectLastName TEXT NOT NULL, subjectDni INTEGER NOT NULL,
                establishmentId TEXT NOT NULL, establishmentName TEXT NOT NULL,
                weightKg REAL NOT NULL, heightCm REAL NOT NULL, measuredAt TEXT NOT NULL,
                syncState TEXT NOT NULL, errorMessage TEXT, attemptCount INTEGER NOT NULL DEFAULT 0,
                bmi REAL, alertsJson TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL
            )
        """.trimIndent(), 0)
        driver.execute(null, """
            INSERT INTO PendingMeasurement VALUES ('old', 's', 'Ana', 'García', 42, 'e', 'Escuela',
                72.5, 170.0, '2026-06-21T18:30:00Z', 'PENDING', NULL, 0, NULL, NULL, 10, 10)
        """.trimIndent(), 0)
        AppDatabase.Schema.migrate(driver, 10L, 11L)
        val upgraded = DefaultMeasurementLocalStore(AppDatabase(driver).pendingMeasurementQueries,
            now = { time }, dispatcher = Dispatchers.Unconfined)
        val old = upgraded.syncable().single()
        assertEquals("old", old.localId)
        assertEquals(72.5, old.weightKg)
        assertEquals(null, old.observation)
        upgraded.insert(row("new").copy(observation = "Observación nueva"))
        assertEquals("Observación nueva", upgraded.all().first { it.localId == "new" }.observation)
        driver.close()
    }

}
