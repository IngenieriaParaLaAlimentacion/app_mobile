package ar.com.ingenieriaalimentacion.data.survey.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ar.com.ingenieriaalimentacion.data.db.AppDatabase
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.model.survey.ResponseConflict
import ar.com.ingenieriaalimentacion.domain.model.survey.ResponseTargetRef
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseUpload
import ar.com.ingenieriaalimentacion.domain.model.survey.isEligibleAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DefaultSurveyResponseLocalStoreTest {

    private lateinit var store: DefaultSurveyResponseLocalStore
    private var time = 100L

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val queries = AppDatabase(driver).pendingSurveyResponseQueries
        store = DefaultSurveyResponseLocalStore(queries, now = { time }, dispatcher = Dispatchers.Unconfined)
    }

    private fun row(
        localId: String,
        target: ResponseTargetRef,
        syncState: SyncState = SyncState.Pending,
        responseNumber: Int? = null,
        errorMessage: String? = null,
    ) = SurveyResponseUpload(
        localId = localId,
        surveyId = "sv-1",
        surveyName = "Encuesta",
        target = target,
        answersJson = """{"q1":"opt-1"}""",
        eventAt = "2026-01-01T00:00:00Z",
        syncState = syncState,
        canonicalId = null,
        errorMessage = errorMessage,
        conflict = null,
        attemptCount = 0,
        createdAt = 10L,
        updatedAt = 10L,
        responseNumber = responseNumber,
    )

    @Test
    fun `a subject-targeted row round-trips through the DB`() = runTest {
        store.insert(row("r1", ResponseTargetRef.Subject("subj-1", "Ana García")))

        assertEquals(ResponseTargetRef.Subject("subj-1", "Ana García"), store.find("r1")?.target)
    }

    @Test
    fun `an establishment-targeted row round-trips through the DB`() = runTest {
        store.insert(row("r1", ResponseTargetRef.Establishment("est-1", "Comedor Norte")))

        assertEquals(ResponseTargetRef.Establishment("est-1", "Comedor Norte"), store.find("r1")?.target)
    }

    @Test
    fun `awaiting number policy selects only the exact legacy failure`() = runTest {
        val target = ResponseTargetRef.Anonymous
        store.insert(
            row(
                "legacy",
                target,
                syncState = SyncState.Failed,
                errorMessage = "Falta el número de respuesta.",
            ),
        )
        store.insert(
            row(
                "other-error",
                target,
                syncState = SyncState.Failed,
                errorMessage = "Las respuestas no son válidas.",
            ),
        )
        store.insert(
            row(
                "has-number",
                target,
                syncState = SyncState.Failed,
                responseNumber = 5,
                errorMessage = "Falta el número de respuesta.",
            ),
        )

        assertEquals(listOf("legacy"), store.awaitingNumberPolicy().map { it.localId })
    }

    @Test
    fun `an anonymous row round-trips with no target id or label`() = runTest {
        store.insert(row("r1", ResponseTargetRef.Anonymous))

        assertEquals(ResponseTargetRef.Anonymous, store.find("r1")?.target)
    }

    @Test
    fun `markConflict writes a terminal row that no sweep can pick up again`() = runTest {
        store.insert(row("r1", ResponseTargetRef.Subject("subj-1", "Ana García")))
        store.markFailed("r1", "Sin conexión.")

        time = 500L
        store.markConflict("r1", ResponseConflict(submittedByName = "Ana", eventAt = "2026-08-12T14:03:00Z"))

        val stored = checkNotNull(store.find("r1"))
        assertEquals(SyncState.Failed, stored.syncState)
        assertEquals(ResponseConflict("Ana", "2026-08-12T14:03:00Z"), stored.conflict)
        // The previous failure copy is cleared — the conflict message replaces it in the UI.
        assertNull(stored.errorMessage)
        assertFalse(stored.isEligibleAt(Long.MAX_VALUE))
        assertEquals(emptyList(), store.syncable())
    }

    @Test
    fun `delete removes the row for good`() = runTest {
        store.insert(row("r1", ResponseTargetRef.Anonymous))
        store.insert(row("r2", ResponseTargetRef.Anonymous))

        store.delete("r1")

        assertNull(store.find("r1"))
        assertEquals(listOf("r2"), store.all().map { it.localId })
    }
}
