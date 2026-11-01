package ar.com.ingenieriaalimentacion.data.subject.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubjectSummaryResponse(
    val id: String,
    val dni: Int,
    val name: String,
    @SerialName("last_name") val lastName: String,
    val sex: SexDto,
    @SerialName("birth_date") val birthDate: String,
)

@Serializable
data class PaginatedSubjectsResponse(
    val items: List<SubjectSummaryResponse>,
    val total: Int,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    @SerialName("total_pages") val totalPages: Int,
)
