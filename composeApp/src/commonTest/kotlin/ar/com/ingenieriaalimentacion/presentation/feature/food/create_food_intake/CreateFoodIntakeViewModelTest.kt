package ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_intake

import ar.com.ingenieriaalimentacion.domain.model.food.Food
import ar.com.ingenieriaalimentacion.domain.model.food.FoodIntakeError
import ar.com.ingenieriaalimentacion.domain.model.food.FoodIntakeFailure
import ar.com.ingenieriaalimentacion.domain.model.food.FoodSearchResult
import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.usecase.food.CreateFoodIntakeUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.SearchFoodsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.ResolveCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.fake.FakeConnectivityObserver
import ar.com.ingenieriaalimentacion.fake.FakeFoodCatalogRepository
import ar.com.ingenieriaalimentacion.fake.FakeFoodIntakeRepository
import ar.com.ingenieriaalimentacion.fake.FakeIdGenerator
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class CreateFoodIntakeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val pollo = Food("f-1", "C1", "Pollo, pechuga sin piel, crudo", FoodUnit.Kilogramos, "Carnes")
    private val huevo = Food("f-2", "C2", "Huevo de gallina, entero, crudo", FoodUnit.Unidades, "Huevos")

    private val establishment = EstablishmentWithStatus(
        id = "e-1",
        name = "Comedor Norte",
        formattedAddress = "Calle 1",
        latitude = 0.0,
        longitude = 0.0,
        placeId = "p-1",
        city = "Ciudad",
        province = "Provincia",
        status = EstablishmentStatus.Approved,
    )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private class Fixture(
        val catalog: FakeFoodCatalogRepository,
        val intakes: FakeFoodIntakeRepository,
        val ids: FakeIdGenerator,
        val viewModel: CreateFoodIntakeViewModel,
    )

    private fun fixture(
        online: Boolean = true,
        establishments: List<EstablishmentWithStatus> = listOf(establishment),
        currentEstablishmentId: String? = establishment.id,
        catalogResult: Result<FoodSearchResult> = Result.success(FoodSearchResult(emptyList(), 0)),
    ): Fixture {
        val catalog = FakeFoodCatalogRepository().apply { result = catalogResult }
        val intakes = FakeFoodIntakeRepository()
        val ids = FakeIdGenerator()
        val profile = FakeProfileRepository().apply {
            myEstablishmentsResult = Result.success(establishments)
            this.currentEstablishmentId = currentEstablishmentId
        }
        return Fixture(
            catalog = catalog,
            intakes = intakes,
            ids = ids,
            viewModel = CreateFoodIntakeViewModel(
                searchFoods = SearchFoodsUseCase(catalog),
                createFoodIntake = CreateFoodIntakeUseCase(intakes),
                resolveCurrentEstablishment = ResolveCurrentEstablishmentUseCase(profile),
                connectivity = FakeConnectivityObserver(initial = online),
                idGenerator = ids,
            ),
        )
    }

    /** A `DDMMYYYY` buffer for a date [daysFromToday] away from today. */
    private fun dateDigits(daysFromToday: Int): String {
        val date = Clock.System.now()
            .plus(kotlin.time.Duration.parse("${daysFromToday}d"))
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        return "${date.day.toString().padStart(2, '0')}" +
            "${date.month.number.toString().padStart(2, '0')}" +
            "${date.year}"
    }

    @Test
    fun `rapid typing produces one debounced search`() = runTest(dispatcher) {
        val f = fixture(catalogResult = Result.success(FoodSearchResult(listOf(pollo), 1)))
        advanceUntilIdle()

        f.viewModel.onAction(CreateFoodIntakeAction.SearchQueryChanged("p"))
        f.viewModel.onAction(CreateFoodIntakeAction.SearchQueryChanged("po"))
        f.viewModel.onAction(CreateFoodIntakeAction.SearchQueryChanged("pol"))
        advanceUntilIdle()

        assertEquals(listOf<String?>("pol"), f.catalog.searchCalls.toList())
        assertEquals(listOf(pollo), f.viewModel.state.value.search.results)
    }

    @Test
    fun `a blank query clears the results without calling the catalog`() = runTest(dispatcher) {
        val f = fixture(catalogResult = Result.success(FoodSearchResult(listOf(pollo), 1)))
        advanceUntilIdle()

        f.viewModel.onAction(CreateFoodIntakeAction.SearchQueryChanged("pollo"))
        advanceUntilIdle()
        assertEquals(1, f.catalog.searchCalls.size)

        f.viewModel.onAction(CreateFoodIntakeAction.SearchQueryChanged(""))
        advanceUntilIdle()

        assertEquals(1, f.catalog.searchCalls.size)
        assertTrue(f.viewModel.state.value.search.results.isEmpty())
    }

    @Test
    fun `picking a food already loaded does not add a second line`() = runTest(dispatcher) {
        val f = fixture()
        advanceUntilIdle()

        f.viewModel.onAction(CreateFoodIntakeAction.FoodPicked(pollo))
        f.viewModel.onAction(CreateFoodIntakeAction.FoodPicked(pollo))
        advanceUntilIdle()

        assertEquals(1, f.viewModel.state.value.lines.size)
    }

    @Test
    fun `the dropdown reopens for the next search after a pick`() = runTest(dispatcher) {
        val f = fixture(catalogResult = Result.success(FoodSearchResult(listOf(pollo), 1)))
        advanceUntilIdle()

        f.viewModel.onAction(CreateFoodIntakeAction.SearchFocusChanged(true))
        f.viewModel.onAction(CreateFoodIntakeAction.SearchQueryChanged("pollo"))
        advanceUntilIdle()
        assertTrue(f.viewModel.state.value.search.isExpanded)

        f.viewModel.onAction(CreateFoodIntakeAction.FoodPicked(pollo))
        assertFalse(f.viewModel.state.value.search.isExpanded, "the pick collapses the dropdown")

        // Picking does not move focus out of the field, so typing again must reopen it — clearing
        // isFocused here would strand the dropdown closed until a blur/refocus.
        f.viewModel.onAction(CreateFoodIntakeAction.SearchQueryChanged("huevo"))
        assertTrue(f.viewModel.state.value.search.isExpanded)
    }

    @Test
    fun `a unidades line rejects a decimal and accepts a whole number`() = runTest(dispatcher) {
        val f = fixture()
        advanceUntilIdle()
        f.viewModel.onAction(CreateFoodIntakeAction.FoodPicked(huevo))

        // The integer sanitizer strips the separator outright, so "12.5" becomes "125" — a
        // decimal never reaches the field in the first place.
        f.viewModel.onAction(CreateFoodIntakeAction.LineQuantityChanged(huevo.id, "12.5"))
        assertEquals("125", f.viewModel.state.value.lines.single().quantity.value)

        f.viewModel.onAction(CreateFoodIntakeAction.LineQuantityChanged(huevo.id, "12"))
        val line = f.viewModel.state.value.lines.single()
        assertEquals("12", line.quantity.value)
        assertTrue(line.quantity.isValid)
    }

    @Test
    fun `a unidades line with a decimal value is invalid`() = runTest(dispatcher) {
        val f = fixture()
        advanceUntilIdle()
        f.viewModel.onAction(CreateFoodIntakeAction.FoodPicked(huevo))
        f.viewModel.onAction(CreateFoodIntakeAction.LineQuantityFocusChanged(huevo.id, true))
        f.viewModel.onAction(CreateFoodIntakeAction.LineQuantityFocusChanged(huevo.id, false))

        // Zero is out of range for every unit, so a blurred empty-but-required line stays invalid.
        assertFalse(f.viewModel.state.value.lines.single().quantity.isValid)
    }

    @Test
    fun `a kilogramos line accepts a comma and normalizes it to a dot in the model`() = runTest(dispatcher) {
        val f = fixture()
        advanceUntilIdle()
        f.viewModel.onAction(CreateFoodIntakeAction.FoodPicked(pollo))
        f.viewModel.onAction(CreateFoodIntakeAction.LineQuantityChanged(pollo.id, "12,5"))

        assertEquals("12.5", f.viewModel.state.value.lines.single().quantity.value)

        f.viewModel.onAction(CreateFoodIntakeAction.Submit)
        advanceUntilIdle()

        assertEquals("12.5", f.intakes.created.single().items.single().quantity)
        assertEquals(establishment.id, f.intakes.created.single().establishmentId)
    }

    @Test
    fun `submit with no lines is a no-op`() = runTest(dispatcher) {
        val f = fixture()
        advanceUntilIdle()
        assertFalse(f.viewModel.state.value.canSubmit)
        f.viewModel.onAction(CreateFoodIntakeAction.Submit)
        advanceUntilIdle()

        assertTrue(f.intakes.created.isEmpty())
    }

    @Test
    fun `the intake id is stable across a failed submit and its retry`() = runTest(dispatcher) {
        val f = fixture()
        f.intakes.result = Result.failure(FoodIntakeFailure(FoodIntakeError.Network, null))
        advanceUntilIdle()
        f.viewModel.onAction(CreateFoodIntakeAction.FoodPicked(pollo))
        f.viewModel.onAction(CreateFoodIntakeAction.LineQuantityChanged(pollo.id, "10"))

        f.viewModel.onAction(CreateFoodIntakeAction.Submit)
        advanceUntilIdle()
        assertEquals("No se pudo enviar. Revisá tu conexión.", f.viewModel.state.value.submitError)

        f.viewModel.onAction(CreateFoodIntakeAction.Submit)
        advanceUntilIdle()

        assertEquals(2, f.intakes.created.size)
        assertEquals(f.intakes.created[0].id, f.intakes.created[1].id)
        assertEquals(1, f.viewModel.state.value.lines.size)
        assertNull(f.viewModel.state.value.destination)
    }

    @Test
    fun `a successful submit emits Saved and preserves the form until pop`() = runTest(dispatcher) {
        val f = fixture()
        advanceUntilIdle()
        val firstId = f.viewModel.state.value.intakeId
        f.viewModel.onAction(CreateFoodIntakeAction.FoodPicked(pollo))
        f.viewModel.onAction(CreateFoodIntakeAction.LineQuantityChanged(pollo.id, "10"))
        f.viewModel.onAction(CreateFoodIntakeAction.CommentsChanged("Donación"))

        f.viewModel.onAction(CreateFoodIntakeAction.Submit)
        advanceUntilIdle()

        val state = f.viewModel.state.value
        assertEquals(firstId, f.intakes.created.single().id)
        assertEquals(firstId, state.intakeId)
        assertEquals(listOf(pollo.id), state.lines.map { it.food.id })
        assertEquals("Donación", state.comments.value)
        assertEquals(CreateFoodIntakeDestination.Saved, state.destination)
        assertEquals(establishment, state.selectedEstablishment)
    }

    @Test
    fun `offline blocks submit`() = runTest(dispatcher) {
        val f = fixture(online = false)
        advanceUntilIdle()
        f.viewModel.onAction(CreateFoodIntakeAction.FoodPicked(pollo))
        f.viewModel.onAction(CreateFoodIntakeAction.LineQuantityChanged(pollo.id, "10"))

        assertFalse(f.viewModel.state.value.isOnline)
        assertFalse(f.viewModel.state.value.canSubmit)

        f.viewModel.onAction(CreateFoodIntakeAction.Submit)
        advanceUntilIdle()
        assertTrue(f.intakes.created.isEmpty())
    }

    @Test
    fun `a future date blocks submit and shows the error`() = runTest(dispatcher) {
        val f = fixture()
        advanceUntilIdle()
        f.viewModel.onAction(CreateFoodIntakeAction.FoodPicked(pollo))
        f.viewModel.onAction(CreateFoodIntakeAction.LineQuantityChanged(pollo.id, "10"))
        f.viewModel.onAction(CreateFoodIntakeAction.ReceivedAtChanged(dateDigits(daysFromToday = 1)))

        f.viewModel.onAction(CreateFoodIntakeAction.Submit)
        advanceUntilIdle()

        assertTrue(f.intakes.created.isEmpty())
        assertEquals("La fecha no puede ser futura.", f.viewModel.state.value.receivedAt.displayError)
    }

    @Test
    fun `a past date is accepted`() = runTest(dispatcher) {
        val f = fixture()
        advanceUntilIdle()
        f.viewModel.onAction(CreateFoodIntakeAction.FoodPicked(pollo))
        f.viewModel.onAction(CreateFoodIntakeAction.LineQuantityChanged(pollo.id, "10"))
        f.viewModel.onAction(CreateFoodIntakeAction.ReceivedAtChanged(dateDigits(daysFromToday = -7)))

        f.viewModel.onAction(CreateFoodIntakeAction.Submit)
        advanceUntilIdle()

        assertEquals(1, f.intakes.created.size)
        assertNull(f.viewModel.state.value.receivedAt.displayError)
    }

    @Test
    fun `the date is pre-filled with today and the establishment pre-selected`() = runTest(dispatcher) {
        val f = fixture()
        advanceUntilIdle()

        val state = f.viewModel.state.value
        assertEquals(dateDigits(daysFromToday = 0), state.receivedAt.value)
        // The stored global establishment is the only accepted scope.
        assertEquals(establishment, state.selectedEstablishment)
        assertNotNull(state.intakeId.takeIf { it.isNotBlank() })
    }

    @Test
    fun `missing global establishment shows profile error`() = runTest(dispatcher) {
        val f = fixture(currentEstablishmentId = null)
        advanceUntilIdle()

        assertNull(f.viewModel.state.value.selectedEstablishment)
        assertTrue(f.viewModel.state.value.profileError!!.contains("Inicio"))
    }

    @Test
    fun `stale global establishment shows profile error and cannot submit`() = runTest(dispatcher) {
        val f = fixture(currentEstablishmentId = "revoked")
        advanceUntilIdle()

        assertNull(f.viewModel.state.value.selectedEstablishment)
        assertFalse(f.viewModel.state.value.canSubmit)
        assertTrue(f.intakes.created.isEmpty())
    }

    @Test
    fun `comments longer than the limit block submit`() = runTest(dispatcher) {
        val f = fixture()
        advanceUntilIdle()
        f.viewModel.onAction(CreateFoodIntakeAction.FoodPicked(pollo))
        f.viewModel.onAction(CreateFoodIntakeAction.LineQuantityChanged(pollo.id, "10"))
        f.viewModel.onAction(CreateFoodIntakeAction.CommentsChanged("x".repeat(MAX_COMMENTS + 1)))

        f.viewModel.onAction(CreateFoodIntakeAction.Submit)
        advanceUntilIdle()

        assertTrue(f.intakes.created.isEmpty())
        assertEquals("Máximo $MAX_COMMENTS caracteres.", f.viewModel.state.value.comments.displayError)
    }

    @Test
    fun `a search failure surfaces as a retryable dropdown error`() = runTest(dispatcher) {
        val f = fixture(catalogResult = Result.failure(RuntimeException("boom")))
        advanceUntilIdle()

        f.viewModel.onAction(CreateFoodIntakeAction.SearchQueryChanged("pollo"))
        advanceUntilIdle()
        assertTrue(f.viewModel.state.value.search.error)

        f.catalog.result = Result.success(FoodSearchResult(listOf(pollo), 1))
        f.viewModel.onAction(CreateFoodIntakeAction.SearchRetry)
        advanceUntilIdle()

        assertFalse(f.viewModel.state.value.search.error)
        assertEquals(listOf(pollo), f.viewModel.state.value.search.results)
    }
}
