package ar.com.ingenieriaalimentacion.presentation.feature.establishment.select_establishment

import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment

data class SelectEstablishmentState(
    val destination: SelectEstablishmentDestination? = null,
    val query: String = "",
    val results: List<Establishment> = emptyList(),
    val selected: List<Establishment> = emptyList(),
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    /** One-tap mode: a tap confirms immediately, so there is no *Listo* button to show. */
    val singleSelect: Boolean = false,
) {
    fun isSelected(id: String): Boolean = selected.any { it.id == id }
    val selectedCount: Int get() = selected.size
}
