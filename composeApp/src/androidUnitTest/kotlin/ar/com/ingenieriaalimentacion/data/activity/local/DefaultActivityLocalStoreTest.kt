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
