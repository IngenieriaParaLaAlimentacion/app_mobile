package ar.com.ingenieriaalimentacion.domain.usecase

import ar.com.ingenieriaalimentacion.domain.model.profile.CurrentEstablishmentResult
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.usecase.profile.ResolveCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ResolveCurrentEstablishmentUseCaseTest {

    private fun establishment(id: String, status: EstablishmentStatus) = EstablishmentWithStatus(
        id = id,
        name = "Comedor $id",
        formattedAddress = "Calle 1",
        latitude = 0.0,
        longitude = 0.0,
        placeId = "p-$id",
        city = "Ciudad",
        province = "Provincia",
        status = status,
    )

    private fun approved(id: String) = establishment(id, EstablishmentStatus.Approved)

    private fun pending(id: String) = establishment(id, EstablishmentStatus.Pending)

    @Test
    fun `a stored id present in the approved list resolves to Selected`() = runTest {
        val repo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(listOf(approved("e1"), approved("e2")))
            currentEstablishmentId = "e2"
        }

        val result = ResolveCurrentEstablishmentUseCase(repo)()

        assertEquals("e2", assertIs<CurrentEstablishmentResult.Selected>(result).establishment.id)
    }

    @Test
    fun `no stored id is NoneSelected and carries the options for the picker`() = runTest {
        val repo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(listOf(approved("e1")))
            currentEstablishmentId = null
        }

        val result = assertIs<CurrentEstablishmentResult.NoneSelected>(
            ResolveCurrentEstablishmentUseCase(repo)(),
        )

        assertEquals(listOf("e1"), result.options.map { it.id })
    }

    @Test
    fun `a stored id no longer approved is StaleSelection, not NoneSelected`() = runTest {
        val repo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(listOf(approved("e1")))
            currentEstablishmentId = "gone"
        }

        val result = assertIs<CurrentEstablishmentResult.StaleSelection>(
            ResolveCurrentEstablishmentUseCase(repo)(),
        )

        assertEquals(listOf("e1"), result.options.map { it.id })
    }

    @Test
    fun `a failed establishment list is LoadFailed — never a picker the user cannot use`() = runTest {
        val repo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.failure(IllegalStateException("offline"))
            currentEstablishmentId = "e1"
        }

        assertIs<CurrentEstablishmentResult.LoadFailed>(
            ResolveCurrentEstablishmentUseCase(repo)(),
        )
    }

    @Test
    fun `a pending relation is filtered out before the id is resolved`() = runTest {
        val repo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(listOf(pending("e1")))
            currentEstablishmentId = "e1"
        }

        val result = assertIs<CurrentEstablishmentResult.StaleSelection>(
            ResolveCurrentEstablishmentUseCase(repo)(),
        )

        // The pending relation is not offered as an option either — it is not pickable.
        assertEquals(emptyList(), result.options)
    }

    @Test
    fun `no approved establishments and no stored id is NoneSelected with empty options`() = runTest {
        val repo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(emptyList())
            currentEstablishmentId = null
        }

        val result = assertIs<CurrentEstablishmentResult.NoneSelected>(
            ResolveCurrentEstablishmentUseCase(repo)(),
        )

        assertEquals(emptyList(), result.options)
    }
}
