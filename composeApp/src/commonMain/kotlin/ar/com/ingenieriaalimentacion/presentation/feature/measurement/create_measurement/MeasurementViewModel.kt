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
import ar.com.ingenieriaalimentacion.domain.model.measurement.NewMeasurement
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary
import ar.com.ingenieriaalimentacion.domain.usecase.measurement.CreateMeasurementUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyProfileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class MeasurementViewModel(
    private val createMeasurement: CreateMeasurementUseCase,
    private val getMyProfile: GetMyProfileUseCase,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(MeasurementState(measuredAtDisplay = friendlyNow()))
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        _state.value,
    )

    init {
        loadProfile()
    }

    fun onAction(action: MeasurementAction) {
        when (action) {
            is MeasurementAction.WeightChanged ->
                _state.update {
                    it.copy(
                        weight = it.weight.onChange(sanitizeDecimal(action.value), weightValidator),
                        successMessage = null,
                    )
                }

            is MeasurementAction.WeightFocusChanged ->
                _state.update { it.copy(weight = it.weight.onFocus(action.focused, weightValidator)) }

            is MeasurementAction.HeightChanged ->
                _state.update {
                    it.copy(
                        height = it.height.onChange(sanitizeDecimal(action.value), heightValidator),
                        successMessage = null,
                    )
                }

            is MeasurementAction.HeightFocusChanged ->
                _state.update { it.copy(height = it.height.onFocus(action.focused, heightValidator)) }

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

            MeasurementAction.Submit -> submit()
            MeasurementAction.DismissSuccess -> _state.update { it.copy(successMessage = null) }
            MeasurementAction.RetryProfile -> loadProfile()
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

    private fun loadProfile() {
        _state.update { it.copy(isLoadingProfile = true, profileError = null) }
        viewModelScope.launch {
            getMyProfile().fold(
                onSuccess = { profile ->
                    val approved = profile.establishments
                        ?.filter { it.status == EstablishmentStatus.Approved }
                        ?: emptyList()
                    _state.update {
                        it.copy(
                            isLoadingProfile = false,
                            establishmentOptions = approved,
                            // Auto-select when exactly one approved establishment exists.
                            selectedEstablishment = approved.singleOrNull() ?: it.selectedEstablishment,
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(
                            isLoadingProfile = false,
                            profileError = "No se pudo cargar el perfil. Intentá nuevamente.",
                        )
                    }
                },
            )
        }
    }

    private fun submit() {
        val current = _state.value
        val validated = current.copy(
            weight = current.weight.showError(weightValidator),
            height = current.height.showError(heightValidator),
            establishmentError = if (current.selectedEstablishment == null) "Elegí el establecimiento." else null,
        )

        val measurement = buildMeasurement(validated)
        if (measurement == null) {
            _state.update { validated }
            return
        }

        _state.update { validated.copy(isSubmitting = true, submitError = null, successMessage = null) }
        viewModelScope.launch {
            createMeasurement(measurement).fold(
                onSuccess = {
                    _state.update {
                        // Keep subject + establishment, clear peso/talla so the relevador can load another.
                        it.copy(
                            weight = TextFieldState(),
                            height = TextFieldState(),
                            isSubmitting = false,
                            submitError = null,
                            successMessage = "Medición guardada. Se sincronizará automáticamente.",
                            measuredAtDisplay = friendlyNow(),
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

    /** Returns the [NewMeasurement] only when every field is valid; otherwise `null`. */
    private fun buildMeasurement(state: MeasurementState): NewMeasurement? {
        val subject = state.subject ?: return null
        val establishment = state.selectedEstablishment ?: return null
        if (!state.weight.isValid || !state.height.isValid) return null
        val weight = state.weight.value.toDoubleOrNull() ?: return null
        val height = state.height.value.toDoubleOrNull() ?: return null
        return NewMeasurement(
            subject = subject,
            establishmentId = establishment.id,
            establishmentName = establishment.name,
            weightKg = weight,
            heightCm = height,
        )
    }

    private fun sanitizeDecimal(raw: String): String {
        val normalized = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
        val firstDot = normalized.indexOf('.')
        if (firstDot == -1) return normalized
        // Keep the first separator, drop any subsequent ones.
        val head = normalized.substring(0, firstDot + 1)
        val tail = normalized.substring(firstDot + 1).filter { it.isDigit() }
        return head + tail
    }

    private val weightValidator: Validator = Rules.of(
        Rules.required("Ingresá el peso."),
        Rules.decimalInRange(0.5, 300.0, "Peso entre 0,5 y 300 kg."),
    )

    private val heightValidator: Validator = Rules.of(
        Rules.required("Ingresá la talla."),
        Rules.decimalInRange(20.0, 250.0, "Talla entre 20 y 250 cm."),
    )

    private companion object {
        fun friendlyNow(): String {
            val dt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val d = dt.dayOfMonth.toString().padStart(2, '0')
            val m = dt.monthNumber.toString().padStart(2, '0')
            val h = dt.hour.toString().padStart(2, '0')
            val min = dt.minute.toString().padStart(2, '0')
            return "Ahora · $d/$m/${dt.year} $h:$min"
        }
    }
}
