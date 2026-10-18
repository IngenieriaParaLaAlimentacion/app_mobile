package ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.edit_characterization

import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationError
import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationFailure
import ar.com.ingenieriaalimentacion.domain.model.establishment.EstablishmentCharacterization
import ar.com.ingenieriaalimentacion.domain.model.establishment.Meal
import ar.com.ingenieriaalimentacion.domain.model.establishment.Premises
import ar.com.ingenieriaalimentacion.domain.usecase.establishment.GetEstablishmentCharacterizationUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.establishment.SaveEstablishmentCharacterizationUseCase
import ar.com.ingenieriaalimentacion.fake.FakeEstablishmentCharacterizationRepository
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationCopy
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationFormAction
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationMultiField
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationTextField
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditCharacterizationViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val characterizations = FakeEstablishmentCharacterizationRepository()
    private val profile = FakeProfileRepository()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = EditCharacterizationViewModel(
        route = Route.EditCharacterization(establishmentId = "e1", establishmentName = "Comedor Norte"),
        getCharacterization = GetEstablishmentCharacterizationUseCase(characterizations),
        saveCharacterization = SaveEstablishmentCharacterizationUseCase(characterizations, profile),
    )

    private fun failure(error: CharacterizationError) = Result.failure<Nothing>(CharacterizationFailure(error, null))

    private fun EditCharacterizationViewModel.form(action: CharacterizationFormAction) =
        onAction(EditCharacterizationAction.Form(action))

    @Test
    fun `loads and prefills the stored ficha`() = runTest(dispatcher) {
        characterizations.getResult = Result.success(
            EstablishmentCharacterization(district = "La Matanza", meals = setOf(Meal.Lunch)),
        )
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("e1", characterizations.lastGetId)
        assertFalse(state.isLoading)
        assertEquals("La Matanza", state.form.text(CharacterizationTextField.District).value)
        assertEquals(setOf("lunch"), state.form.selected(CharacterizationMultiField.Meals))
    }

    @Test
    fun `a load failure can be retried`() = runTest(dispatcher) {
        characterizations.getResult = failure(CharacterizationError.Network)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(CharacterizationError.Network, vm.state.value.loadError)

        characterizations.getResult = Result.success(EstablishmentCharacterization.EMPTY)
        vm.onAction(EditCharacterizationAction.RetryClick)
        advanceUntilIdle()

        assertNull(vm.state.value.loadError)
        assertEquals(2, characterizations.getCount)
    }

    @Test
    fun `saving sends the whole profile and navigates back as saved`() = runTest(dispatcher) {
        characterizations.getResult = Result.success(EstablishmentCharacterization(premises = Premises.Street))
        val vm = viewModel()
        advanceUntilIdle()

        vm.form(CharacterizationFormAction.OptionToggled(CharacterizationMultiField.Meals, "dinner"))
        vm.onAction(EditCharacterizationAction.SaveClick)
        advanceUntilIdle()

        assertEquals("e1", characterizations.lastReplaceId)
        assertEquals(
            EstablishmentCharacterization(premises = Premises.Street, meals = setOf(Meal.Dinner)),
            characterizations.lastReplaced,
        )
        assertEquals(1, profile.refreshCount)
        assertEquals(EditCharacterizationDestination.Saved, vm.state.value.destination)
    }

    @Test
    fun `client-side errors block the save and are all shown`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.form(CharacterizationFormAction.TextChanged(CharacterizationTextField.ContactEmail, "no-es-mail"))
        vm.onAction(EditCharacterizationAction.SaveClick)
        advanceUntilIdle()

        assertEquals(0, characterizations.replaceCount)
        assertTrue(vm.state.value.hasInvalidFields)
        assertEquals(
            CharacterizationCopy.INVALID_EMAIL,
            vm.state.value.form.displayError(CharacterizationTextField.ContactEmail),
        )
    }

    @Test
    fun `a 422 flags the reported field and keeps what was typed`() = runTest(dispatcher) {
        characterizations.replaceResult = failure(CharacterizationError.InvalidField("district"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.form(CharacterizationFormAction.TextChanged(CharacterizationTextField.District, "Moreno"))
        vm.onAction(EditCharacterizationAction.SaveClick)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isSaving)
        assertNull(state.destination)
        assertEquals(INVALID_FIELDS_MESSAGE, state.saveError?.toUserMessage())
        assertEquals("Moreno", state.form.text(CharacterizationTextField.District).value)
        assertEquals(CharacterizationCopy.SERVER_INVALID, state.form.displayError(CharacterizationTextField.District))
        assertEquals(0, profile.refreshCount)
    }

    @Test
    fun `a 403 explains the access is no longer approved`() = runTest(dispatcher) {
        characterizations.replaceResult = failure(CharacterizationError.Forbidden)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onAction(EditCharacterizationAction.SaveClick)
        advanceUntilIdle()

        assertEquals(
            "No tenés acceso aprobado a este establecimiento.",
            vm.state.value.saveError?.toUserMessage(),
        )
    }
}
