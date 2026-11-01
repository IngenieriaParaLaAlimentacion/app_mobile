package ar.com.ingenieriaalimentacion.domain.usecase

import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectPage
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary
import ar.com.ingenieriaalimentacion.domain.usecase.subject.GetSubjectsUseCase
import ar.com.ingenieriaalimentacion.fake.FakeSubjectQueryRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class GetSubjectsUseCaseTest {

    private val ana = SubjectSummary("1", 30111222, "Ana", "Pérez", Sex.Female, birthDate = kotlinx.datetime.LocalDate(1990, 1, 1))

    @Test
    fun `returns page from repository`() = runTest {
        val repo = FakeSubjectQueryRepository().apply {
            pages = mapOf(1 to SubjectPage(items = listOf(ana), page = 1, totalPages = 2))
        }
        val useCase = GetSubjectsUseCase(repo)

        val result = useCase(query = "an", page = 1)

        assertEquals(listOf(ana), result.getOrNull()?.items)
        assertEquals("an", repo.lastSearch)
    }

    @Test
    fun `defaults page size to twenty`() = runTest {
        val repo = FakeSubjectQueryRepository()
        val useCase = GetSubjectsUseCase(repo)

        useCase(query = null, page = 1)

        assertEquals(GetSubjectsUseCase.DEFAULT_PAGE_SIZE, 20)
    }

    @Test
    fun `propagates repository failure`() = runTest {
        val repo = FakeSubjectQueryRepository().apply { error = IllegalStateException("boom") }
        val useCase = GetSubjectsUseCase(repo)

        val result = useCase(query = null, page = 1)

        assertTrue(result.isFailure)
    }
}
