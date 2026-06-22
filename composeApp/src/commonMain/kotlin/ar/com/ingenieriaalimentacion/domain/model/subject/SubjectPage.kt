package ar.com.ingenieriaalimentacion.domain.model.subject

data class SubjectPage(
    val items: List<SubjectSummary>,
    val page: Int,
    val totalPages: Int,
)
