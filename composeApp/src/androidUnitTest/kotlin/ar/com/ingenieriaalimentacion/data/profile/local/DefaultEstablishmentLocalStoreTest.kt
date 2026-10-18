package ar.com.ingenieriaalimentacion.data.profile.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ar.com.ingenieriaalimentacion.data.db.AppDatabase
import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultEstablishmentLocalStoreTest {

    private lateinit var store: DefaultEstablishmentLocalStore

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        store = DefaultEstablishmentLocalStore(
            AppDatabase(driver).cachedEstablishmentQueries,
            now = { 10L },
            dispatcher = Dispatchers.Unconfined,
        )
    }

    private fun establishment(id: String, characterization: CharacterizationStatus?) = EstablishmentWithStatus(
        id = id, name = "Comedor $id", formattedAddress = "Calle 1", latitude = -34.6, longitude = -58.4,
        placeId = "p$id", city = "Ciudad", province = "Provincia", status = EstablishmentStatus.Approved,
        characterizationStatus = characterization,
    )

    @Test
    fun `characterization status survives the cache round trip, including not reported`() = runTest {
        val items = listOf(
            establishment("1", CharacterizationStatus.Pending),
            establishment("2", CharacterizationStatus.Complete),
            establishment("3", null),
        )

        store.replaceAll(items)

        assertEquals(items.toSet(), store.getAll().toSet())
    }
}
