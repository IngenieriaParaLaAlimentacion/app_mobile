package ar.com.ingenieriaalimentacion.presentation.feature.subject.select_subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.domain.usecase.subject.GetSubjectsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(FlowPreview::class)
class SelectSubjectViewModel(
    private val getSubjects: GetSubjectsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SelectSubjectState())
    val state = _state.asStateFlow()

    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch {
            query
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { search(it) }
        }
    }

    fun onAction(action: SelectSubjectAction) {
        when (action) {
            is SelectSubjectAction.QueryChanged -> {
                _state.update { it.copy(query = action.value) }
                query.value = action.value
            }

            is SelectSubjectAction.SubjectClicked ->
                _state.update {
                    it.copy(
                        destination = SelectSubjectDestination.Confirm(
                            Json.encodeToString(action.subject),
                        ),
                    )
                }

            SelectSubjectAction.LoadMore -> loadMore()

            SelectSubjectAction.BackClick ->
                _state.update { it.copy(destination = SelectSubjectDestination.Back) }

            SelectSubjectAction.RetryClick ->
                viewModelScope.launch { search(_state.value.query) }

            SelectSubjectAction.CreateSubjectClick ->
                _state.update {
                    it.copy(destination = SelectSubjectDestination.CreateSubject(it.createDniPrefill))
                }
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }

    private suspend fun search(value: String) {
        _state.update { it.copy(isLoading = true, loadError = false) }
        getSubjects(value.takeIf { it.isNotBlank() }, page = 1).fold(
            onSuccess = { p ->
                _state.update {
                    it.copy(
                        results = p.items,
                        page = p.page,
                        totalPages = p.totalPages,
                        isLoading = false,
                    )
                }
            },
            onFailure = { t ->
                if (t is CancellationException) throw t
                _state.update { it.copy(isLoading = false, loadError = true) }
            },
        )
    }

    private fun loadMore() {
        val current = _state.value
        if (!current.canLoadMore) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            getSubjects(current.query.takeIf { it.isNotBlank() }, page = current.page + 1).fold(
                onSuccess = { p ->
                    _state.update {
                        it.copy(
                            results = it.results + p.items,
                            page = p.page,
                            totalPages = p.totalPages,
                            isLoadingMore = false,
                        )
                    }
                },
                onFailure = { t ->
                    if (t is CancellationException) throw t
                    // conserva la lista; el fin se reintenta al scrollear
                    _state.update { it.copy(isLoadingMore = false) }
                },
            )
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
