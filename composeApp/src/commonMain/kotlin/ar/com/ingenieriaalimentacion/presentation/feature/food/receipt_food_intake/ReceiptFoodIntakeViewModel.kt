package ar.com.ingenieriaalimentacion.presentation.feature.food.receipt_food_intake

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.core.validation.Rules
import ar.com.ingenieriaalimentacion.core.validation.TextFieldState
import ar.com.ingenieriaalimentacion.core.validation.Validator
import ar.com.ingenieriaalimentacion.core.validation.onChange
import ar.com.ingenieriaalimentacion.core.validation.onFocus
import ar.com.ingenieriaalimentacion.core.validation.showError
import ar.com.ingenieriaalimentacion.data.network.connectivity.ConnectivityObserver
import ar.com.ingenieriaalimentacion.domain.model.food.Food
import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit
import ar.com.ingenieriaalimentacion.domain.model.food.NewFoodIntake
import ar.com.ingenieriaalimentacion.domain.model.food.NewFoodIntakeLine
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptError
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptFailure
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptLine
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptPhoto
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptSource
import ar.com.ingenieriaalimentacion.domain.model.profile.CurrentEstablishmentResult
import ar.com.ingenieriaalimentacion.domain.repository.FoodCatalogRepository
import ar.com.ingenieriaalimentacion.domain.usecase.food.ConfirmReceiptIntakeUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.ProposeReceiptIntakeUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.ResolveCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.util.IdGenerator
import ar.com.ingenieriaalimentacion.domain.util.MAX_INTEGER_QUANTITY
import ar.com.ingenieriaalimentacion.domain.util.exactReceiptPrefill
import ar.com.ingenieriaalimentacion.domain.util.sanitizeIntegerInput
import ar.com.ingenieriaalimentacion.domain.util.sanitizeQuantityInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

