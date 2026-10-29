package ar.com.ingenieriaalimentacion.presentation.feature.measurement.create_measurement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.core.validation.Rules
import ar.com.ingenieriaalimentacion.core.validation.TextFieldState
import ar.com.ingenieriaalimentacion.core.validation.Validator
import ar.com.ingenieriaalimentacion.core.validation.onChange
import ar.com.ingenieriaalimentacion.core.validation.onFocus
import ar.com.ingenieriaalimentacion.core.validation.showError
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.CreateMeasurementUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.FutureMeasurementDateException
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.PrepareMeasurementUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.util.sanitizeDecimalInput
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class MeasurementViewModel(
    private val createMeasurement: CreateMeasurementUseCase,
    private val getMyEstablishments: GetMyEstablishmentsUseCase,
    private val getCurrentEstablishment: GetCurrentEstablishmentUseCase,
    private val syncCoordinator: SyncCoordinator,
    private val prepareMeasurement: PrepareMeasurementUseCase,
    private val now: () -> Instant,
) : ViewModel() {

    private fun currentMeasurementDateTime(): LocalDateTime {
        val value = now().toLocalDateTime(PrepareMeasurementUseCase.ARGENTINA_TIME_ZONE)
        return LocalDateTime(value.year, value.monthNumber, value.dayOfMonth, value.hour, value.minute)
    }

    private val initialDateTime = currentMeasurementDateTime()
    private val _state = MutableStateFlow(MeasurementState(
        measuredDateTime = initialDateTime,
        latestAllowedMeasurementDate = initialDateTime.date,
    ))
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        _state.value,
    )

    private var retryDraft: MeasurementConfirmation? = null
    private var nextConfirmationToken = 0L

    init {
        loadEstablishments()
    }

    fun onAction(action: MeasurementAction) {
        if (_state.value.isSubmitting &&
            action != MeasurementAction.DismissSuccess &&
            action !is MeasurementAction.WeightFocusChanged &&
            action !is MeasurementAction.HeightFocusChanged &&
            action !is MeasurementAction.ObservationFocusChanged
        ) return
        if (action == MeasurementAction.BackClick &&
            (_state.value.showMeasurementDatePicker || _state.value.showMeasurementTimePicker)
        ) {
            _state.update { it.copy(showMeasurementDatePicker = false, showMeasurementTimePicker = false) }
            return
        }
        if (action == MeasurementAction.BackClick && _state.value.confirmation != null) {
            invalidateDraft()
            return
        }
        when (action) {
            is MeasurementAction.MeasurementDateSelected,
            is MeasurementAction.MeasurementTimeSelected,
            is MeasurementAction.ObservationChanged,
            is MeasurementAction.WeightChanged,
            is MeasurementAction.HeightChanged,
            is MeasurementAction.SubjectSelected,
            is MeasurementAction.EstablishmentSelected,
            MeasurementAction.ChangeSubjectClick,
            MeasurementAction.BackClick,
            MeasurementAction.RetryProfile -> invalidateDraft()
            else -> Unit
        }
        when (action) {
            MeasurementAction.MeasurementDatePickerOpen -> {
                if (_state.value.confirmation == null && !_state.value.showMeasurementTimePicker) {
                    _state.update { it.copy(
                        showMeasurementDatePicker = true,
                        latestAllowedMeasurementDate = currentMeasurementDateTime().date,
                    ) }
                }
            }
            MeasurementAction.MeasurementDatePickerDismiss ->
                _state.update { it.copy(showMeasurementDatePicker = false) }
            is MeasurementAction.MeasurementDateSelected -> {
                val current = _state.value.measuredDateTime ?: return
                val maximum = currentMeasurementDateTime().date
                _state.update { it.copy(
                    measuredDateTime = LocalDateTime(action.date, current.time),
                    latestAllowedMeasurementDate = maximum,
                    measurementDateError = if (action.date > maximum) "La fecha no puede ser futura." else null,
                    showMeasurementDatePicker = false,
                    successMessage = null,
                    submitError = null,
                ) }
            }
            MeasurementAction.MeasurementTimePickerOpen -> {
                if (_state.value.confirmation == null && !_state.value.showMeasurementDatePicker) {
                    _state.update { it.copy(showMeasurementTimePicker = true) }
                }
            }
            MeasurementAction.MeasurementTimePickerDismiss ->
                _state.update { it.copy(showMeasurementTimePicker = false) }
            is MeasurementAction.MeasurementTimeSelected -> {
                val current = _state.value.measuredDateTime ?: return
                _state.update { it.copy(
                    measuredDateTime = LocalDateTime(current.date, LocalTime(action.time.hour, action.time.minute)),
                    showMeasurementTimePicker = false,
                    successMessage = null,
                    submitError = null,
                ) }
            }
            is MeasurementAction.WeightChanged ->
                _state.update {
                    it.copy(
                        weight = it.weight.onChange(sanitizeDecimalInput(action.value), weightValidator),
                        successMessage = null,
                    )
                }

            is MeasurementAction.WeightFocusChanged ->
                _state.update { it.copy(weight = it.weight.onFocus(action.focused, weightValidator)) }

            is MeasurementAction.HeightChanged ->
                _state.update {
                    it.copy(
                        height = it.height.onChange(sanitizeDecimalInput(action.value), heightValidator),
                        successMessage = null,
                    )
                }

            is MeasurementAction.HeightFocusChanged ->
                _state.update { it.copy(height = it.height.onFocus(action.focused, heightValidator)) }

            is MeasurementAction.ObservationChanged ->
                _state.update {
                    it.copy(observation = it.observation.onChange(action.value, observationValidator), successMessage = null)
                }

            is MeasurementAction.ObservationFocusChanged ->
                _state.update { it.copy(observation = it.observation.onFocus(action.focused, observationValidator)) }

            is MeasurementAction.SubjectSelected ->
                _state.update { it.copy(subject = action.subject, successMessage = null) }

            MeasurementAction.ChangeSubjectClick ->
                _state.update { it.copy(destination = MeasurementDestination.NavigateToSelectSubject) }

            MeasurementAction.EstablishmentPickerOpen ->
                _state.update { it.copy(showEstablishmentPicker = true) }

            MeasurementAction.EstablishmentPickerDismiss ->
                _state.update { it.copy(showEstablishmentPicker = false) }

            is MeasurementAction.EstablishmentSelected ->
                _state.update {
                    it.copy(
                        selectedEstablishment = action.establishment,
                        establishmentError = null,
                        showEstablishmentPicker = false,
                        successMessage = null,
                    )
                }

            is MeasurementAction.ConfirmMeasurement -> {
                val confirmation = _state.value.confirmation ?: return
                if (confirmation.token == action.token) persist(confirmation)
            }
            is MeasurementAction.DismissConfirmation -> {
                if (_state.value.confirmation?.token == action.token) invalidateDraft()
            }
            MeasurementAction.Submit -> submit()
            MeasurementAction.DismissSuccess -> _state.update { it.copy(successMessage = null) }
            MeasurementAction.RetryProfile -> loadEstablishments()
            MeasurementAction.BackClick ->
                _state.update { it.copy(destination = MeasurementDestination.Back) }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    /** Called from the Root when the subject selector returns a result. */
    fun setSubjectFromResult(subject: SubjectSummary) {
        onAction(MeasurementAction.SubjectSelected(subject))
    }

    private fun loadEstablishments() {
        _state.update { it.copy(isLoadingProfile = true, profileError = null) }
        viewModelScope.launch {
            getMyEstablishments().fold(
                onSuccess = { establishments ->
                    val approved = establishments.filter { it.status == EstablishmentStatus.Approved }
                    val current = getCurrentEstablishment(approved)
                    val selected = current ?: approved.singleOrNull() ?: _state.value.selectedEstablishment
                    if (!_state.value.isSubmitting && selected != _state.value.selectedEstablishment) invalidateDraft()
                    _state.update {
                        it.copy(
                            isLoadingProfile = false,
                            establishmentOptions = approved,
                            // Current establishment (Home's default) wins over auto-select-when-single;
                            // a manual pick already in state (e.g. a RetryProfile re-load) is untouched.
                            selectedEstablishment = if (it.isSubmitting) it.selectedEstablishment else selected,
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(
                            isLoadingProfile = false,
                            profileError = "No se pudieron cargar los establecimientos. Intentá nuevamente.",
                        )
                    }
                },
            )
        }
    }

    private fun invalidateDraft() {
        retryDraft = null
        _state.update { it.copy(confirmation = null) }
    }

    private fun submit() {
        val current = _state.value
        if (current.isSubmitting || current.confirmation != null ||
            current.showMeasurementDatePicker || current.showMeasurementTimePicker ||
            current.isLoadingProfile || current.profileError != null
        ) return
        val selected = current.measuredDateTime ?: return
        val maximum = currentMeasurementDateTime().date
        val validated = current.copy(
            weight = current.weight.showError(weightValidator),
            height = current.height.showError(heightValidator),
            observation = current.observation.showError(observationValidator),
            establishmentError = if (current.selectedEstablishment == null) "Elegí el establecimiento." else null,
            latestAllowedMeasurementDate = maximum,
            measurementDateError = if (selected.date > maximum) "La fecha no puede ser futura." else null,
        )
        _state.value = validated
        val subject = validated.subject ?: return
        val establishment = validated.selectedEstablishment ?: return
        if (!validated.weight.isValid || !validated.height.isValid || !validated.observation.isValid) return
        val weight = validated.weight.value.toDoubleOrNull() ?: return
        val height = validated.height.value.toDoubleOrNull() ?: return
        if (validated.measurementDateError != null) return
        val selectedInstant = selected.toInstant(PrepareMeasurementUseCase.ARGENTINA_TIME_ZONE)
        if (selectedInstant.toLocalDateTime(PrepareMeasurementUseCase.ARGENTINA_TIME_ZONE) != selected) {
            _state.update { it.copy(submitError = "La hora elegida no existe en esa fecha en Argentina. Elegí otra hora.") }
            return
        }
        val draft = retryDraft?.takeIf { sameForm(it, validated) } ?: try {
            MeasurementConfirmation(
                token = ++nextConfirmationToken,
                prepared = prepareMeasurement(
                    subject, establishment.id, establishment.name, weight, height,
                    observation = validated.observation.value,
                    measuredAt = selectedInstant,
                ),
                weightText = validated.weight.value,
                heightText = validated.height.value,
            ).also { retryDraft = it }
        } catch (_: FutureMeasurementDateException) {
            _state.update { it.copy(measurementDateError = "La fecha no puede ser futura.") }
            return
        } catch (_: IllegalArgumentException) {
            _state.update { it.copy(submitError = "Revisá los datos de la medición.") }
            return
        }
        _state.update {
            it.copy(
                submitError = null,
                successMessage = null,
            )
        }
        if (draft.prepared.requiresConfirmation) {
            _state.update { it.copy(confirmation = draft) }
        } else {
            persist(draft)
        }
    }

    private fun sameForm(draft: MeasurementConfirmation, state: MeasurementState): Boolean {
        val measurement = draft.prepared.measurement
        return measurement.subject == state.subject &&
            measurement.establishmentId == state.selectedEstablishment?.id &&
            measurement.establishmentName == state.selectedEstablishment?.name &&
            draft.weightText == state.weight.value && draft.heightText == state.height.value &&
            measurement.observation == state.observation.value.trim().takeIf { it.isNotEmpty() } &&
            measurement.measuredAt == state.measuredDateTime?.toInstant(PrepareMeasurementUseCase.ARGENTINA_TIME_ZONE)
    }

    private fun persist(draft: MeasurementConfirmation) {
        if (_state.value.isSubmitting || !sameForm(draft, _state.value)) return
        _state.update {
            it.copy(confirmation = null, isSubmitting = true, submitError = null, successMessage = null)
        }
        viewModelScope.launch {
            createMeasurement(draft.prepared.measurement).fold(
                onSuccess = {
                    retryDraft = null
                    val resetDateTime = currentMeasurementDateTime()
                    _state.update {
                        it.copy(
                            weight = TextFieldState(),
                            height = TextFieldState(),
                            observation = TextFieldState(),
                            confirmation = null,
                            isSubmitting = false,
                            submitError = null,
                            successMessage = "Medición guardada",
                            measuredDateTime = resetDateTime,
                            latestAllowedMeasurementDate = resetDateTime.date,
                            measurementDateError = null,
                            showMeasurementDatePicker = false,
                            showMeasurementTimePicker = false,
                        )
                    }
                    syncCoordinator.requestSync()
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _state.update {
                        it.copy(isSubmitting = false, submitError = "No se pudo guardar. Intentá nuevamente.")
                    }
                },
            )
        }
    }

    private val observationValidator: Validator = Rules.of(Rules.maxLength(500, "Máximo 500 caracteres."))

    private val weightValidator: Validator = Rules.of(
        Rules.required("Ingresá el peso."),
        Rules.decimalInRange(0.5, 300.0, "Peso entre 0,5 y 300 kg."),
    )

    private val heightValidator: Validator = Rules.of(
        Rules.required("Ingresá la talla."),
        Rules.decimalInRange(20.0, 250.0, "Talla entre 20 y 250 cm."),
    )

}
