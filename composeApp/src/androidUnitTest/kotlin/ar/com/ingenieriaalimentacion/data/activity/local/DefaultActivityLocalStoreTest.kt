package ar.com.ingenieriaalimentacion.data.activity.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ar.com.ingenieriaalimentacion.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultActivityLocalStoreTest {

    private lateinit var store: DefaultActivityLocalStore

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val queries = AppDatabase(driver).cachedActivityItemQueries
        store = DefaultActivityLocalStore(queries, dispatcher = Dispatchers.Unconfined)
    }

    private fun row(id: String, occurredAtMillis: Long) =
        CachedActivityRow(id = id, occurredAtMillis = occurredAtMillis, payloadJson = """{"id":"$id"}""")

    @Test
    fun `upserting past the cap evicts the oldest rows for that type only`() = runTest {
        val rows = (1..210).map { row("subject-$it", occurredAtMillis = it.toLong()) }

        store.upsertPage(ActivityItemType.SUBJECT, rows, cap = 200)

        val remaining = store.observe(ActivityItemType.SUBJECT).first()
        assertEquals(200, remaining.size)
        // The 10 oldest (occurredAtMillis 1..10) are gone; the newest 200 survive.
        assertFalse(remaining.any { it.id == "subject-1" })
        assertFalse(remaining.any { it.id == "subject-10" })
        assertTrue(remaining.any { it.id == "subject-11" })
        assertTrue(remaining.any { it.id == "subject-210" })
    }

    @Test
    fun `eviction of one type does not touch the other type's rows`() = runTest {
        store.upsertPage(ActivityItemType.MEASUREMENT, listOf(row("m-1", 1L)), cap = 200)
        val subjectRows = (1..210).map { row("subject-$it", occurredAtMillis = it.toLong()) }

        store.upsertPage(ActivityItemType.SUBJECT, subjectRows, cap = 200)

        val measurements = store.observe(ActivityItemType.MEASUREMENT).first()
        assertEquals(listOf("m-1"), measurements.map { it.id })
    }

    @Test
    fun `the cap is enforced per type independently with four types present`() = runTest {
        // The Alimentos section is fed by two cache types at once, so the cache holds up to
        // 200 + 200 food rows and the rarer direction is never starved by the common one.
        store.upsertPage(ActivityItemType.SUBJECT, listOf(row("s-1", 1L)), cap = 200)
        store.upsertPage(ActivityItemType.MEASUREMENT, listOf(row("m-1", 1L)), cap = 200)
        store.upsertPage(ActivityItemType.FOOD_OUTFLOW, listOf(row("out:o-1", 1L)), cap = 200)

        val intakes = (1..201).map { row("in:i-$it", occurredAtMillis = it.toLong()) }
        store.upsertPage(ActivityItemType.FOOD_INTAKE, intakes, cap = 200)

        val remainingIntakes = store.observe(ActivityItemType.FOOD_INTAKE).first()
        assertEquals(200, remainingIntakes.size)
        assertFalse(remainingIntakes.any { it.id == "in:i-1" })
        assertEquals(listOf("out:o-1"), store.observe(ActivityItemType.FOOD_OUTFLOW).first().map { it.id })
        assertEquals(listOf("s-1"), store.observe(ActivityItemType.SUBJECT).first().map { it.id })
        assertEquals(listOf("m-1"), store.observe(ActivityItemType.MEASUREMENT).first().map { it.id })
    }

    @Test
    fun `v8 migration removes only cached food intakes`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val local = DefaultActivityLocalStore(AppDatabase(driver).cachedActivityItemQueries,
            dispatcher = Dispatchers.Unconfined)
        local.upsertPage(ActivityItemType.FOOD_INTAKE, listOf(row("in:i-1", 1L)))
        local.upsertPage(ActivityItemType.FOOD_OUTFLOW, listOf(row("out:o-1", 2L)))
        local.upsertPage(ActivityItemType.SUBJECT, listOf(row("subject-1", 3L)))
        local.upsertPage(ActivityItemType.MEASUREMENT, listOf(row("measurement-1", 4L)))

        AppDatabase.Schema.migrate(driver, 8L, 9L)

        assertTrue(local.observe(ActivityItemType.FOOD_INTAKE).first().isEmpty())
        assertEquals(listOf("out:o-1"), local.observe(ActivityItemType.FOOD_OUTFLOW).first().map { it.id })
        assertEquals(listOf("subject-1"), local.observe(ActivityItemType.SUBJECT).first().map { it.id })
        assertEquals(listOf("measurement-1"), local.observe(ActivityItemType.MEASUREMENT).first().map { it.id })
    }

    @Test
    fun `getPage paginates newest-first`() = runTest {
        val rows = (1..30).map { row("subject-$it", occurredAtMillis = it.toLong()) }
        store.upsertPage(ActivityItemType.SUBJECT, rows, cap = 200)

        val firstPage = store.getPage(ActivityItemType.SUBJECT, limit = 20, offset = 0)
        val secondPage = store.getPage(ActivityItemType.SUBJECT, limit = 20, offset = 20)

        assertEquals("subject-30", firstPage.first().id)
        assertEquals(20, firstPage.size)
        assertEquals(10, secondPage.size)
    }

    @Test
    fun `clear wipes every row`() = runTest {
        store.upsertPage(ActivityItemType.SUBJECT, listOf(row("s-1", 1L)), cap = 200)
        store.upsertPage(ActivityItemType.MEASUREMENT, listOf(row("m-1", 1L)), cap = 200)

        store.clear()

        assertTrue(store.observe(ActivityItemType.SUBJECT).first().isEmpty())
        assertTrue(store.observe(ActivityItemType.MEASUREMENT).first().isEmpty())
    }
}
