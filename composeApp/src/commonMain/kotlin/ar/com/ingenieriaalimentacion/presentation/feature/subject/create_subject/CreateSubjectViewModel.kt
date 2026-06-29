package ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.core.validation.Rules
import ar.com.ingenieriaalimentacion.core.validation.Validator
import ar.com.ingenieriaalimentacion.core.validation.onChange
import ar.com.ingenieriaalimentacion.core.validation.onFocus
import ar.com.ingenieriaalimentacion.core.validation.showError
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.domain.model.subject.NewSubject
import ar.com.ingenieriaalimentacion.domain.usecase.subject.CreateSubjectUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class CreateSubjectViewModel(
    private val createSubject: CreateSubjectUseCase,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateSubjectState())
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        _state.value,
    )

    fun onAction(action: CreateSubjectAction) {
        when (action) {
            is CreateSubjectAction.DniChanged ->
                _state.update {
                    it.copy(
                        dni = it.dni.onChange(action.value.filter(Char::isDigit), dniValidator),
                        successMessage = null,
                    )
                }

            is CreateSubjectAction.DniFocusChanged ->
                _state.update { it.copy(dni = it.dni.onFocus(action.focused, dniValidator)) }

            is CreateSubjectAction.SexChanged ->
                _state.update { it.copy(sex = action.value, sexError = null, successMessage = null) }

            is CreateSubjectAction.NameChanged ->
                _state.update {
                    it.copy(
                        name = it.name.onChange(action.value, nameValidator),
                        successMessage = null,
                    )
                }

            is CreateSubjectAction.NameFocusChanged ->
                _state.update { it.copy(name = it.name.onFocus(action.focused, nameValidator)) }

            is CreateSubjectAction.LastNameChanged ->
                _state.update {
                    it.copy(
                        lastName = it.lastName.onChange(action.value, lastNameValidator),
                        successMessage = null,
                    )
                }

            is CreateSubjectAction.LastNameFocusChanged ->
                _state.update { it.copy(lastName = it.lastName.onFocus(action.focused, lastNameValidator)) }

            is CreateSubjectAction.BirthDateChanged ->
                _state.update {
                    it.copy(
                        birthDate = it.birthDate.onChange(
                            action.value.filter(Char::isDigit).take(8),
                            birthDateValidator,
                        ),
                        successMessage = null,
                    )
                }

            is CreateSubjectAction.BirthDateFocusChanged ->
                _state.update { it.copy(birthDate = it.birthDate.onFocus(action.focused, birthDateValidator)) }

            is CreateSubjectAction.IsRuralChanged ->
                _state.update { it.copy(isRural = action.value, successMessage = null) }

            is CreateSubjectAction.IsIndigenousChanged ->
                _state.update { it.copy(isIndigenous = action.value, successMessage = null) }

            CreateSubjectAction.Submit -> submit()
            CreateSubjectAction.DismissSuccess -> _state.update { it.copy(successMessage = null) }
            CreateSubjectAction.BackClick ->
                _state.update { it.copy(destination = CreateSubjectDestination.Back) }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    private fun submit() {
        // Re-validate every field and force errors to display (the user may never
        // have blurred a field, e.g. tapping Submit straight after the last one).
        val validated = _state.value.copy(
            dni = _state.value.dni.showError(dniValidator),
            name = _state.value.name.showError(nameValidator),
            lastName = _state.value.lastName.showError(lastNameValidator),
            birthDate = _state.value.birthDate.showError(birthDateValidator),
            sexError = if (_state.value.sex == null) "Seleccioná el sexo." else null,
        )

        val newSubject = buildSubject(validated)
        if (newSubject == null) {
            _state.update { validated }
            return
        }

        _state.update { validated.copy(isSubmitting = true, submitError = null, successMessage = null) }
        viewModelScope.launch {
            createSubject(newSubject).fold(
                onSuccess = {
                    _state.update {
                        CreateSubjectState(
                            successMessage = "Sujeto guardado",
                        )
                    }
                    syncCoordinator.requestSync() // fire-and-forget; UI does not wait on the result
                },
                onFailure = {
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            submitError = "No se pudo guardar. Intentá nuevamente.",
                        )
                    }
                },
            )
        }
    }

    /** Returns the [NewSubject] only when every field is valid; otherwise `null`. */
    private fun buildSubject(state: CreateSubjectState): NewSubject? {
        val allValid = state.dni.isValid && state.name.isValid &&
            state.lastName.isValid && state.birthDate.isValid && state.sexError == null
        if (!allValid) return null

        val dni = state.dni.value.toIntOrNull() ?: return null
        val sex = state.sex ?: return null
        val birthDate = parseBirthDate(state.birthDate.value) ?: return null
        return NewSubject(
            dni = dni,
            sex = sex,
            name = state.name.value.trim(),
            lastName = state.lastName.value.trim(),
            birthDate = birthDate,
            isRural = state.isRural,
            isIndigenous = state.isIndigenous,
        )
    }

    private val dniValidator: Validator = Rules.of(
        Rules.required("Ingresá el DNI."),
        Rules.digitsInRange(1..99_999_999, "Ingresá un DNI válido (1 a 8 dígitos)."),
    )

    private val nameValidator: Validator = Rules.of(
        Rules.required("Ingresá el nombre."),
        Rules.maxLength(50, "Máximo 50 caracteres."),
    )

    private val lastNameValidator: Validator = Rules.of(
        Rules.required("Ingresá el apellido."),
        Rules.maxLength(50, "Máximo 50 caracteres."),
    )

    private val birthDateValidator: Validator = Rules.of(
        Rules.required("Ingresá la fecha de nacimiento."),
        { value ->
            val date = parseBirthDate(value)
            when {
                value.length != 8 -> "Formato esperado DD/MM/AAAA."
                date == null -> "Fecha inválida."
                date > today() -> "La fecha no puede ser futura."
                else -> null
            }
        },
    )

    private fun parseBirthDate(input: String): LocalDate? {
        if (input.length != 8 || !input.all(Char::isDigit)) return null
        return runCatching {
            LocalDate(
                year = input.substring(4, 8).toInt(),
                monthNumber = input.substring(2, 4).toInt(),
                dayOfMonth = input.substring(0, 2).toInt(),
            )
        }.getOrNull()
    }

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}
