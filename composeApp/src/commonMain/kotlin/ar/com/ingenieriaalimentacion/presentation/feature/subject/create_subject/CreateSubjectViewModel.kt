package ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
                        dni = action.value.filter(Char::isDigit),
                        fieldErrors = it.fieldErrors.copy(dni = null),
                        successMessage = null,
                    )
                }

            is CreateSubjectAction.SexChanged ->
                _state.update {
                    it.copy(
                        sex = action.value,
                        fieldErrors = it.fieldErrors.copy(sex = null),
                        successMessage = null,
                    )
                }

            is CreateSubjectAction.NameChanged ->
                _state.update {
                    it.copy(
                        name = action.value,
                        fieldErrors = it.fieldErrors.copy(name = null),
                        successMessage = null,
                    )
                }

            is CreateSubjectAction.LastNameChanged ->
                _state.update {
                    it.copy(
                        lastName = action.value,
                        fieldErrors = it.fieldErrors.copy(lastName = null),
                        successMessage = null,
                    )
                }

            is CreateSubjectAction.BirthDateChanged ->
                _state.update {
                    it.copy(
                        birthDate = formatBirthDate(action.value),
                        fieldErrors = it.fieldErrors.copy(birthDate = null),
                        successMessage = null,
                    )
                }

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
        val current = _state.value
        val parsed = parseAndValidate(current)
        if (parsed.errors != CreateSubjectFieldErrors()) {
            _state.update { it.copy(fieldErrors = parsed.errors) }
            return
        }
        val newSubject = parsed.value ?: return

        _state.update { it.copy(isSubmitting = true, submitError = null, successMessage = null) }
        viewModelScope.launch {
            createSubject(newSubject).fold(
                onSuccess = {
                    _state.update {
                        CreateSubjectState(
                            successMessage = "Guardado. Se sincronizará automáticamente.",
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

    private data class Parsed(val value: NewSubject?, val errors: CreateSubjectFieldErrors)

    private fun parseAndValidate(state: CreateSubjectState): Parsed {
        val dniInt = state.dni.toIntOrNull()
        val dniError = when {
            state.dni.isBlank() -> "Ingresá el DNI."
            dniInt == null || dniInt !in 1..99_999_999 -> "Ingresá un DNI válido (1 a 8 dígitos)."
            else -> null
        }
        val sexError = if (state.sex == null) "Seleccioná el sexo." else null
        val nameError = when {
            state.name.trim().isBlank() -> "Ingresá el nombre."
            state.name.trim().length > 50 -> "Máximo 50 caracteres."
            else -> null
        }
        val lastNameError = when {
            state.lastName.trim().isBlank() -> "Ingresá el apellido."
            state.lastName.trim().length > 50 -> "Máximo 50 caracteres."
            else -> null
        }
        val (birthDate, birthError) = parseBirthDate(state.birthDate)

        val errors = CreateSubjectFieldErrors(
            dni = dniError,
            sex = sexError,
            name = nameError,
            lastName = lastNameError,
            birthDate = birthError,
        )
        val value = if (errors == CreateSubjectFieldErrors() &&
            dniInt != null && state.sex != null && birthDate != null
        ) {
            NewSubject(
                dni = dniInt,
                sex = state.sex,
                name = state.name.trim(),
                lastName = state.lastName.trim(),
                birthDate = birthDate,
                isRural = state.isRural,
                isIndigenous = state.isIndigenous,
            )
        } else {
            null
        }
        return Parsed(value, errors)
    }

    private fun formatBirthDate(input: String): String {
        val digits = input.filter(Char::isDigit).take(8)
        return buildString {
            digits.forEachIndexed { i, c ->
                if (i == 2 || i == 4) append('/')
                append(c)
            }
        }
    }

    private fun parseBirthDate(input: String): Pair<LocalDate?, String?> {
        if (input.isBlank()) return null to "Ingresá la fecha de nacimiento."
        val match = BIRTH_DATE_REGEX.matchEntire(input)
            ?: return null to "Formato esperado DD/MM/AAAA."
        val (d, m, y) = match.destructured
        val parsed = runCatching {
            LocalDate(year = y.toInt(), monthNumber = m.toInt(), dayOfMonth = d.toInt())
        }.getOrNull() ?: return null to "Fecha inválida."
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        if (parsed > today) return null to "La fecha no puede ser futura."
        return parsed to null
    }

    private companion object {
        val BIRTH_DATE_REGEX = Regex("""^(\d{2})/(\d{2})/(\d{4})$""")
    }
}
