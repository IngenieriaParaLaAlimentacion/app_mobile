package ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.edit_characterization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationError
import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationFailure
import ar.com.ingenieriaalimentacion.domain.usecase.establishment.GetEstablishmentCharacterizationUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.establishment.SaveEstablishmentCharacterizationUseCase
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationFormReducer
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditCharacterizationViewModel(
    route: Route.EditCharacterization,
    private val getCharacterization: GetEstablishmentCharacterizationUseCase,
    private val saveCharacterization: SaveEstablishmentCharacterizationUseCase,
) : ViewModel() {

    private val establishmentId = route.establishmentId

    private val _state = MutableStateFlow(EditCharacterizationState(establishmentName = route.establishmentName))
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun onAction(action: EditCharacterizationAction) {
        when (action) {
            is EditCharacterizationAction.Form -> _state.update {
                // The form keeps everything typed; a stale banner just goes away once they edit.
                it.copy(
                    form = CharacterizationFormReducer.reduce(it.form, action.action),
                    saveError = null,
                    hasInvalidFields = false,
                )
            }

            EditCharacterizationAction.SaveClick -> save()
            EditCharacterizationAction.RetryClick -> load()
            EditCharacterizationAction.BackClick ->
                _state.update { it.copy(destination = EditCharacterizationDestination.Back) }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            getCharacterization(establishmentId).fold(
                onSuccess = { profile ->
                    _state.update {
                        it.copy(isLoading = false, form = CharacterizationFormReducer.from(profile))
                    }
                },
                onFailure = { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _state.update { it.copy(isLoading = false, loadError = throwable.toError()) }
                },
            )
        }
    }

    private fun save() {
        val current = _state.value
        if (!current.canEdit) return

        val validated = current.copy(form = CharacterizationFormReducer.showAllErrors(current.form))
        if (!validated.form.isValid) {
            _state.value = validated.copy(hasInvalidFields = true, saveError = null)
            return
        }

        _state.value = validated.copy(isSaving = true, saveError = null, hasInvalidFields = false)
        viewModelScope.launch {
            saveCharacterization(establishmentId, CharacterizationFormReducer.toCharacterization(validated.form)).fold(
                onSuccess = {
                    // Keep isSaving = true until navigation tears down this screen, so the button
                    // doesn't flash back to idle before we pop back.
                    _state.update { it.copy(destination = EditCharacterizationDestination.Saved) }
                },
                onFailure = { throwable ->
                    if (throwable is CancellationException) throw throwable
                    val error = throwable.toError()
                    _state.update {
                        val form = (error as? CharacterizationError.InvalidField)
                            ?.let { invalid -> CharacterizationFormReducer.withServerError(it.form, invalid.field) }
                            ?: it.form
                        it.copy(isSaving = false, saveError = error, form = form)
                    }
                },
            )
        }
    }

    private fun Throwable.toError(): CharacterizationError =
        (this as? CharacterizationFailure)?.error ?: CharacterizationError.Unknown(this)
}
