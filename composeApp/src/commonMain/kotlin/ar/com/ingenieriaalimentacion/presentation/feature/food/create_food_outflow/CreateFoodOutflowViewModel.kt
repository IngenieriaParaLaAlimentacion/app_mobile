package ar.com.ingenieriaalimentacion.presentation.feature.food.create_food_outflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.core.validation.Rules
import ar.com.ingenieriaalimentacion.core.validation.TextFieldState
import ar.com.ingenieriaalimentacion.core.validation.Validator
import ar.com.ingenieriaalimentacion.core.validation.onChange
import ar.com.ingenieriaalimentacion.core.validation.onFocus
import ar.com.ingenieriaalimentacion.core.validation.showError
import ar.com.ingenieriaalimentacion.data.network.connectivity.ConnectivityObserver
import ar.com.ingenieriaalimentacion.domain.model.food.FoodOutflowError
import ar.com.ingenieriaalimentacion.domain.model.food.FoodOutflowFailure
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockFilters
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockItem
import ar.com.ingenieriaalimentacion.domain.model.food.FoodUnit
import ar.com.ingenieriaalimentacion.domain.model.food.NewFoodOutflow
import ar.com.ingenieriaalimentacion.domain.model.food.NewFoodOutflowLine
import ar.com.ingenieriaalimentacion.domain.model.food.StockSortOrder
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.usecase.food.CreateFoodOutflowUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetFoodCategoriesUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetFoodStockPageUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.GetMyEstablishmentsUseCase
import ar.com.ingenieriaalimentacion.domain.util.IdGenerator
import ar.com.ingenieriaalimentacion.domain.util.sanitizeIntegerInput
import ar.com.ingenieriaalimentacion.domain.util.sanitizeQuantityInput
import ar.com.ingenieriaalimentacion.presentation.common.formatQuantity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class CreateFoodOutflowViewModel(
    private val createFoodOutflow: CreateFoodOutflowUseCase,
    private val getFoodStockPage: GetFoodStockPageUseCase,
    private val getFoodCategories: GetFoodCategoriesUseCase,
    private val getMyEstablishments: GetMyEstablishmentsUseCase,
    private val getCurrentEstablishment: GetCurrentEstablishmentUseCase,
    connectivity: ConnectivityObserver,
    private val idGenerator: IdGenerator,
) : ViewModel() {
    private val _state = MutableStateFlow(
        CreateFoodOutflowState(
            outflowId = idGenerator.newId(),
            occurredAt = TextFieldState(value = todayDigits()),
        ),
    )
    val state = _state.asStateFlow()
    private var stockJob: Job? = null

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online ->
                _state.update { it.copy(isOnline = online) }
                if (online && _state.value.selectedEstablishment != null && _state.value.stock.items.isEmpty()) {
                    refreshStock()
                }
            }
        }
        viewModelScope.launch {
            getFoodCategories().onSuccess { categories ->
                _state.update { it.copy(stock = it.stock.copy(categories = categories)) }
            }
        }
        loadEstablishments()
    }

    fun onAction(action: CreateFoodOutflowAction) {
        when (action) {
            CreateFoodOutflowAction.BackClick ->
                _state.update { it.copy(destination = CreateFoodOutflowDestination.Back) }
            CreateFoodOutflowAction.RetryProfile -> loadEstablishments()
            CreateFoodOutflowAction.EstablishmentPickerOpen ->
                _state.update { it.copy(showEstablishmentPicker = true) }
            CreateFoodOutflowAction.EstablishmentPickerDismiss ->
                _state.update { it.copy(showEstablishmentPicker = false) }
            is CreateFoodOutflowAction.EstablishmentSelected -> selectEstablishment(action.value)
            is CreateFoodOutflowAction.OccurredAtChanged -> _state.update {
                it.copy(
                    occurredAt = it.occurredAt.onChange(dateDigits(action.value), occurredAtValidator),
                    successMessage = null,
                )
            }
            is CreateFoodOutflowAction.OccurredAtFocusChanged -> _state.update {
                it.copy(occurredAt = it.occurredAt.onFocus(action.focused, occurredAtValidator))
            }
            is CreateFoodOutflowAction.CommentsChanged -> _state.update {
                it.copy(
                    comments = it.comments.onChange(action.value, commentsValidator),
                    successMessage = null,
                )
            }
            is CreateFoodOutflowAction.CommentsFocusChanged -> _state.update {
                it.copy(comments = it.comments.onFocus(action.focused, commentsValidator))
            }
            CreateFoodOutflowAction.StockPickerOpen -> {
                _state.update { it.copy(stock = it.stock.copy(showPicker = true)) }
                if (_state.value.stock.items.isEmpty()) refreshStock()
            }
            CreateFoodOutflowAction.StockPickerDismiss ->
                _state.update { it.copy(stock = it.stock.copy(showPicker = false)) }
            CreateFoodOutflowAction.CategoryPickerOpen ->
                _state.update {
                    it.copy(stock = it.stock.copy(showPicker = false, showCategoryPicker = true))
                }
            CreateFoodOutflowAction.CategoryPickerDismiss ->
                _state.update {
                    it.copy(stock = it.stock.copy(showPicker = true, showCategoryPicker = false))
                }
            is CreateFoodOutflowAction.CategorySelected -> {
                if (action.id != _state.value.stock.selectedCategoryId) {
                    _state.update {
                        it.copy(
                            stock = it.stock.copy(
                                selectedCategoryId = action.id,
                                showPicker = true,
                                showCategoryPicker = false,
                            ),
                        )
                    }
                    refreshStock()
                } else {
                    _state.update {
                        it.copy(stock = it.stock.copy(showPicker = true, showCategoryPicker = false))
                    }
                }
            }
            CreateFoodOutflowAction.RetryStock -> refreshStock()
            CreateFoodOutflowAction.LoadMoreStock -> loadMoreStock()
            is CreateFoodOutflowAction.FoodPicked -> pickFood(action.item)
            is CreateFoodOutflowAction.LineQuantityChanged -> updateLine(action.foodId) { line ->
                val value = when (line.stock.unit) {
                    FoodUnit.Unidades -> sanitizeIntegerInput(action.value)
                    else -> sanitizeQuantityInput(action.value)
                }
                line.copy(quantity = line.quantity.onChange(value, validatorFor(line)))
            }
            is CreateFoodOutflowAction.LineQuantityFocusChanged -> updateLine(action.foodId) { line ->
                line.copy(quantity = line.quantity.onFocus(action.focused, validatorFor(line)))
            }
            is CreateFoodOutflowAction.LineRemoved -> _state.update {
                it.copy(lines = it.lines.filterNot { line -> line.stock.foodId == action.foodId })
            }
            CreateFoodOutflowAction.Submit -> submit()
            CreateFoodOutflowAction.DismissSuccess -> _state.update { it.copy(successMessage = null) }
        }
    }

    fun clearDestination() = _state.update { it.copy(destination = null) }

    private fun loadEstablishments() {
        _state.update { it.copy(isLoadingProfile = true, profileError = null) }
        viewModelScope.launch {
            getMyEstablishments().fold(
                onSuccess = { all ->
                    val approved = all.filter { it.status == EstablishmentStatus.Approved }
                    val selected = getCurrentEstablishment(approved) ?: approved.singleOrNull()
                    _state.update {
                        it.copy(
                            isLoadingProfile = false,
                            establishmentOptions = approved,
                            selectedEstablishment = selected,
                        )
                    }
                    if (selected != null && _state.value.isOnline) refreshStock()
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

    private fun selectEstablishment(value: EstablishmentWithStatus) {
        if (_state.value.selectedEstablishment?.id == value.id) {
            _state.update { it.copy(showEstablishmentPicker = false) }
            return
        }
        stockJob?.cancel()
        _state.update {
            it.copy(
                selectedEstablishment = value,
                showEstablishmentPicker = false,
                lines = emptyList(),
                stock = it.stock.copy(items = emptyList(), page = 1, hasMore = false, error = null),
                submitError = null,
                successMessage = null,
            )
        }
        refreshStock()
    }

    private fun refreshStock() {
        val establishment = _state.value.selectedEstablishment ?: return
        if (!_state.value.isOnline) return
        val category = _state.value.stock.selectedCategoryId
        stockJob?.cancel()
        _state.update {
            it.copy(
                stock = it.stock.copy(
                    items = emptyList(),
                    page = 1,
                    hasMore = false,
                    isLoading = true,
                    isLoadingMore = false,
                    error = null,
                ),
            )
        }
        stockJob = viewModelScope.launch {
            getFoodStockPage(
                establishment.id,
                FoodStockFilters(
                    onlyAvailable = true,
                    categoryId = category,
                    sortOrder = StockSortOrder.NameAsc,
                ),
                page = 1,
                pageSize = PAGE_SIZE,
            ).fold(
                onSuccess = { page ->
                    _state.update {
                        it.copy(
                            stock = it.stock.copy(
                                items = page.items,
                                page = 1,
                                hasMore = page.hasMore,
                                isLoading = false,
                                error = null,
                            ),
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(
                            stock = it.stock.copy(
                                isLoading = false,
                                error = "No pudimos cargar los alimentos disponibles.",
                            ),
                        )
                    }
                },
            )
        }
    }

    private fun loadMoreStock() {
        val current = _state.value
        val establishment = current.selectedEstablishment ?: return
        if (
            !current.isOnline || current.stock.isLoading || current.stock.isLoadingMore ||
            !current.stock.hasMore || current.stock.error != null
        ) return
        val next = current.stock.page + 1
        _state.update { it.copy(stock = it.stock.copy(isLoadingMore = true)) }
        stockJob = viewModelScope.launch {
            getFoodStockPage(
                establishment.id,
                FoodStockFilters(
                    onlyAvailable = true,
                    categoryId = current.stock.selectedCategoryId,
                    sortOrder = StockSortOrder.NameAsc,
                ),
                next,
                PAGE_SIZE,
            ).fold(
                onSuccess = { page ->
                    _state.update {
                        it.copy(
                            stock = it.stock.copy(
                                items = it.stock.items + page.items,
                                page = next,
                                hasMore = page.hasMore,
                                isLoadingMore = false,
                            ),
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(stock = it.stock.copy(isLoadingMore = false)) }
                },
            )
        }
    }

    private fun pickFood(item: FoodStockItem) = _state.update { current ->
        val exists = current.lines.any { it.stock.foodId == item.foodId }
        current.copy(
            lines = if (exists) current.lines else current.lines + FoodOutflowLine(item),
            stock = current.stock.copy(showPicker = false),
            successMessage = null,
        )
    }

    private inline fun updateLine(
        foodId: String,
        crossinline transform: (FoodOutflowLine) -> FoodOutflowLine,
    ) {
        _state.update { current ->
            current.copy(
                lines = current.lines.map { if (it.stock.foodId == foodId) transform(it) else it },
                successMessage = null,
            )
        }
    }

    private fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        val validated = current.copy(
            occurredAt = current.occurredAt.showError(occurredAtValidator),
            comments = current.comments.showError(commentsValidator),
            lines = current.lines.map { it.copy(quantity = it.quantity.showError(validatorFor(it))) },
        )
        val outflow = buildOutflow(validated)
        if (outflow == null) {
            _state.value = validated
            return
        }
        _state.value = validated.copy(isSubmitting = true, submitError = null, successMessage = null)
        viewModelScope.launch {
            createFoodOutflow(outflow).fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            outflowId = idGenerator.newId(),
                            lines = emptyList(),
                            comments = TextFieldState(),
                            occurredAt = TextFieldState(value = todayDigits()),
                            isSubmitting = false,
                            submitError = null,
                            successMessage = "Egreso registrado",
                        )
                    }
                    refreshStock()
                },
                onFailure = { throwable ->
                    if (throwable is CancellationException) throw throwable
                    val error = (throwable as? FoodOutflowFailure)?.error
                        ?: FoodOutflowError.Unknown(throwable)
                    if (error is FoodOutflowError.InsufficientStock) {
                        _state.update { state ->
                            state.copy(
                                lines = state.lines.map { line ->
                                    if (line.stock.foodId == error.foodId) {
                                        line.copy(stock = line.stock.copy(quantity = error.available))
                                    } else {
                                        line
                                    }
                                },
                            )
                        }
                        refreshStock()
                    }
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            submitError = error.toUserMessage(it.lines),
                        )
                    }
                },
            )
        }
    }

    private fun buildOutflow(state: CreateFoodOutflowState): NewFoodOutflow? {
        val establishment = state.selectedEstablishment ?: return null
        val date = parseDate(state.occurredAt.value) ?: return null
        if (
            !state.occurredAt.isValid || !state.comments.isValid ||
            state.lines.isEmpty() || state.lines.any { !it.quantity.isValid }
        ) return null
        return NewFoodOutflow(
            id = state.outflowId,
            establishmentId = establishment.id,
            occurredAtEpochMillis = occurredAtMillis(date),
            comments = state.comments.value.trim().takeIf(String::isNotBlank),
            items = state.lines.map {
                NewFoodOutflowLine(it.stock.foodId, it.quantity.value.trimEnd('.'))
            },
        )
    }

    private fun validatorFor(line: FoodOutflowLine): Validator = { raw ->
        val normalized = raw.trimEnd('.')
        val entered = decimalThousandths(normalized)
        val available = decimalThousandths(line.stock.quantity)
        when {
            raw.isBlank() -> "Ingresá la cantidad."
            line.stock.unit == FoodUnit.Unidades && normalized.toLongOrNull() == null ->
                "Ingresá una cantidad entera mayor a 0."
            entered == null || entered <= 0L -> "Ingresá una cantidad mayor a 0."
            available == null || entered > available ->
                "Disponible: ${formatQuantity(line.stock.quantity)}."
            else -> null
        }
    }

    private fun decimalThousandths(raw: String): Long? {
        val parts = raw.split('.', limit = 2)
        val whole = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val fraction = parts.getOrNull(1).orEmpty().padEnd(3, '0').take(3)
        if (fraction.any { !it.isDigit() }) return null
        return whole * 1_000 + fraction.ifEmpty { "0" }.toLong()
    }

    private val commentsValidator = Rules.of(
        Rules.maxLength(MAX_OUTFLOW_COMMENTS, "Máximo $MAX_OUTFLOW_COMMENTS caracteres."),
    )
    private val occurredAtValidator: Validator = { raw ->
        val date = parseDate(raw)
        when {
            raw.isBlank() -> "Ingresá la fecha."
            raw.length != DATE_DIGITS -> "Formato esperado DD/MM/AAAA."
            date == null -> "Fecha inválida."
            date > today() -> "La fecha no puede ser futura."
            else -> null
        }
    }

    private fun occurredAtMillis(date: LocalDate): Long {
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        return if (date == now.toLocalDateTime(zone).date) {
            now.toEpochMilliseconds()
        } else {
            date.atStartOfDayIn(zone).toEpochMilliseconds()
        }
    }

    private fun today() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    private companion object {
        const val PAGE_SIZE = 20
        const val DATE_DIGITS = 8

        fun dateDigits(raw: String) = raw.filter(Char::isDigit).take(DATE_DIGITS)

        fun todayDigits(): String {
            val date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            return date.day.toString().padStart(2, '0') +
                date.month.number.toString().padStart(2, '0') + date.year
        }

        fun parseDate(raw: String): LocalDate? =
            if (raw.length != DATE_DIGITS || !raw.all(Char::isDigit)) {
                null
            } else {
                runCatching {
                    LocalDate(
                        raw.substring(4).toInt(),
                        raw.substring(2, 4).toInt(),
                        raw.substring(0, 2).toInt(),
                    )
                }.getOrNull()
            }
    }
}
