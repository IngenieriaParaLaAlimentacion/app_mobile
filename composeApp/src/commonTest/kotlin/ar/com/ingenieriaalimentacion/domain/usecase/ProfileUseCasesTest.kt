package ar.com.ingenieriaalimentacion.domain.usecase

import ar.com.ingenieriaalimentacion.domain.model.profile.AvailableEstablishment
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.PagedEstablishments
import ar.com.ingenieriaalimentacion.domain.model.profile.Profile
import ar.com.ingenieriaalimentacion.domain.model.profile.UpdateProfile
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetAvailableEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyProfileUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.RequestEstablishmentAccessUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.SetCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.UpdateMyProfileUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.WithdrawFromEstablishmentUseCase
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileUseCasesTest {

    private val profile = Profile(
        id = "u1", email = "a@b.com", name = "Ana", lastName = "García",
        role = "analyst", establishments = null,
    )

    @Test
    fun `GetMyProfile returns repository result`() = runTest {
        val repo = FakeProfileRepository().apply { meResult = Result.success(profile) }

        val result = GetMyProfileUseCase(repo)()

        assertEquals(profile, result.getOrNull())
        assertEquals(1, repo.getMeCount)
    }

    @Test
    fun `UpdateMyProfile forwards the update`() = runTest {
        val repo = FakeProfileRepository().apply { updateResult = Result.success(profile) }
        val update = UpdateProfile(name = "Nuevo")

        UpdateMyProfileUseCase(repo)(update)

        assertEquals(update, repo.lastUpdate)
    }

    @Test
    fun `GetAvailableEstablishments forwards pagination args`() = runTest {
        val repo = FakeProfileRepository().apply {
            availableResult = Result.success(
                PagedEstablishments(
                    items = listOf(
                        AvailableEstablishment("e1", "Norte", "Calle 1", 0.0, 0.0, "p1", "C", "P"),
                    ),
                    page = 2, totalPages = 3, total = 50,
                ),
            )
        }

        val result = GetAvailableEstablishmentsUseCase(repo)(search = "n", page = 2, pageSize = 20)

        assertEquals(1, result.getOrNull()?.items?.size)
        assertEquals(Triple("n", 2, 20), repo.availableCalls.single())
    }

    @Test
    fun `RequestEstablishmentAccess forwards ids and returns relations`() = runTest {
        val repo = FakeProfileRepository().apply {
            requestResult = Result.success(
                listOf(
                    EstablishmentWithStatus(
                        "e1", "Norte", "Calle 1", 0.0, 0.0, "p1", "C", "P",
                        EstablishmentStatus.Pending,
                    ),
                ),
            )
        }

        val result = RequestEstablishmentAccessUseCase(repo)(listOf("e1"))

        assertEquals(listOf("e1"), repo.lastRequestedIds)
        assertTrue(result.getOrNull()?.first()?.status == EstablishmentStatus.Pending)
    }

    private fun establishment(id: String, status: EstablishmentStatus) = EstablishmentWithStatus(
        id, "Escuela $id", "Calle 1", 0.0, 0.0, "p$id", "Ciudad", "Provincia", status,
    )

    @Test
    fun `GetMyEstablishments filters out withdrawn and revoked relations`() = runTest {
        val repo = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(
                listOf(
                    establishment("e1", EstablishmentStatus.Approved),
                    establishment("e2", EstablishmentStatus.Pending),
                    establishment("e3", EstablishmentStatus.Rejected),
                    establishment("e4", EstablishmentStatus.Withdrawn),
                    establishment("e5", EstablishmentStatus.Revoked),
                ),
            )
        }

        val result = GetMyEstablishmentsUseCase(repo)()

        assertEquals(listOf("e1", "e2", "e3"), result.getOrNull()?.map { it.id })
    }

    @Test
    fun `GetCurrentEstablishment returns null when nothing is stored`() = runTest {
        val repo = FakeProfileRepository().apply { currentEstablishmentId = null }

        val result = GetCurrentEstablishmentUseCase(repo)(
            listOf(establishment("e1", EstablishmentStatus.Approved)),
        )

        assertEquals(null, result)
    }

    @Test
    fun `GetCurrentEstablishment returns the matching approved establishment`() = runTest {
        val repo = FakeProfileRepository().apply { currentEstablishmentId = "e1" }
        val approved = listOf(
            establishment("e1", EstablishmentStatus.Approved),
            establishment("e2", EstablishmentStatus.Approved),
        )

        val result = GetCurrentEstablishmentUseCase(repo)(approved)

        assertEquals("e1", result?.id)
    }

    @Test
    fun `GetCurrentEstablishment returns null when the stored id is no longer approved`() = runTest {
        val repo = FakeProfileRepository().apply { currentEstablishmentId = "revoked" }
        val approved = listOf(establishment("e1", EstablishmentStatus.Approved))

        val result = GetCurrentEstablishmentUseCase(repo)(approved)

        assertEquals(null, result)
    }

    @Test
    fun `SetCurrentEstablishment forwards the establishment's id`() = runTest {
        val repo = FakeProfileRepository()

        SetCurrentEstablishmentUseCase(repo)(establishment("e1", EstablishmentStatus.Approved))

        assertEquals("e1", repo.currentEstablishmentId)
        assertEquals(1, repo.setCurrentEstablishmentIdCount)
    }

    @Test
    fun `WithdrawFromEstablishment forwards the id and returns the repository result`() = runTest {
        val repo = FakeProfileRepository().apply { withdrawResult = Result.success(Unit) }

        val result = WithdrawFromEstablishmentUseCase(repo)("e1")

        assertEquals("e1", repo.lastWithdrawnId)
        assertEquals(1, repo.withdrawCount)
        assertTrue(result.isSuccess)
    }
}
