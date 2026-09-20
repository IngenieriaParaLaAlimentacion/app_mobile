package ar.com.ingenieriaalimentacion.presentation.feature.food.establishment_food_movement_history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.core.validation.Validator
import ar.com.ingenieriaalimentacion.core.validation.onChange
import ar.com.ingenieriaalimentacion.core.validation.onFocus
import ar.com.ingenieriaalimentacion.core.validation.showError
import ar.com.ingenieriaalimentacion.data.network.connectivity.ConnectivityObserver
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementError
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementFailure
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementFilters
import ar.com.ingenieriaalimentacion.domain.model.food.Food
import ar.com.ingenieriaalimentacion.domain.model.profile.CurrentEstablishmentResult
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetEstablishmentFoodMovementPageUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.SearchFoodsUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.ResolveCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.SetCurrentEstablishmentUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant

class EstablishmentFoodMovementHistoryViewModel(
    private val resolveCurrentEstablishment: ResolveCurrentEstablishmentUseCase,
    private val setCurrentEstablishment: SetCurrentEstablishmentUseCase,
    private val getPage: GetEstablishmentFoodMovementPageUseCase,
    private val searchFoods: SearchFoodsUseCase,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    private val _state = MutableStateFlow(EstablishmentFoodMovementHistoryState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null
    private var foodSearchJob: Job? = null
    private var firstResumeConsumed = false

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> _state.update { it.copy(isOnline = online) } }
        }
        viewModelScope.launch { resolve(refreshAlways = true) }
    }

    fun onAction(action: EstablishmentFoodMovementHistoryAction) {
        when (action) {
            EstablishmentFoodMovementHistoryAction.BackClick ->
                emit(EstablishmentFoodMovementHistoryDestination.Back)
            EstablishmentFoodMovementHistoryAction.LoadMore -> loadMore()
            EstablishmentFoodMovementHistoryAction.RetryLoad -> retry()
            EstablishmentFoodMovementHistoryAction.FilterOpen ->
                _state.update { it.copy(showFilters = true) }
            EstablishmentFoodMovementHistoryAction.FilterDismiss ->
                _state.update { it.copy(showFilters = false) }
            EstablishmentFoodMovementHistoryAction.FilterApply -> applyFilters()
            EstablishmentFoodMovementHistoryAction.FilterClear -> clearFilters()
            is EstablishmentFoodMovementHistoryAction.DirectionChanged ->
                _state.update { it.copy(filterDraft = it.filterDraft.copy(direction = action.value)) }
            is EstablishmentFoodMovementHistoryAction.FromChanged -> updateFrom(action.value)
            is EstablishmentFoodMovementHistoryAction.FromFocusChanged -> updateFromFocus(action.focused)
            is EstablishmentFoodMovementHistoryAction.ToChanged -> updateTo(action.value)
            is EstablishmentFoodMovementHistoryAction.ToFocusChanged -> updateToFocus(action.focused)
            is EstablishmentFoodMovementHistoryAction.FoodQueryChanged -> updateFoodQuery(action.value)
            is EstablishmentFoodMovementHistoryAction.FoodFocusChanged ->
                _state.update {
                    it.copy(filterDraft = it.filterDraft.copy(foodFocused = action.focused))
                }
            is EstablishmentFoodMovementHistoryAction.FoodSelected -> selectFood(action.food)
            EstablishmentFoodMovementHistoryAction.FoodSearchRetry ->
                searchFood(_state.value.filterDraft.foodQuery)
            EstablishmentFoodMovementHistoryAction.EstablishmentPickerOpen ->
                _state.update { it.copy(showEstablishmentPicker = true) }
            EstablishmentFoodMovementHistoryAction.EstablishmentPickerDismiss ->
                _state.update { it.copy(showEstablishmentPicker = false) }
            is EstablishmentFoodMovementHistoryAction.EstablishmentSelected ->
                selectEstablishment(action.value)
            is EstablishmentFoodMovementHistoryAction.MovementClick ->
                _state.value.establishment?.id?.let {
                    emit(EstablishmentFoodMovementHistoryDestination.Detail(it, action.movementId))
                }
        }
    }

    fun clearDestination() = _state.update { it.copy(destination = null) }

    fun onResume() {
        if (!firstResumeConsumed) {
            firstResumeConsumed = true
            return
        }
        viewModelScope.launch { resolve(refreshAlways = false) }
    }

    private fun applyFilters() {
        val draft = _state.value.filterDraft
        var from = draft.from.showError(optionalDateValidator)
        val to = draft.to.showError(optionalDateValidator)
        val fromDate = parseDate(from.value)
        val toDate = parseDate(to.value)
        val rangeError = fromDate != null && toDate != null && fromDate > toDate
        if (rangeError) {
            from = from.copy(
                error = "La fecha desde debe ser anterior a la fecha hasta.",
                forceShow = true,
            )
        }
        val foodError = if (draft.foodQuery.isNotBlank() && draft.selectedFood == null) {
            "Elegí un alimento de la lista."
        } else {
            null
        }
        _state.update {
            it.copy(filterDraft = draft.copy(from = from, to = to, foodError = foodError))
        }
        if (!from.isValid || !to.isValid || rangeError || foodError != null) return
        val zone = TimeZone.currentSystemDefault()
        val filters = EstablishmentFoodMovementFilters(
            direction = draft.direction,
            occurredFromIso = fromDate?.atStartOfDayIn(zone)?.toString(),
            occurredToIso = toDate?.let {
                LocalDateTime(it, LocalTime(23, 59, 59, 999_999_000)).toInstant(zone).toString()
            },
            foodId = draft.selectedFood?.id,
        )
        _state.update { it.copy(filters = filters, appliedFoodName = draft.selectedFood?.name, showFilters = false) }
        refreshPage1()
    }

    private val optionalDateValidator: Validator = { raw ->
        when {
            raw.isBlank() -> null
            raw.length != DATE_DIGITS -> "Formato esperado DD/MM/AAAA."
            parseDate(raw) == null -> "Fecha inválida."
            else -> null
        }
    }

    private fun updateFrom(raw: String) = _state.update { state ->
        state.copy(
            filterDraft = state.filterDraft.copy(
                from = state.filterDraft.from.onChange(dateDigits(raw), optionalDateValidator),
            ),
        )
    }

    private fun updateFromFocus(focused: Boolean) = _state.update { state ->
        state.copy(
            filterDraft = state.filterDraft.copy(
                from = state.filterDraft.from.onFocus(focused, optionalDateValidator),
            ),
        )
    }

    private fun updateTo(raw: String) = _state.update { state ->
        state.copy(
            filterDraft = state.filterDraft.copy(
                to = state.filterDraft.to.onChange(dateDigits(raw), optionalDateValidator),
            ),
        )
    }

    private fun updateToFocus(focused: Boolean) = _state.update { state ->
        state.copy(
            filterDraft = state.filterDraft.copy(
                to = state.filterDraft.to.onFocus(focused, optionalDateValidator),
            ),
        )
    }

    private fun updateFoodQuery(query: String) {
        _state.update {
            it.copy(
                filterDraft = it.filterDraft.copy(
                    foodQuery = query,
                    selectedFood = null,
                    foodError = null,
                    foodSuggestions = if (query.isBlank()) emptyList() else it.filterDraft.foodSuggestions,
                ),
            )
        }
        foodSearchJob?.cancel()
        if (query.isBlank()) return
        foodSearchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            performFoodSearch(query)
        }
    }

    private fun searchFood(query: String) {
        foodSearchJob?.cancel()
        foodSearchJob = viewModelScope.launch { performFoodSearch(query) }
    }

    private suspend fun performFoodSearch(query: String) {
        _state.update {
            it.copy(
                filterDraft = it.filterDraft.copy(
                    isSearchingFood = true,
                    foodSearchFailed = false,
                ),
            )
        }
        searchFoods(query).fold(
            onSuccess = { result ->
                _state.update {
                    if (it.filterDraft.foodQuery == query) {
                        it.copy(
                            filterDraft = it.filterDraft.copy(
                                foodSuggestions = result.items,
                                foodTotal = result.total,
                                isSearchingFood = false,
                            ),
                        )
                    } else {
                        it
                    }
                }
            },
            onFailure = {
                _state.update {
                    if (it.filterDraft.foodQuery == query) {
                        it.copy(
                            filterDraft = it.filterDraft.copy(
                                isSearchingFood = false,
                                foodSearchFailed = true,
                            ),
                        )
                    } else {
                        it
                    }
                }
            },
        )
    }

    private fun selectFood(food: Food) = _state.update {
        it.copy(
            filterDraft = it.filterDraft.copy(
                foodQuery = food.name,
                selectedFood = food,
                foodSuggestions = emptyList(),
                foodFocused = false,
                foodError = null,
            ),
        )
    }

    private suspend fun resolve(refreshAlways: Boolean) {
        val previousId = _state.value.establishment?.id
        when (val result = resolveCurrentEstablishment()) {
            is CurrentEstablishmentResult.Selected -> {
                _state.update {
                    it.copy(
                        establishment = result.establishment,
                        establishmentOptions = result.options,
                        needsEstablishment = false,
                        staleSelection = false,
                        error = null,
                    )
                }
                if (refreshAlways || result.establishment.id != previousId) refreshPage1()
            }
            is CurrentEstablishmentResult.NoneSelected -> showPicker(result.options, stale = false)
            is CurrentEstablishmentResult.StaleSelection -> showPicker(result.options, stale = true)
            is CurrentEstablishmentResult.LoadFailed -> {
                loadJob?.cancel()
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = EstablishmentFoodMovementHistoryError.EstablishmentsUnavailable,
                    )
                }
            }
        }
    }

    private fun showPicker(options: List<EstablishmentWithStatus>, stale: Boolean) {
        loadJob?.cancel()
        _state.update {
            it.copy(
                establishment = null,
                establishmentOptions = options,
                showEstablishmentPicker = options.isNotEmpty(),
                needsEstablishment = true,
                staleSelection = stale,
                items = emptyList(),
                isLoading = false,
                isLoadingMore = false,
                error = null,
            )
        }
    }

    private fun selectEstablishment(value: EstablishmentWithStatus) {
        _state.update {
            it.copy(
                establishment = value,
                showEstablishmentPicker = false,
                needsEstablishment = false,
                staleSelection = false,
                error = null,
            )
        }
        viewModelScope.launch { setCurrentEstablishment(value) }
        refreshPage1()
    }

    private fun retry() {
        val current = _state.value
        if (
            current.error == EstablishmentFoodMovementHistoryError.EstablishmentsUnavailable ||
            current.establishment == null
        ) {
            _state.update { it.copy(error = null, isLoading = true) }
            viewModelScope.launch { resolve(refreshAlways = true) }
        } else {
            refreshPage1()
        }
    }

    private fun refreshPage1() {
        val establishmentId = _state.value.establishment?.id ?: return
        val filters = _state.value.filters
        loadJob?.cancel()
        _state.update {
            it.copy(
                items = emptyList(),
                page = 1,
                hasMore = true,
                isLoading = true,
                isLoadingMore = false,
                error = null,
            )
        }
        loadJob = viewModelScope.launch {
            getPage(establishmentId, filters, page = 1, pageSize = PAGE_SIZE).fold(
                onSuccess = { page ->
                    _state.update { current ->
                        if (
                            current.establishment?.id == establishmentId &&
                            current.filters == filters
                        ) {
                            current.copy(
                                items = page.items,
                                page = 1,
                                hasMore = page.hasMore,
                                isLoading = false,
                            )
                        } else {
                            current
                        }
                    }
                },
                onFailure = { error ->
                    _state.update { current ->
                        if (
                            current.establishment?.id == establishmentId &&
                            current.filters == filters
                        ) {
                            current.copy(isLoading = false, error = error.toScreenError())
                        } else {
                            current
                        }
                    }
                },
            )
        }
    }

    private fun loadMore() {
        val current = _state.value
        if (!current.canLoadMore) return
        val establishmentId = current.establishment?.id ?: return
        val filters = current.filters
        val nextPage = current.page + 1
        _state.update { it.copy(isLoadingMore = true) }
        loadJob = viewModelScope.launch {
            getPage(establishmentId, filters, nextPage, PAGE_SIZE).fold(
                onSuccess = { page ->
                    _state.update { latest ->
                        if (
                            latest.establishment?.id == establishmentId &&
                            latest.filters == filters && latest.page == nextPage - 1
                        ) {
                            latest.copy(
                                items = latest.items + page.items,
                                page = nextPage,
                                hasMore = page.hasMore,
                                isLoadingMore = false,
                            )
                        } else {
                            latest
                        }
                    }
                },
                onFailure = {
                    _state.update { latest ->
                        if (latest.establishment?.id == establishmentId && latest.filters == filters) {
                            latest.copy(isLoadingMore = false)
                        } else {
                            latest
                        }
                    }
                },
            )
        }
    }

    private fun clearFilters() {
        val hadFilters = _state.value.hasActiveFilters
        _state.update {
            it.copy(
                filters = EstablishmentFoodMovementFilters(),
                appliedFoodName = null,
                filterDraft = MovementFilterDraft(),
                showFilters = false,
            )
        }
        if (hadFilters) refreshPage1()
    }

    private fun emit(destination: EstablishmentFoodMovementHistoryDestination) =
        _state.update { it.copy(destination = destination) }

    private fun Throwable.toScreenError(): EstablishmentFoodMovementHistoryError =
        when ((this as? EstablishmentFoodMovementFailure)?.error) {
            EstablishmentFoodMovementError.EstablishmentAccessDenied ->
                EstablishmentFoodMovementHistoryError.AccessDenied
            EstablishmentFoodMovementError.EstablishmentNotFound ->
                EstablishmentFoodMovementHistoryError.EstablishmentNotFound
            EstablishmentFoodMovementError.Network, null ->
                EstablishmentFoodMovementHistoryError.Retryable
            else -> EstablishmentFoodMovementHistoryError.Terminal
        }

    private companion object {
        const val PAGE_SIZE = 20
        const val DATE_DIGITS = 8
        const val DEBOUNCE_MS = 300L

        fun dateDigits(raw: String) = raw.filter(Char::isDigit).take(DATE_DIGITS)

        fun parseDate(raw: String): LocalDate? {
            if (raw.length != DATE_DIGITS || !raw.all(Char::isDigit)) return null
            return runCatching {
                LocalDate(
                    year = raw.substring(4, 8).toInt(),
                    month = raw.substring(2, 4).toInt(),
                    day = raw.substring(0, 2).toInt(),
                )
            }.getOrNull()
        }
    }
}
