package ar.com.ingenieriaalimentacion.presentation.feature.food.receipt_food_intake

import ar.com.ingenieriaalimentacion.domain.model.food.Food
import ar.com.ingenieriaalimentacion.domain.model.food.FoodIntakeOrigin
import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit
import ar.com.ingenieriaalimentacion.domain.model.food.NewFoodIntake
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptError
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptFailure
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptLine
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptPhoto
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptProposal
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptSource
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.repository.ReceiptIntakeRepository
import ar.com.ingenieriaalimentacion.domain.usecase.food.ConfirmReceiptIntakeUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.ProposeReceiptIntakeUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.ResolveCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.fake.FakeConnectivityObserver
import ar.com.ingenieriaalimentacion.fake.FakeFoodCatalogRepository
import ar.com.ingenieriaalimentacion.fake.FakeIdGenerator
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptFoodIntakeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val food = Food("f1", "A1", "Arroz", FoodUnit.Kilogramos, "Cereales")
    private val photo = processedReceiptPhoto(byteArrayOf(
        'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
        0, 0, 0, 0, 'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(),
    ))
    private val establishment = EstablishmentWithStatus("e1", "Comedor", "Calle 1", 0.0, 0.0,
        "p1", "Ciudad", "Provincia", EstablishmentStatus.Approved)

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private class ReceiptRepo : ReceiptIntakeRepository {
        var proposal: Result<ReceiptProposal> = Result.success(ReceiptProposal("e1", emptyList(), null))
        var proposalWait: CompletableDeferred<Unit>? = null
        var confirmation: Result<Unit> = Result.success(Unit)
        val confirms = mutableListOf<Triple<NewFoodIntake, ReceiptSource, ReceiptPhoto>>()
        val proposals = mutableListOf<ReceiptPhoto>()
        override suspend fun propose(establishmentId: String, photo: ReceiptPhoto): Result<ReceiptProposal> {
            proposals += photo
            proposalWait?.await()
            return proposal
        }
        override suspend fun confirm(intake: NewFoodIntake, source: ReceiptSource,
            photo: ReceiptPhoto): Result<Unit> {
            confirms += Triple(intake, source, photo)
            return confirmation
        }
    }

    private data class Fixture(val vm: ReceiptFoodIntakeViewModel, val repo: ReceiptRepo,
        val catalog: FakeFoodCatalogRepository, val ids: FakeIdGenerator)

    private fun fixture(online: Boolean = true): Fixture {
        val repo = ReceiptRepo()
        val catalog = FakeFoodCatalogRepository().apply { foodResult = Result.success(food) }
        val ids = FakeIdGenerator()
        val profile = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(listOf(establishment))
            currentEstablishmentId = establishment.id
        }
        val vm = ReceiptFoodIntakeViewModel(ProposeReceiptIntakeUseCase(repo),
            ConfirmReceiptIntakeUseCase(repo), catalog, ResolveCurrentEstablishmentUseCase(profile),
            FakeConnectivityObserver(online), ids)
        return Fixture(vm, repo, catalog, ids)
    }

    private fun ReceiptFoodIntakeViewModel.pick() =
        onAction(ReceiptFoodIntakeAction.PhotoPicked(Result.success(photo)))

    private fun selectChoices(vm: ReceiptFoodIntakeViewModel, recurring: Boolean = false) {
        vm.onAction(ReceiptFoodIntakeAction.OriginSelected(FoodIntakeOrigin.Foundation))
        vm.onAction(ReceiptFoodIntakeAction.RecurrenceSelected(recurring))
    }

    private fun matchedLine() = ReceiptLine("Arroz", "f1", null, null, "500", "g")

    @Test fun safePrefillStillRequiresExplicitReview() = runTest(dispatcher) {
        val f = fixture()
        f.repo.proposal = Result.success(ReceiptProposal("e1", listOf(matchedLine()), null))
        advanceUntilIdle()
        f.vm.pick()
        f.vm.onAction(ReceiptFoodIntakeAction.Analyze)
        advanceUntilIdle()
        val line = f.vm.state.value.lines.single()
        assertEquals("0.5", line.quantity.value)
        assertFalse(line.reviewed)
        f.vm.onAction(ReceiptFoodIntakeAction.Submit)
        assertTrue(f.repo.confirms.isEmpty())
        f.vm.onAction(ReceiptFoodIntakeAction.LineConfirmed(line.id))
        selectChoices(f.vm, recurring = true)
        f.vm.onAction(ReceiptFoodIntakeAction.Submit)
        advanceUntilIdle()
        assertEquals(1, f.repo.confirms.size)
        assertSame(f.repo.proposals.single(), f.repo.confirms.single().third)
        assertEquals("image/webp", f.repo.confirms.single().third.contentType)
        assertEquals("recibo.webp", f.repo.confirms.single().third.fileName)
        assertEquals(FoodIntakeOrigin.Foundation, f.repo.confirms.single().first.originType)
        assertEquals(true, f.repo.confirms.single().first.isRecurring)
        assertEquals(ReceiptFoodIntakeDestination.Saved, f.vm.state.value.destination)
    }

    @Test fun reviewRequiresExplicitOriginAndRecurrence() = runTest(dispatcher) {
        val f = fixture()
        f.repo.proposal = Result.success(ReceiptProposal("e1", listOf(matchedLine()), null))
        advanceUntilIdle(); f.vm.pick(); f.vm.onAction(ReceiptFoodIntakeAction.Analyze); advanceUntilIdle()
        assertNull(f.vm.state.value.originType)
        assertNull(f.vm.state.value.isRecurring)
        f.vm.onAction(ReceiptFoodIntakeAction.LineConfirmed(f.vm.state.value.lines.single().id))
        f.vm.onAction(ReceiptFoodIntakeAction.Submit)
        assertTrue(f.repo.confirms.isEmpty())
        assertEquals("Seleccioná el origen del alimento.", f.vm.state.value.originError)
        assertEquals("Seleccioná el tipo de aporte.", f.vm.state.value.recurrenceError)
        f.vm.onAction(ReceiptFoodIntakeAction.OriginSelected(FoodIntakeOrigin.Foundation))
        assertNull(f.vm.state.value.originError)
        f.vm.onAction(ReceiptFoodIntakeAction.RecurrenceSelected(false))
        assertNull(f.vm.state.value.recurrenceError)
        f.vm.onAction(ReceiptFoodIntakeAction.Submit); advanceUntilIdle()
        assertEquals(false, f.repo.confirms.single().first.isRecurring)
    }

    @Test fun uncertainFailureFreezesRequestAndPhotoAcrossRetry() = runTest(dispatcher) {
        val f = fixture()
        f.repo.proposal = Result.success(ReceiptProposal("e1", listOf(matchedLine()), null))
        f.repo.confirmation = Result.failure(ReceiptFailure(ReceiptError.Retryable))
        advanceUntilIdle(); f.vm.pick(); f.vm.onAction(ReceiptFoodIntakeAction.Analyze); advanceUntilIdle()
        val id = f.vm.state.value.lines.single().id
        f.vm.onAction(ReceiptFoodIntakeAction.LineConfirmed(id))
        selectChoices(f.vm)
        f.vm.onAction(ReceiptFoodIntakeAction.Submit); advanceUntilIdle()
        f.vm.onAction(ReceiptFoodIntakeAction.QuantityChanged(id, "9"))
        f.vm.onAction(ReceiptFoodIntakeAction.OriginSelected(FoodIntakeOrigin.Church))
        f.vm.onAction(ReceiptFoodIntakeAction.RecurrenceSelected(true))
        assertEquals("0.5", f.vm.state.value.lines.single().quantity.value)
        assertEquals(FoodIntakeOrigin.Foundation, f.vm.state.value.originType)
        assertEquals(false, f.vm.state.value.isRecurring)
        f.repo.confirmation = Result.success(Unit)
        f.vm.onAction(ReceiptFoodIntakeAction.RetrySubmit); advanceUntilIdle()
        assertEquals(2, f.repo.confirms.size)
        assertEquals(f.repo.confirms[0].first, f.repo.confirms[1].first)
        assertEquals(FoodIntakeOrigin.Foundation, f.repo.confirms[1].first.originType)
        assertEquals(false, f.repo.confirms[1].first.isRecurring)
        assertSame(f.repo.proposals.single(), f.repo.confirms[0].third)
        assertSame(f.repo.confirms[0].third, f.repo.confirms[1].third)
        assertEquals(1, f.ids.count)
    }

    @Test fun emptyExtractionCanUseManualFallbackWithSamePhoto() = runTest(dispatcher) {
        val f = fixture()
        advanceUntilIdle(); f.vm.pick(); f.vm.onAction(ReceiptFoodIntakeAction.Analyze); advanceUntilIdle()
        assertEquals(ReceiptSource.ManualFallback, f.vm.state.value.source)
        assertNull(f.vm.state.value.originType)
        assertNull(f.vm.state.value.isRecurring)
        f.vm.onAction(ReceiptFoodIntakeAction.AddLine)
        val id = f.vm.state.value.lines.single().id
        assertNull(f.vm.state.value.lines.single().description)
        f.vm.onAction(ReceiptFoodIntakeAction.FoodPicked(id, food)); advanceUntilIdle()
        f.vm.onAction(ReceiptFoodIntakeAction.QuantityChanged(id, "2"))
        f.vm.onAction(ReceiptFoodIntakeAction.LineConfirmed(id))
        selectChoices(f.vm)
        f.vm.onAction(ReceiptFoodIntakeAction.Submit); advanceUntilIdle()
        assertEquals(ReceiptSource.ManualFallback, f.repo.confirms.single().second)
        assertEquals(false, f.repo.confirms.single().first.isRecurring)
        assertSame(photo, f.repo.confirms.single().third)
    }

    @Test fun changingSearchFieldIgnoresBlurFromPreviousLine() = runTest(dispatcher) {
        val f = fixture()
        f.repo.proposal = Result.success(ReceiptProposal("e1", listOf(matchedLine(), matchedLine()), null))
        advanceUntilIdle(); f.vm.pick(); f.vm.onAction(ReceiptFoodIntakeAction.Analyze); advanceUntilIdle()
        val (first, second) = f.vm.state.value.lines
        f.vm.onAction(ReceiptFoodIntakeAction.FoodSearchFocusChanged(first.id, true))
        f.vm.onAction(ReceiptFoodIntakeAction.FoodSearchChanged(first.id, "arro"))
        f.vm.onAction(ReceiptFoodIntakeAction.FoodSearchFocusChanged(second.id, true))
        f.vm.onAction(ReceiptFoodIntakeAction.FoodSearchFocusChanged(first.id, false))
        assertEquals(second.id, f.vm.state.value.searchLineId)
        assertEquals("", f.vm.state.value.search.query)
        assertTrue(f.vm.state.value.search.isFocused)
    }

    @Test fun cancelledProposalCannotReplacePreview() = runTest(dispatcher) {
        val f = fixture()
        f.repo.proposalWait = CompletableDeferred()
        f.repo.proposal = Result.success(ReceiptProposal("e1", listOf(matchedLine()), null))
        advanceUntilIdle(); f.vm.pick(); f.vm.onAction(ReceiptFoodIntakeAction.Analyze)
        testScheduler.runCurrent()
        f.vm.onAction(ReceiptFoodIntakeAction.CancelAnalysis)
        f.repo.proposalWait!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(ReceiptStep.Preview, f.vm.state.value.step)
        assertTrue(f.vm.state.value.lines.isEmpty())
    }

    @Test fun failedHydrationLeavesLineUnresolved() = runTest(dispatcher) {
        val f = fixture()
        f.catalog.foodResult = Result.failure(IllegalStateException("deleted"))
        f.repo.proposal = Result.success(ReceiptProposal("e1", listOf(matchedLine()), null))
        advanceUntilIdle(); f.vm.pick(); f.vm.onAction(ReceiptFoodIntakeAction.Analyze); advanceUntilIdle()
        assertNull(f.vm.state.value.lines.single().food)
        assertEquals("", f.vm.state.value.lines.single().quantity.value)
    }

    @Test fun duplicatePrintedFoodsRemainSeparateRows() = runTest(dispatcher) {
        val f = fixture()
        f.repo.proposal = Result.success(ReceiptProposal("e1", listOf(matchedLine(), matchedLine()), null))
        advanceUntilIdle(); f.vm.pick(); f.vm.onAction(ReceiptFoodIntakeAction.Analyze); advanceUntilIdle()
        val rows = f.vm.state.value.lines
        assertEquals(2, rows.size)
        assertTrue(rows[0].id != rows[1].id)
        f.vm.onAction(ReceiptFoodIntakeAction.LineRemoved(rows[0].id))
        assertEquals(rows[1].id, f.vm.state.value.lines.single().id)
    }

    @Test fun terminalFailureRequiresRevisionWithNewUuid() = runTest(dispatcher) {
        val f = fixture()
        f.repo.proposal = Result.success(ReceiptProposal("e1", listOf(matchedLine()), null))
        f.repo.confirmation = Result.failure(ReceiptFailure(ReceiptError.FoodNotFound))
        advanceUntilIdle(); f.vm.pick(); f.vm.onAction(ReceiptFoodIntakeAction.Analyze); advanceUntilIdle()
        val id = f.vm.state.value.lines.single().id
        f.vm.onAction(ReceiptFoodIntakeAction.LineConfirmed(id))
        selectChoices(f.vm)
        f.vm.onAction(ReceiptFoodIntakeAction.Submit); advanceUntilIdle()
        assertTrue(f.vm.state.value.submissionLocked)
        f.vm.onAction(ReceiptFoodIntakeAction.ReviseAfterFailure)
        assertFalse(f.vm.state.value.submissionLocked)
        assertEquals("id-2", f.vm.state.value.intakeId)
        assertEquals(FoodIntakeOrigin.Foundation, f.vm.state.value.originType)
        assertEquals(false, f.vm.state.value.isRecurring)
    }

    @Test fun offlineSubmitDoesNotSend() = runTest(dispatcher) {
        val f = fixture(online = false)
        advanceUntilIdle(); f.vm.pick(); f.vm.onAction(ReceiptFoodIntakeAction.ManualFallback)
        f.vm.onAction(ReceiptFoodIntakeAction.Submit); advanceUntilIdle()
        assertTrue(f.repo.confirms.isEmpty())
    }

    @Test fun processingFailureNeverSendsSourceBytes() = runTest(dispatcher) {
        val f = fixture()
        advanceUntilIdle()
        f.vm.onAction(ReceiptFoodIntakeAction.PhotoPicked(
            Result.failure(ReceiptFailure(ReceiptError.SourceTooLarge))))
        f.vm.onAction(ReceiptFoodIntakeAction.Analyze)
        advanceUntilIdle()
        assertNull(f.vm.previewPhoto())
        assertTrue(f.repo.proposals.isEmpty())
        assertTrue(f.repo.confirms.isEmpty())
        assertEquals("La imagen es demasiado grande para procesarla. Elegí otra foto.",
            f.vm.state.value.error)
    }

    @Test fun unmatchedOrInvalidLinesCannotBeConfirmed() = runTest(dispatcher) {
        val f = fixture()
        f.repo.proposal = Result.success(
            ReceiptProposal("e1", listOf(matchedLine().copy(matchedFoodId = null)), null)
        )
        advanceUntilIdle()
        f.vm.pick()
        f.vm.onAction(ReceiptFoodIntakeAction.Analyze)
        advanceUntilIdle()
        val id = f.vm.state.value.lines.single().id
        selectChoices(f.vm)
        f.vm.onAction(ReceiptFoodIntakeAction.LineConfirmed(id))
        f.vm.onAction(ReceiptFoodIntakeAction.Submit)
        assertFalse(f.vm.state.value.lines.single().reviewed)
        assertTrue(f.repo.confirms.isEmpty())
        f.vm.onAction(ReceiptFoodIntakeAction.FoodPicked(id, food))
        advanceUntilIdle()
        f.vm.onAction(ReceiptFoodIntakeAction.QuantityChanged(id, "0"))
        f.vm.onAction(ReceiptFoodIntakeAction.LineConfirmed(id))
        f.vm.onAction(ReceiptFoodIntakeAction.Submit)
        assertFalse(f.vm.state.value.lines.single().reviewed)
        assertTrue(f.repo.confirms.isEmpty())
    }

    @Test fun editingFoodOrQuantityInvalidatesReview() = runTest(dispatcher) {
        val f = fixture()
        f.repo.proposal = Result.success(ReceiptProposal("e1", listOf(matchedLine()), null))
        advanceUntilIdle()
        f.vm.pick()
        f.vm.onAction(ReceiptFoodIntakeAction.Analyze)
        advanceUntilIdle()
        val id = f.vm.state.value.lines.single().id
        f.vm.onAction(ReceiptFoodIntakeAction.LineConfirmed(id))
        assertTrue(f.vm.state.value.lines.single().reviewed)
        f.vm.onAction(ReceiptFoodIntakeAction.QuantityChanged(id, "2"))
        assertFalse(f.vm.state.value.lines.single().reviewed)
        f.vm.onAction(ReceiptFoodIntakeAction.LineConfirmed(id))
        assertTrue(f.vm.state.value.lines.single().reviewed)
        f.vm.onAction(ReceiptFoodIntakeAction.FoodPicked(id, food))
        advanceUntilIdle()
        assertFalse(f.vm.state.value.lines.single().reviewed)
    }

}
