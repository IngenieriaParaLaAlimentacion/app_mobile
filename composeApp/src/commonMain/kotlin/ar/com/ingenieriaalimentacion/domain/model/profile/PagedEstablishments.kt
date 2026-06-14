package ar.com.ingenieriaalimentacion.domain.model.profile

data class PagedEstablishments(
    val items: List<AvailableEstablishment>,
    val page: Int,
    val totalPages: Int,
    val total: Int,
)