@OptIn(FlowPreview::class)
class ReceiptFoodIntakeViewModel(
    private val propose: ProposeReceiptIntakeUseCase,
    private val confirm: ConfirmReceiptIntakeUseCase,
    private val catalog: FoodCatalogRepository,
    private val resolve: ResolveCurrentEstablishmentUseCase,
    private val connectivity: ConnectivityObserver,
    private val idGenerator: IdGenerator,
) : ViewModel() {
    private var photo: ReceiptPhoto? = null
    private var proposalJob: Job? = null
    private val selectionJobs = mutableMapOf<Int, Job>()
    private var generation = 0
    private var nextLineId = 0
    private data class Pending(val intake: NewFoodIntake, val source: ReceiptSource, val photo: ReceiptPhoto)
    private var pending: Pending? = null
    private val query = MutableStateFlow("")
    private val _state = MutableStateFlow(ReceiptFoodIntakeState(
        intakeId = idGenerator.newId(), date = TextFieldState(value = todayDigits())))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { connectivity.isOnline.collect { online ->
            _state.update { it.copy(isOnline = online) }
        } }
        viewModelScope.launch { query.debounce(300).distinctUntilChanged().collectLatest { search(it) } }
        loadEstablishment()
    }

    fun onAction(action: ReceiptFoodIntakeAction) {
        when (action) {
            ReceiptFoodIntakeAction.CameraClick, ReceiptFoodIntakeAction.GalleryClick -> Unit
            is ReceiptFoodIntakeAction.PhotoPicked -> if (!state.value.submissionLocked) {
                action.result?.fold(
                    onSuccess = { picked -> validateReceiptPhoto(picked).fold(
                        onSuccess = { valid ->
                            proposalJob?.cancel(); selectionJobs.values.forEach(Job::cancel)
                            selectionJobs.clear(); photo = valid; generation++
                            _state.update { it.copy(step = ReceiptStep.Preview, photoToken = generation,
                                photoName = valid.fileName, photoSize = valid.size, lines = emptyList(),
                                proposalMessage = null, source = ReceiptSource.Photo, error = null) }
                        },
                        onFailure = ::showError,
                    ) },
                    onFailure = ::showError,
                )
            }
            ReceiptFoodIntakeAction.RemovePhoto -> if (!state.value.submissionLocked) {
                proposalJob?.cancel(); selectionJobs.values.forEach(Job::cancel)
                selectionJobs.clear(); photo = null; generation++
                _state.update { it.copy(step = ReceiptStep.Choose, photoName = null, photoSize = 0,
                    photoToken = generation, lines = emptyList(), error = null) }
            }
            ReceiptFoodIntakeAction.Analyze -> if (!state.value.submissionLocked) analyze()
            ReceiptFoodIntakeAction.CancelAnalysis -> if (!state.value.submissionLocked) {
                proposalJob?.cancel(); generation++
                _state.update { it.copy(step = ReceiptStep.Preview) }
            }
            ReceiptFoodIntakeAction.ManualFallback -> if (!state.value.submissionLocked && photo != null) {
                proposalJob?.cancel(); generation++
                _state.update { it.copy(step = ReceiptStep.Review, source = ReceiptSource.ManualFallback,
                    lines = emptyList(), proposalMessage = null, error = null) }
            }
            is ReceiptFoodIntakeAction.FoodSearchChanged -> if (editable()) {
                activateSearchLine(action.lineId)
                _state.update { it.copy(search = it.search.copy(query = action.value, isFocused = true)) }
                query.value = action.value
            }
            is ReceiptFoodIntakeAction.FoodSearchFocusChanged -> if (editable()) {
                if (action.focused) {
                    activateSearchLine(action.lineId)
                    _state.update { it.copy(search = it.search.copy(isFocused = true)) }
                } else if (state.value.searchLineId == action.lineId) {
                    _state.update { it.copy(searchLineId = null,
                        search = it.search.cleared().copy(isFocused = false)) }
                    query.value = ""
                }
            }
            ReceiptFoodIntakeAction.SearchRetry -> if (editable()) viewModelScope.launch { search(state.value.search.query) }
            is ReceiptFoodIntakeAction.FoodPicked -> if (editable()) pickFood(action.lineId, action.food)
            is ReceiptFoodIntakeAction.QuantityChanged -> if (editable()) updateLine(action.lineId) { line ->
                val food = line.food
                val value = if (food?.unit == FoodUnit.Unidades) sanitizeIntegerInput(action.value)
                    else sanitizeQuantityInput(action.value)
                line.copy(quantity = line.quantity.onChange(value, quantityValidator(food?.unit)), reviewed = false)
            }
            is ReceiptFoodIntakeAction.QuantityFocusChanged -> if (editable()) updateLine(action.lineId) { line ->
                line.copy(quantity = line.quantity.onFocus(action.focused, quantityValidator(line.food?.unit)))
            }
            is ReceiptFoodIntakeAction.LineConfirmed -> if (editable()) updateLine(action.lineId) { line ->
                val validated = line.quantity.showError(quantityValidator(line.food?.unit))
                line.copy(quantity = validated, reviewed = line.food != null && validated.isValid)
            }
            is ReceiptFoodIntakeAction.LineRemoved -> if (editable()) {
                selectionJobs.remove(action.lineId)?.cancel()
                val removedSearch = state.value.searchLineId == action.lineId
                _state.update { current -> current.copy(
                    lines = current.lines.filterNot { line -> line.id == action.lineId },
                    searchLineId = current.searchLineId.takeUnless { it == action.lineId },
                    search = if (current.searchLineId == action.lineId) current.search.cleared() else current.search,
                ) }
                if (removedSearch) query.value = ""
            }
            ReceiptFoodIntakeAction.AddLine -> if (editable()) {
                val id = nextLineId++
                _state.update { it.copy(lines = it.lines + ReceiptReviewLine(id, null, null, null),
                    searchLineId = id, search = it.search.cleared()) }
            }
            is ReceiptFoodIntakeAction.DateChanged -> if (editable()) _state.update {
                it.copy(date = it.date.onChange(dateDigits(action.value), dateValidator))
            }
            is ReceiptFoodIntakeAction.DateFocusChanged -> if (editable()) _state.update {
                it.copy(date = it.date.onFocus(action.focused, dateValidator))
            }
            is ReceiptFoodIntakeAction.CommentsChanged -> if (editable()) _state.update {
                it.copy(comments = it.comments.onChange(action.value, commentsValidator))
            }
            is ReceiptFoodIntakeAction.CommentsFocusChanged -> if (editable()) _state.update {
                it.copy(comments = it.comments.onFocus(action.focused, commentsValidator))
            }
            is ReceiptFoodIntakeAction.OriginSelected -> if (editable()) _state.update {
                it.copy(originType = action.origin, originError = null)
            }
            is ReceiptFoodIntakeAction.RecurrenceSelected -> if (editable()) _state.update {
                it.copy(isRecurring = action.isRecurring, recurrenceError = null)
            }
            ReceiptFoodIntakeAction.Submit -> if (editable()) submit()
            ReceiptFoodIntakeAction.RetrySubmit -> pending?.let { if (state.value.isOnline) sendPending(it) }
            ReceiptFoodIntakeAction.ReviseAfterFailure -> if (state.value.submitFailureKind != ReceiptError.Retryable && !state.value.isSubmitting) {
                pending = null
                _state.update { it.copy(intakeId = idGenerator.newId(), submissionLocked = false,
                    submitFailureKind = null, error = null) }
            }
            ReceiptFoodIntakeAction.RetryProfile -> if (!state.value.submissionLocked) loadEstablishment()
            ReceiptFoodIntakeAction.BackClick -> if (!state.value.isSubmitting) _state.update {
                if (photo == null) it.copy(destination = ReceiptFoodIntakeDestination.Back)
                else it.copy(showDiscardDialog = true)
            }
            ReceiptFoodIntakeAction.ConfirmDiscard -> if (!state.value.isSubmitting) {
                proposalJob?.cancel(); selectionJobs.values.forEach(Job::cancel)
                selectionJobs.clear(); photo = null; pending = null
                _state.update { it.copy(showDiscardDialog = false, destination = ReceiptFoodIntakeDestination.Back) }
            }
            ReceiptFoodIntakeAction.CancelDiscard -> _state.update { it.copy(showDiscardDialog = false) }
        }
    }

    private fun editable() = state.value.step == ReceiptStep.Review && !state.value.submissionLocked

    private fun loadEstablishment() {
        _state.update { it.copy(isLoadingProfile = true, profileError = null) }
        viewModelScope.launch {
            when (val result = resolve()) {
                is CurrentEstablishmentResult.Selected -> _state.update {
                    it.copy(isLoadingProfile = false, selectedEstablishment = result.establishment)
                }
                is CurrentEstablishmentResult.NoneSelected, is CurrentEstablishmentResult.StaleSelection ->
                    _state.update { it.copy(isLoadingProfile = false, selectedEstablishment = null,
                        profileError = "Elegí un establecimiento en Inicio para cargar un ingreso.") }
                is CurrentEstablishmentResult.LoadFailed -> _state.update {
                    it.copy(isLoadingProfile = false, selectedEstablishment = null,
                        profileError = "No se pudo cargar el establecimiento. Intentá nuevamente.")
                }
            }
        }
    }

    private fun analyze() {
        val selected = state.value.selectedEstablishment ?: return
        val captured = photo ?: return
        if (!state.value.isOnline) return
        proposalJob?.cancel()
        val token = generation
        _state.update { it.copy(step = ReceiptStep.Processing, error = null) }
        proposalJob = viewModelScope.launch {
            propose(selected.id, captured).fold(
                onSuccess = { proposal ->
                    if (token != generation || proposal.establishmentId != selected.id ||
                        state.value.selectedEstablishment?.id != selected.id) return@fold
                    val rows = proposal.lines.map { line ->
                        val food = line.matchedFoodId?.let { id -> catalog.getFood(id).getOrNull() }
                        if (token != generation) return@launch
                        ReceiptReviewLine(
                            id = nextLineId++, description = line.description,
                            rawQuantity = line.rawQuantity, rawUnitText = line.rawUnitText,
                            food = food,
                            quantity = TextFieldState(value = prefillQuantity(line, food)),
                            resolutionError = if (line.matchedFoodId != null && food == null)
                                "No se pudo cargar el alimento. Buscalo en el catálogo." else null,
                        )
                    }
                    _state.update { it.copy(step = ReceiptStep.Review,
                        source = if (rows.isEmpty()) ReceiptSource.ManualFallback else ReceiptSource.Photo,
                        lines = rows, proposalMessage = proposal.message) }
                },
                onFailure = { failure ->
                    if (failure is CancellationException) throw failure
                    if (token == generation) _state.update {
                        it.copy(step = ReceiptStep.Preview, error = failure.userMessage())
                    }
                },
            )
        }
    }

    private fun pickFood(lineId: Int, candidate: Food) {
        selectionJobs.remove(lineId)?.cancel()
        updateLine(lineId) { it.copy(food = null, quantity = TextFieldState(), reviewed = false,
            resolutionError = null) }
        _state.update { it.copy(search = it.search.cleared(), searchLineId = null) }
        query.value = ""
        val token = generation
        selectionJobs[lineId] = viewModelScope.launch {
            val result = catalog.getFood(candidate.id)
            if (token != generation || state.value.lines.none { it.id == lineId }) return@launch
            result.fold(
                onSuccess = { food -> updateLine(lineId) { line -> line.copy(food = food,
                    quantity = TextFieldState(value = exactReceiptPrefill(line.rawQuantity, line.rawUnitText, food.unit).orEmpty()),
                    reviewed = false, resolutionError = null) } },
                onFailure = { if (it is CancellationException) throw it
                    updateLine(lineId) { line -> line.copy(resolutionError =
                        "No se pudo cargar el alimento. Buscalo en el catálogo.") } },
            )
        }
    }

    private fun updateLine(id: Int, transform: (ReceiptReviewLine) -> ReceiptReviewLine) {
        _state.update { current -> current.copy(lines = current.lines.map { if (it.id == id) transform(it) else it }) }
    }

    private fun activateSearchLine(lineId: Int) {
        if (state.value.searchLineId == lineId) return
        _state.update { it.copy(searchLineId = lineId, search = it.search.cleared()) }
        query.value = ""
    }

    private suspend fun search(value: String) {
        if (value.isBlank()) {
            _state.update { it.copy(search = it.search.copy(results = emptyList(), total = 0, isSearching = false)) }
            return
        }
        _state.update { it.copy(search = it.search.copy(isSearching = true, error = false)) }
        catalog.searchFoods(value, 8).fold(
            onSuccess = { result -> if (state.value.search.query == value) _state.update {
                it.copy(search = it.search.copy(results = result.items, total = result.total,
                    isSearching = false, error = false))
            } },
            onFailure = { if (it is CancellationException) throw it
                _state.update { current -> current.copy(search = current.search.copy(isSearching = false, error = true)) } },
        )
    }

    private fun submit() {
        val current = state.value
        if (!current.canSubmit) {
            _state.update { it.copy(error = "Completá al menos un alimento y conectate para guardar.") }
            return
        }
        val validated = current.copy(
            date = current.date.showError(dateValidator),
            comments = current.comments.showError(commentsValidator),
            originError = if (current.originType == null) "Seleccioná el origen del alimento." else null,
            recurrenceError = if (current.isRecurring == null) "Seleccioná el tipo de aporte." else null,
            lines = current.lines.map { it.copy(quantity = it.quantity.showError(quantityValidator(it.food?.unit))) },
        )
        _state.value = validated
        if (!validated.date.isValid || !validated.comments.isValid ||
            validated.originType == null || validated.isRecurring == null ||
            validated.lines.any { it.food == null || !it.quantity.isValid || !it.reviewed }) {
            _state.update { it.copy(error = "Revisá los datos del ingreso y los alimentos.") }
            return
        }
        val captured = photo ?: return
        val intake = NewFoodIntake(
            id = validated.intakeId,
            establishmentId = validated.selectedEstablishment!!.id,
            receivedAtEpochMillis = selectedDateMillis(validated.date.value),
            comments = validated.comments.value.trim().ifBlank { null },
            originType = requireNotNull(validated.originType),
            isRecurring = requireNotNull(validated.isRecurring),
            items = validated.lines.map { NewFoodIntakeLine(it.food!!.id, it.quantity.value.trimEnd('.')) },
        )
        pending = Pending(intake, validated.source, captured)
        sendPending(pending!!)
    }

    private fun sendPending(value: Pending) {
        if (state.value.isSubmitting || !state.value.isOnline) return
        _state.update { it.copy(isSubmitting = true, submissionLocked = true, error = null) }
        viewModelScope.launch {
            confirm(value.intake, value.source, value.photo).fold(
                onSuccess = { pending = null; _state.update { it.copy(isSubmitting = false,
                    destination = ReceiptFoodIntakeDestination.Saved) } },
                onFailure = { failure ->
                    if (failure is CancellationException) throw failure
                    _state.update { it.copy(isSubmitting = false,
                        submitFailureKind = (failure as? ReceiptFailure)?.kind ?: ReceiptError.Retryable,
                        error = failure.userMessage()) }
                },
            )
        }
    }

    private fun showError(failure: Throwable) = _state.update { it.copy(error = failure.userMessage()) }
    fun previewPhoto(): ReceiptPhoto? = photo
    fun clearDestination() = _state.update { it.copy(destination = null) }

    private fun prefillQuantity(line: ReceiptLine, food: Food?): String =
        food?.let { exactReceiptPrefill(line.rawQuantity, line.rawUnitText, it.unit) }.orEmpty()

    private val integerValidator: Validator = Rules.of(
        Rules.required("Ingresá la cantidad."),
        Rules.digitsInRange(1..MAX_INTEGER_QUANTITY, "Ingresá una cantidad entera mayor a 0."),
    )
    private val decimalValidator: Validator = { raw ->
        val text = raw.trimEnd('.')
        val match = Regex("^[0-9]{1,7}(\\.[0-9]{1,3})?$").matches(text)
        when {
            text.isBlank() -> "Ingresá la cantidad."
            !match || text.all { it == '0' || it == '.' } -> "Cantidad entre 0 y 9.999.999,999."
            else -> null
        }
    }
    private fun quantityValidator(unit: FoodUnit?) = when (unit) {
        FoodUnit.Unidades -> integerValidator
        FoodUnit.Kilogramos, FoodUnit.Litros -> decimalValidator
        null -> { _: String -> "Elegí un alimento." }
    }
    private val commentsValidator: Validator = Rules.of(Rules.maxLength(500, "Máximo 500 caracteres."))
    private val dateValidator: Validator = { raw ->
        val date = parseDate(raw)
        when {
            raw.isBlank() -> "Ingresá la fecha."
            raw.length != 8 -> "Formato esperado DD/MM/AAAA."
            date == null -> "Fecha inválida."
            date > today() -> "La fecha no puede ser futura."
            else -> null
        }
    }
    private fun selectedDateMillis(raw: String): Long {
        val date = requireNotNull(parseDate(raw))
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        return if (date == now.toLocalDateTime(zone).date) now.toEpochMilliseconds()
            else date.atStartOfDayIn(zone).toEpochMilliseconds()
    }
    private fun today() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    private fun todayDigits(): String {
        val date = today()
        return date.day.toString().padStart(2, '0') + date.month.number.toString().padStart(2, '0') + date.year
    }
    private fun dateDigits(raw: String) = raw.filter(Char::isDigit).take(8)
    private fun parseDate(raw: String): LocalDate? = if (raw.length != 8 || !raw.all(Char::isDigit)) null
        else runCatching { LocalDate(raw.substring(4, 8).toInt(), raw.substring(2, 4).toInt(), raw.substring(0, 2).toInt()) }.getOrNull()
}

