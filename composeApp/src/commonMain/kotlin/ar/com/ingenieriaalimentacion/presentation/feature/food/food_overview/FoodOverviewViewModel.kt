package ar.com.ingenieriaalimentacion.presentation.feature.food.food_overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.data.network.connectivity.ConnectivityObserver
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementError
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementFailure
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementFilters
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockError
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockFailure
import ar.com.ingenieriaalimentacion.domain.model.food.FoodStockFilters
import ar.com.ingenieriaalimentacion.domain.model.profile.CurrentEstablishmentResult
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetEstablishmentFoodMovementPageUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetFoodCategoriesUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.food.GetFoodStockPageUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.ResolveCurrentEstablishmentUseCase
import ar.com.ingenieriaalimentacion.presentation.feature.food.receipt_food_intake.ReceiptCaptureSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FoodOverviewViewModel(
    private val resolveCurrentEstablishment: ResolveCurrentEstablishmentUseCase,
    private val getStockPage: GetFoodStockPageUseCase,
    private val getMovementPage: GetEstablishmentFoodMovementPageUseCase,
    private val getFoodCategories: GetFoodCategoriesUseCase,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    private val _state = MutableStateFlow(FoodOverviewState())
    val state = _state.asStateFlow()
    private var stockJob: Job? = null
    private var movementJob: Job? = null
    private var stockRequestId = 0L
    private var movementRequestId = 0L

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> _state.update { it.copy(isOnline = online) } }
        }
        viewModelScope.launch {
            getFoodCategories().onSuccess { categories ->
                _state.update { it.copy(stock = it.stock.copy(categories = categories)) }
            }
        }
        resolveAndLoad()
    }

    fun onAction(action: FoodOverviewAction) {
        when (action) {
            FoodOverviewAction.RecipesClick -> if (_state.value.establishment != null) emit(FoodOverviewDestination.Recipes)
            FoodOverviewAction.BackClick -> emit(FoodOverviewDestination.Back)
            FoodOverviewAction.IntakeClick -> emit(FoodOverviewDestination.CreateIntake)
            FoodOverviewAction.ReceiptIntakeClick ->
                _state.update { it.copy(showReceiptSourceSheet = true) }
            FoodOverviewAction.ReceiptSourceSheetDismiss ->
                _state.update { it.copy(showReceiptSourceSheet = false) }
            FoodOverviewAction.ReceiptCameraClick -> selectReceiptSource(ReceiptCaptureSource.Camera)
            FoodOverviewAction.ReceiptGalleryClick -> selectReceiptSource(ReceiptCaptureSource.Gallery)
            FoodOverviewAction.OutflowClick -> emit(FoodOverviewDestination.CreateOutflow)
            FoodOverviewAction.SeeAllMovementsClick -> emit(FoodOverviewDestination.MovementHistory)
            is FoodOverviewAction.MovementClick -> _state.value.establishment?.let {
                emit(FoodOverviewDestination.MovementDetail(it.id, action.movementId))
            }
            FoodOverviewAction.RetryAll -> resolveAndLoad()
            FoodOverviewAction.RetryStock -> refreshStock()
            FoodOverviewAction.RetryMovements -> refreshMovements()
            FoodOverviewAction.SeeAllStockClick -> emit(FoodOverviewDestination.Stock)
            is FoodOverviewAction.CategorySelected -> {
                updateStock { it.copy(showCategorySheet = false) }
                updateFilters { it.copy(categoryId = action.categoryId) }
            }
            is FoodOverviewAction.SortSelected -> {
                updateStock { it.copy(showSortSheet = false) }
                updateFilters { it.copy(sortOrder = action.sortOrder) }
            }
            FoodOverviewAction.CategorySheetOpen -> updateStock { it.copy(showCategorySheet = true) }
            FoodOverviewAction.CategorySheetDismiss -> updateStock { it.copy(showCategorySheet = false) }
            FoodOverviewAction.SortSheetOpen -> updateStock { it.copy(showSortSheet = true) }
            FoodOverviewAction.SortSheetDismiss -> updateStock { it.copy(showSortSheet = false) }
        }
    }

    fun clearDestination() = _state.update { it.copy(destination = null) }

    fun refreshAfterMutation() {
        refreshStock()
        refreshMovements()
    }

    private fun resolveAndLoad() {
        viewModelScope.launch {
            _state.update { it.copy(isResolvingEstablishment = true, establishmentProblem = null) }
            when (val result = resolveCurrentEstablishment()) {
                is CurrentEstablishmentResult.Selected -> {
                    _state.update {
                        it.copy(
                            establishment = result.establishment,
                            isResolvingEstablishment = false,
                            establishmentProblem = null,
                        )
                    }
                    refreshStock()
                    refreshMovements()
                }
                is CurrentEstablishmentResult.NoneSelected ->
                    showScopeProblem(EstablishmentProblem.NoneSelected)
                is CurrentEstablishmentResult.StaleSelection ->
                    showScopeProblem(EstablishmentProblem.StaleSelection)
                is CurrentEstablishmentResult.LoadFailed ->
                    showScopeProblem(EstablishmentProblem.LoadFailed)
            }
        }
    }

    private fun showScopeProblem(problem: EstablishmentProblem) {
        stockJob?.cancel()
        movementJob?.cancel()
        stockRequestId++
        movementRequestId++
        _state.update {
            it.copy(
                establishment = null,
                isResolvingEstablishment = false,
                establishmentProblem = problem,
                stock = it.stock.copy(
                    items = emptyList(),
                    isLoading = false,
                    error = null,
                ),
                movements = it.movements.copy(
                    items = emptyList(),
                    isLoading = false,
                    error = null,
                ),
            )
        }
    }

    private fun updateFilters(change: (FoodStockFilters) -> FoodStockFilters) {
        val next = change(_state.value.stock.filters)
        if (next == _state.value.stock.filters) return
        updateStock { it.copy(filters = next) }
        refreshStock()
    }

    private fun refreshStock() {
        val establishmentId = _state.value.establishment?.id ?: return
        val filters = _state.value.stock.filters
        stockJob?.cancel()
        val requestId = ++stockRequestId
        updateStock {
            it.copy(
                items = emptyList(),
                isLoading = true,
                error = null,
            )
        }
        stockJob = viewModelScope.launch {
            getStockPage(establishmentId, filters, 1, STOCK_PREVIEW_SIZE).fold(
                onSuccess = { page ->
                    updateStockIfCurrent(establishmentId, filters, requestId) {
                        it.copy(
                            items = page.items.take(STOCK_PREVIEW_SIZE),
                            isLoading = false,
                            error = null,
                        )
                    }
                },
                onFailure = { error ->
                    updateStockIfCurrent(establishmentId, filters, requestId) {
                        it.copy(isLoading = false, error = error.toStockSectionError())
                    }
                },
            )
        }
    }

    private fun refreshMovements() {
        val establishmentId = _state.value.establishment?.id ?: return
        movementJob?.cancel()
        val requestId = ++movementRequestId
        _state.update {
            it.copy(movements = it.movements.copy(isLoading = true, error = null))
        }
        movementJob = viewModelScope.launch {
            getMovementPage(
                establishmentId,
                EstablishmentFoodMovementFilters(),
                page = 1,
                pageSize = MOVEMENT_PREVIEW_SIZE,
            ).fold(
                onSuccess = { page ->
                    _state.update { current ->
                        if (
                            current.establishment?.id == establishmentId &&
                            movementRequestId == requestId
                        ) {
                            current.copy(
                                movements = current.movements.copy(
                                    items = page.items,
                                    isLoading = false,
                                    error = null,
                                ),
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
                            movementRequestId == requestId
                        ) {
                            current.copy(
                                movements = current.movements.copy(
                                    isLoading = false,
                                    error = error.toMovementSectionError(),
                                ),
                            )
                        } else {
                            current
                        }
                    }
                },
            )
        }
    }

    private fun emit(destination: FoodOverviewDestination) =
        _state.update { it.copy(destination = destination) }

    private fun selectReceiptSource(source: ReceiptCaptureSource) =
        _state.update {
            it.copy(showReceiptSourceSheet = false,
                destination = FoodOverviewDestination.ReceiptIntake(source))
        }

    private fun updateStock(change: (FoodOverviewStockState) -> FoodOverviewStockState) =
        _state.update { it.copy(stock = change(it.stock)) }

    private fun updateStockIfCurrent(
        establishmentId: String,
        filters: FoodStockFilters,
        requestId: Long,
        change: (FoodOverviewStockState) -> FoodOverviewStockState,
    ) {
        _state.update { current ->
            if (
                current.establishment?.id == establishmentId &&
                current.stock.filters == filters &&
                stockRequestId == requestId
            ) {
                current.copy(stock = change(current.stock))
            } else {
                current
            }
        }
    }

    private fun Throwable.toStockSectionError() = when ((this as? FoodStockFailure)?.error) {
        FoodStockError.EstablishmentAccessDenied -> FoodOverviewSectionError.AccessDenied
        FoodStockError.EstablishmentNotFound -> FoodOverviewSectionError.EstablishmentNotFound
        FoodStockError.InvalidRequest, FoodStockError.Unauthorized -> FoodOverviewSectionError.Terminal
        FoodStockError.Network, null -> FoodOverviewSectionError.Retryable
    }

    private fun Throwable.toMovementSectionError() =
        when ((this as? EstablishmentFoodMovementFailure)?.error) {
            EstablishmentFoodMovementError.EstablishmentAccessDenied ->
                FoodOverviewSectionError.AccessDenied
            EstablishmentFoodMovementError.EstablishmentNotFound ->
                FoodOverviewSectionError.EstablishmentNotFound
            EstablishmentFoodMovementError.Network, null -> FoodOverviewSectionError.Retryable
            else -> FoodOverviewSectionError.Terminal
        }

    private companion object {
        const val MOVEMENT_PREVIEW_SIZE = 5
    }
}
