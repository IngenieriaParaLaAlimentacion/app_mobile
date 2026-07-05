package ar.com.ingenieriaalimentacion.data.profile.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ar.com.ingenieriaalimentacion.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultCurrentEstablishmentLocalStoreTest {

    private lateinit var store: DefaultCurrentEstablishmentLocalStore

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val queries = AppDatabase(driver).currentEstablishmentQueries
        store = DefaultCurrentEstablishmentLocalStore(queries, now = { 10L }, dispatcher = Dispatchers.Unconfined)
    }

    @Test
    fun `get returns null before anything is set`() = runTest {
        assertNull(store.get())
    }

    @Test
    fun `set then get returns the stored id`() = runTest {
        store.set("e1")

        assertEquals("e1", store.get())
    }

    @Test
    fun `set overwrites the single row instead of adding a second one`() = runTest {
        store.set("e1")

        store.set("e2")

        assertEquals("e2", store.get())
    }

    @Test
    fun `set null clears it back to null`() = runTest {
        store.set("e1")

        store.set(null)

        assertNull(store.get())
    }
}