private fun Throwable.userMessage(): String = when ((this as? ReceiptFailure)?.kind) {
    ReceiptError.InvalidImage, ReceiptError.MismatchedContent -> "Elegí una imagen válida del recibo."
    ReceiptError.SourceTooLarge -> "La imagen es demasiado grande para procesarla. Elegí otra foto."
    ReceiptError.TooLarge -> "La imagen procesada supera 10 MB. Elegí otra foto."
    ReceiptError.AccessDenied -> "Ya no tenés acceso a este establecimiento."
    ReceiptError.EstablishmentNotFound -> "El establecimiento seleccionado ya no existe."
    ReceiptError.FoodNotFound -> "Un alimento ya no está en el catálogo. Revisá las líneas."
    ReceiptError.InvalidData -> "Revisá la fecha, los alimentos y las cantidades."
    ReceiptError.Unauthorized -> "Tu sesión venció. Volvé a ingresar."
    ReceiptError.AiQuotaExhausted ->
        "Se alcanzó el límite del servicio de IA. Intentá más tarde o cargá el recibo manualmente con esta foto."
    ReceiptError.AiUnavailable ->
        "El servicio de IA no está disponible. Intentá más tarde o cargá el recibo manualmente con esta foto."
    ReceiptError.RateLimited ->
        "Alcanzaste el límite de solicitudes. Esperá antes de volver a analizar el recibo."
    ReceiptError.Retryable, null -> "No se pudo completar. Reintentá con este mismo recibo."
}
