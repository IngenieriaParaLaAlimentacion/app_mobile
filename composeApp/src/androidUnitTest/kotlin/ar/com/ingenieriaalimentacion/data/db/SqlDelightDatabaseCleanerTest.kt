package ar.com.ingenieriaalimentacion.data.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlDelightDatabaseCleanerTest {

    private lateinit var database: AppDatabase
    private lateinit var cleaner: SqlDelightDatabaseCleaner

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        database = AppDatabase(driver)
        cleaner = SqlDelightDatabaseCleaner(database, dispatcher = Dispatchers.Unconfined)
    }

    private fun seedQueues(ownerId: String) {
        database.pendingSubjectQueries.insert(
            ownerUserId = ownerId,
            localId = "s1-$ownerId",
            dni = 42L,
            sex = "female",
            name = "Ana",
            lastName = "García",
            birthDate = "1990-01-01",
            isRural = 1L,
            isIndigenous = 0L,
            syncState = "PENDING",
            canonicalId = null,
            errorMessage = null,
            attemptCount = 0L,
            createdAt = 10L,
            updatedAt = 10L,
        )
        database.pendingMeasurementQueries.insert(
            ownerUserId = ownerId,
            localId = "m1-$ownerId",
            subjectId = "subj-1",
            subjectName = "Ana",
            subjectLastName = "García",
            subjectDni = 42L,
            establishmentId = "est-1",
            establishmentName = "Norte",
            weightKg = 70.0,
            heightCm = 170.0,
            measuredAt = "2026-01-01T00:00:00Z",
            syncState = "PENDING",
            errorMessage = null,
            attemptCount = 0L,
            bmi = null,
            alertsJson = null,
            observation = null,
            createdAt = 10L,
            updatedAt = 10L,
        )
        database.pendingSurveyResponseQueries.insert(
            ownerUserId = ownerId,
            localId = "sr-1-$ownerId",
            surveyId = "sv-1",
            surveyName = "Encuesta",
            responseTarget = "ESTABLISHMENT",
            targetId = "est-1",
            targetLabel = "Norte",
            answersJson = "{}",
            eventAt = "2026-01-01T00:00:00Z",
            syncState = "PENDING",
            canonicalId = null,
            errorMessage = null,
            conflictBy = null,
            conflictAt = null,
            attemptCount = 0L,
            createdAt = 10L,
            updatedAt = 10L,
            responseNumber = null,
        )
        database.pendingSurveyResponseEditQueries.insert(
            ownerUserId = ownerId,
            editId = "se-1-$ownerId",
            responseId = "sr-1-$ownerId",
            surveyId = "s-1",
            surveyName = "Encuesta",
            establishmentName = "Norte",
            answersJson = "{}",
            eventAt = "2026-01-01T00:00:00Z",
            syncState = "PENDING",
            errorMessage = null,
            attemptCount = 0L,
            createdAt = 10L,
            updatedAt = 10L,
            responseNumber = null,
        )
    }

    private fun seedCaches() {
        database.cachedEstablishmentQueries.insert(
            id = "est-1",
            name = "Norte",
            formattedAddress = "Calle 1",
            latitude = 0.0,
            longitude = 0.0,
            placeId = "p1",
            city = "Ciudad",
            province = "Provincia",
            status = "Approved",
            updatedAt = 10L,
            characterizationStatus = null,
        )
        database.cachedActivityItemQueries.upsert(
            id = "a1",
            type = "SUBJECT",
            occurredAtMillis = 10L,
            payloadJson = "{}",
        )
        // `deleteAll` takes no `:type`, so the food types are wiped by the same statement. Seeded
        // explicitly so that property is locked rather than incidental.
        database.cachedActivityItemQueries.upsert(
            id = "in:a2",
            type = "FOOD_INTAKE",
            occurredAtMillis = 10L,
            payloadJson = "{}",
        )
        database.cachedActivityItemQueries.upsert(
            id = "out:a3",
            type = "FOOD_OUTFLOW",
            occurredAtMillis = 10L,
            payloadJson = "{}",
        )
        database.currentEstablishmentQueries.set("est-1", 10L)
        database.cachedSurveyQueries.insertOrReplace(
            id = "sv-1",
            name = "Encuesta",
            responseTarget = "GENERIC",
            isActive = 1L,
            questionCount = 3L,
            detailJson = null,
            updatedAt = 10L,
            requiresResponseNumber = null,
        )
    }

    private fun assertCacheSize(size: Int) {
        assertEquals(size, database.cachedEstablishmentQueries.selectAll().executeAsList().size)
        assertEquals(size, database.cachedActivityItemQueries.selectByType("SUBJECT").executeAsList().size)
        assertEquals(size, database.cachedActivityItemQueries.selectByType("FOOD_INTAKE").executeAsList().size)
        assertEquals(size, database.cachedActivityItemQueries.selectByType("FOOD_OUTFLOW").executeAsList().size)
        assertEquals(size, database.cachedSurveyQueries.selectAll().executeAsList().size)
        assertEquals(size == 0, database.currentEstablishmentQueries.select().executeAsOneOrNull()?.establishmentId == null)
    }

    private fun assertQueueSize(ownerId: String, size: Int) {
        assertEquals(size, database.pendingSubjectQueries.selectAll(ownerId).executeAsList().size)
        assertEquals(size, database.pendingMeasurementQueries.selectAll(ownerId).executeAsList().size)
        assertEquals(size, database.pendingSurveyResponseQueries.selectAll(ownerId).executeAsList().size)
        assertEquals(size, database.pendingSurveyResponseEditQueries.selectAll(ownerId).executeAsList().size)
    }

    @Test
    fun `cache cleanup keeps all accounts' queues and sign-out deletes only the departing owner`() = runTest {
        seedQueues("owner-a")
        seedQueues("owner-b")
        seedCaches()
        assertCacheSize(1)
        assertQueueSize("owner-a", 1)
        assertQueueSize("owner-b", 1)

        cleaner.clearCaches()

        assertCacheSize(0)
        assertQueueSize("owner-a", 1)
        assertQueueSize("owner-b", 1)

        seedCaches()
        cleaner.clearForSignOut("owner-b")

        assertCacheSize(0)
        assertQueueSize("owner-a", 1)
        assertQueueSize("owner-b", 0)
    }
}
