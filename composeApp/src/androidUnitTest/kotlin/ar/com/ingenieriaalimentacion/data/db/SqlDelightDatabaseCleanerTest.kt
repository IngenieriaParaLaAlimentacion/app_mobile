package ar.com.ingenieriaalimentacion.data.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

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

    private fun seedAllTables() {
        database.pendingSubjectQueries.insert(
            localId = "s1",
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
            localId = "m1",
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
            createdAt = 10L,
            updatedAt = 10L,
        )
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
        database.pendingSurveyResponseQueries.insert(
            localId = "sr-1",
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
            editId = "se-1",
            responseId = "sr-1",
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

    @Test
    fun `clearAll wipes every local table`() = runTest {
        seedAllTables()
        // Sanity: each table has a row before the wipe.
        assertTrue(database.pendingSubjectQueries.selectAll().executeAsList().isNotEmpty())
        assertTrue(database.pendingMeasurementQueries.selectAll().executeAsList().isNotEmpty())
        assertTrue(database.cachedEstablishmentQueries.selectAll().executeAsList().isNotEmpty())
        assertTrue(database.cachedActivityItemQueries.selectByType("SUBJECT").executeAsList().isNotEmpty())
        assertTrue(database.cachedActivityItemQueries.selectByType("FOOD_INTAKE").executeAsList().isNotEmpty())
        assertTrue(database.cachedActivityItemQueries.selectByType("FOOD_OUTFLOW").executeAsList().isNotEmpty())
        assertTrue(database.currentEstablishmentQueries.select().executeAsOneOrNull()?.establishmentId != null)
        assertTrue(database.cachedSurveyQueries.selectAll().executeAsList().isNotEmpty())
        assertTrue(database.pendingSurveyResponseQueries.selectAll().executeAsList().isNotEmpty())
        assertTrue(database.pendingSurveyResponseEditQueries.selectAll().executeAsList().isNotEmpty())

        cleaner.clearAll()

        assertTrue(database.pendingSubjectQueries.selectAll().executeAsList().isEmpty())
        assertTrue(database.pendingMeasurementQueries.selectAll().executeAsList().isEmpty())
        assertTrue(database.cachedEstablishmentQueries.selectAll().executeAsList().isEmpty())
        assertTrue(database.cachedActivityItemQueries.selectByType("SUBJECT").executeAsList().isEmpty())
        assertTrue(database.cachedActivityItemQueries.selectByType("FOOD_INTAKE").executeAsList().isEmpty())
        assertTrue(database.cachedActivityItemQueries.selectByType("FOOD_OUTFLOW").executeAsList().isEmpty())
        assertTrue(database.currentEstablishmentQueries.select().executeAsOneOrNull()?.establishmentId == null)
        assertTrue(database.cachedSurveyQueries.selectAll().executeAsList().isEmpty())
        assertTrue(database.pendingSurveyResponseQueries.selectAll().executeAsList().isEmpty())
        assertTrue(database.pendingSurveyResponseEditQueries.selectAll().executeAsList().isEmpty())
    }
}
