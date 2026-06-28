package ar.com.ingenieriaalimentacion.domain.usecase

import ar.com.ingenieriaalimentacion.domain.model.profile.AvailableEstablishment
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.PagedEstablishments
import ar.com.ingenieriaalimentacion.domain.model.profile.Profile
import ar.com.ingenieriaalimentacion.domain.model.profile.UpdateProfile
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetAvailableEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyProfileUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.RequestEstablishmentAccessUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.UpdateMyProfileUseCase
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
}
