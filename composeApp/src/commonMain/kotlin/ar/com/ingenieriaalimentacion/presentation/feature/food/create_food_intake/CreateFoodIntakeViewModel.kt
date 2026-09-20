package ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_intake

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.core.validation.Rules
import ar.com.ingenieriaalimentacion.core.validation.TextFieldState
import ar.com.ingenieriaalimentacion.core.validation.Validator
import ar.com.ingenieriaalimentacion.core.validation.onChange
import ar.com.ingenieriaalimentacion.core.validation.onFocus
import ar.com.ingenieriaalimentacion.core.validation.showError
import ar.com.ingenieriaalimentacion.data.network.connectivity.ConnectivityObserver
import ar.com.ingenieriaalimentacion.domain.model.food.FoodIntakeError
import ar.com.ingenieriaalimentacion.domain.model.food.FoodIntakeFailure
import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit
import ar.com.ingenieriaalimentacion.domain.model.food.NewFoodIntake
import ar.com.ingenieriaalimentacion.domain.model.food.NewFoodIntakeLine
import ar.com.ingenieriaalimentacion.domain.model.profile.CurrentEstablishmentResult
import ar.com.ingenieriaalimentacion.domain.usecase.food.CreateFoodIntakeUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.SearchFoodsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.ResolveCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.util.IdGenerator
import ar.com.ingenieriaalimentacion.domain.util.MAX_INTEGER_QUANTITY
import ar.com.ingenieriaalimentacion.domain.util.MAX_QUANTITY
import ar.com.ingenieriaalimentacion.domain.util.sanitizeIntegerInput
import ar.com.ingenieriaalimentacion.domain.util.sanitizeQuantityInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
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
class CreateFoodIntakeViewModel(
    private val searchFoods: SearchFoodsUseCase,
    private val createFoodIntake: CreateFoodIntakeUseCase,
    private val resolveCurrentEstablishment: ResolveCurrentEstablishmentUseCase,
    private val connectivity: ConnectivityObserver,
    private val idGenerator: IdGenerator,
) : ViewModel() {

    // Every other IdGenerator call site parks the id in a SQLDelight `localId`. This feature has no
    // DB, so the idempotency key lives here instead: it survives configuration changes and every
    // retry — which is the whole point, since a retry after a timeout must not double-count stock.
    // It does not survive process death, and that is fine: neither does the rest of the form, so
    // there would be nothing left to re-submit.
    private val _state = MutableStateFlow(
        CreateFoodIntakeState(
            intakeId = idGenerator.newId(),
            receivedAt = TextFieldState(value = todayDigits()),
        ),
    )
    val state = _state.asStateFlow()

    /** Second flow purely for debouncing, exactly as `RequestEstablishmentViewModel` does it. */
    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch {
            query
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { search(it) }
        }
        viewModelScope.launch {
            // The Flow, not the suspending one-shot: IosConnectivityObserver.isOnline() suspends
            // until the first nw_path_monitor emission, so a one-shot check at screen entry would
            // stall the first frame on iOS.
            connectivity.isOnline.collect { online -> _state.update { it.copy(isOnline = online) } }
        }
        loadEstablishment()
    }

    fun onAction(action: CreateFoodIntakeAction) {
        when (action) {
            is CreateFoodIntakeAction.ReceivedAtChanged ->
                _state.update {
                    it.copy(
                        receivedAt = it.receivedAt.onChange(dateDigits(action.value), receivedAtValidator),
                    )
                }

            is CreateFoodIntakeAction.ReceivedAtFocusChanged ->
                _state.update { it.copy(receivedAt = it.receivedAt.onFocus(action.focused, receivedAtValidator)) }

            is CreateFoodIntakeAction.CommentsChanged ->
                _state.update {
                    it.copy(
                        comments = it.comments.onChange(action.value, commentsValidator),
                    )
                }

            is CreateFoodIntakeAction.CommentsFocusChanged ->
                _state.update { it.copy(comments = it.comments.onFocus(action.focused, commentsValidator)) }

            is CreateFoodIntakeAction.SearchQueryChanged -> {
                _state.update { it.copy(search = it.search.copy(query = action.value)) }
                query.value = action.value
            }

            is CreateFoodIntakeAction.SearchFocusChanged ->
                _state.update { it.copy(search = it.search.copy(isFocused = action.focused)) }

            CreateFoodIntakeAction.SearchRetry ->
                viewModelScope.launch { search(_state.value.search.query) }

            is CreateFoodIntakeAction.FoodPicked -> {
                _state.update { current ->
                    // The backend accepts duplicate lines and sums them into one balance, but two
                    // rows for the same food read as a mistake — edit the existing line instead.
                    val alreadyThere = current.lines.any { it.food.id == action.food.id }
                    current.copy(
                        lines = if (alreadyThere) current.lines else current.lines + FoodIntakeLine(action.food),
                        search = current.search.cleared(),
                    )
                }
                query.value = ""
            }

            is CreateFoodIntakeAction.LineQuantityChanged -> updateLine(action.foodId) { line ->
                val validator = validatorFor(line.food.unit)
                val sanitized = when (line.food.unit) {
                    FoodUnit.Unidades -> sanitizeIntegerInput(action.value)
                    else -> sanitizeQuantityInput(action.value)
                }
                line.copy(quantity = line.quantity.onChange(sanitized, validator))
            }

            is CreateFoodIntakeAction.LineQuantityFocusChanged -> updateLine(action.foodId) { line ->
                line.copy(quantity = line.quantity.onFocus(action.focused, validatorFor(line.food.unit)))
            }

            is CreateFoodIntakeAction.LineRemoved ->
                _state.update { it.copy(lines = it.lines.filterNot { line -> line.food.id == action.foodId }) }

            CreateFoodIntakeAction.Submit -> submit()
            CreateFoodIntakeAction.RetryProfile -> loadEstablishment()
            CreateFoodIntakeAction.BackClick ->
                _state.update { it.copy(destination = CreateFoodIntakeDestination.Back) }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    private inline fun updateLine(foodId: String, crossinline transform: (FoodIntakeLine) -> FoodIntakeLine) {
        _state.update { current ->
            current.copy(
                lines = current.lines.map { if (it.food.id == foodId) transform(it) else it },
            )
        }
    }

    private suspend fun search(value: String) {
        if (value.isBlank()) {
            _state.update { it.copy(search = it.search.copy(results = emptyList(), total = 0, isSearching = false, error = false)) }
            return
        }
        _state.update { it.copy(search = it.search.copy(isSearching = true, error = false)) }
        searchFoods(value).fold(
            onSuccess = { result ->
                _state.update {
                    it.copy(
                        search = it.search.copy(
                            results = result.items,
                            total = result.total,
                            isSearching = false,
                            error = false,
                        ),
                    )
                }
            },
            onFailure = { throwable ->
                // collectLatest cancels the in-flight search on every keystroke; swallowing that
                // into an error state would flash "no se pudo buscar" while the user is typing.
                if (throwable is CancellationException) throw throwable
                _state.update { it.copy(search = it.search.copy(isSearching = false, error = true)) }
            },
        )
    }

    private fun loadEstablishment() {
        _state.update { it.copy(isLoadingProfile = true, profileError = null) }
        viewModelScope.launch {
            when (val result = resolveCurrentEstablishment()) {
                is CurrentEstablishmentResult.Selected ->
                    _state.update {
                        it.copy(
                            isLoadingProfile = false,
                            selectedEstablishment = result.establishment,
                            profileError = null,
                        )
                    }
                is CurrentEstablishmentResult.NoneSelected,
                is CurrentEstablishmentResult.StaleSelection ->
                    _state.update {
                        it.copy(
                            isLoadingProfile = false,
                            selectedEstablishment = null,
                            profileError = "Elegí un establecimiento en Inicio para cargar un ingreso.",
                        )
                    }
                is CurrentEstablishmentResult.LoadFailed ->
                    _state.update {
                        it.copy(
                            isLoadingProfile = false,
                            selectedEstablishment = null,
                            profileError = "No se pudo cargar el establecimiento. Intentá nuevamente.",
                        )
                    }
            }
        }
    }

    private fun submit() {
        val current = _state.value
        if (!current.canSubmit) return              // also guards the double-tap
        val validated = current.copy(
            receivedAt = current.receivedAt.showError(receivedAtValidator),
            comments = current.comments.showError(commentsValidator),
            lines = current.lines.map { it.copy(quantity = it.quantity.showError(validatorFor(it.food.unit))) },
        )

        val intake = buildIntake(validated)
        if (intake == null) {
            _state.update { validated }
            return
        }

        _state.update { validated.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            createFoodIntake(intake).fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            submitError = null,
                            destination = CreateFoodIntakeDestination.Saved,
                        )
                    }
                },
                onFailure = { throwable ->
                    if (throwable is CancellationException) throw throwable
                    val error = (throwable as? FoodIntakeFailure)?.error ?: FoodIntakeError.Unknown(throwable)
                    // The form and the intakeId are untouched: tapping Guardar again re-sends the
                    // same id, which the server answers 200 to if the first attempt actually
                    // landed. A retry can never inflate the stock.
                    _state.update { it.copy(isSubmitting = false, submitError = error.toUserMessage()) }
                },
            )
        }
    }

    private fun buildIntake(state: CreateFoodIntakeState): NewFoodIntake? {
        val establishment = state.selectedEstablishment ?: return null
        if (state.lines.isEmpty()) return null
        if (!state.receivedAt.isValid || !state.comments.isValid) return null
        if (state.lines.any { !it.quantity.isValid }) return null
        val date = parseDate(state.receivedAt.value) ?: return null

        return NewFoodIntake(
            id = state.intakeId,
            establishmentId = establishment.id,
            receivedAtEpochMillis = receivedAtMillis(date),
            comments = state.comments.value.trim().takeIf { it.isNotBlank() },
            items = state.lines.map {
                NewFoodIntakeLine(
                    foodId = it.food.id,
                    // The sanitizer already normalized the separator as the user typed; a trailing
                    // "12." would not survive the server's Decimal parse, so trim it here.
                    quantity = it.quantity.value.trimEnd('.'),
                )
            },
        )
    }

    // digitsInRange goes through toIntOrNull, so "12.5" fails it — exactly the integer rule.
    // The backend does NOT enforce this (it accepts 12.5 eggs); the check is client-side only.
    private val integerQuantityValidator: Validator = Rules.of(
        Rules.required("Ingresá la cantidad."),
        Rules.digitsInRange(1..MAX_INTEGER_QUANTITY, "Ingresá una cantidad entera mayor a 0."),
    )

    private val decimalQuantityValidator: Validator = Rules.of(
        Rules.required("Ingresá la cantidad."),
        Rules.decimalInRange(0.0, MAX_QUANTITY, "Cantidad entre 0 y 9.999.999,999."),
    )

    private fun validatorFor(unit: FoodUnit): Validator = when (unit) {
        FoodUnit.Unidades -> integerQuantityValidator
        else -> decimalQuantityValidator
    }

    private val commentsValidator: Validator = Rules.of(
        Rules.maxLength(MAX_COMMENTS, "Máximo $MAX_COMMENTS caracteres."),
    )

    /**
     * Not built from [Rules] alone: the parse and the not-future check share one parsed date, the
     * same shape `CreateSubjectViewModel.birthDateValidator` uses.
     */
    private val receivedAtValidator: Validator = { raw ->
        val date = parseDate(raw)
        when {
            raw.isBlank() -> "Ingresá la fecha."
            raw.length != DATE_DIGITS -> "Formato esperado DD/MM/AAAA."
            date == null -> "Fecha inválida."
            date > today() -> "La fecha no puede ser futura."
            else -> null
        }
    }

    /**
     * Today → the real capture instant, which is never future. A past date → local midnight, which
     * is unambiguously non-future in any timezone the device reports.
     */
    private fun receivedAtMillis(date: LocalDate): Long {
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        return if (date == now.toLocalDateTime(zone).date) {
            now.toEpochMilliseconds()
        } else {
            date.atStartOfDayIn(zone).toEpochMilliseconds()
        }
    }

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    private companion object {
        const val DEBOUNCE_MS = 300L
        const val DATE_DIGITS = 8

        /** Keeps the editing buffer digits-only, as `DateVisualTransformation` requires. */
        fun dateDigits(raw: String): String = raw.filter(Char::isDigit).take(DATE_DIGITS)

        fun todayDigits(): String {
            val date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val day = date.day.toString().padStart(2, '0')
            val month = date.month.number.toString().padStart(2, '0')
            return "$day$month${date.year}"
        }

        fun parseDate(input: String): LocalDate? {
            if (input.length != DATE_DIGITS || !input.all(Char::isDigit)) return null
            return runCatching {
                LocalDate(
                    year = input.substring(4, 8).toInt(),
                    month = input.substring(2, 4).toInt(),
                    day = input.substring(0, 2).toInt(),
                )
            }.getOrNull()
        }
    }
}
