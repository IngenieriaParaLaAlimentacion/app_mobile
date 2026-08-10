package ar.com.ingenieriaalimentacion.presentation.feature.profile.request_establishment

import ar.com.ingenieriaalimentacion.domain.model.profile.AvailableEstablishment
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentRequestError

data class RequestEstablishmentState(
    val destination: RequestEstablishmentDestination? = null,
    val query: String = "",
    val results: List<AvailableEstablishment> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val isLoading: Boolean = true,       // initial / search reload
    val isLoadingMore: Boolean = false,  // appending next page
    val loadError: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitError: EstablishmentRequestError? = null,
    val successMessage: String? = null,
) {
    val canLoadMore: Boolean get() = page < totalPages && !isLoadingMore && !isLoading
    val selectedCount: Int get() = selectedIds.size
    val canSubmit: Boolean get() = selectedIds.isNotEmpty() && !isSubmitting
    fun isSelected(id: String): Boolean = id in selectedIds
}
