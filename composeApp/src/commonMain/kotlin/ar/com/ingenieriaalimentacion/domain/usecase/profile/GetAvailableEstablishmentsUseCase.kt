package ar.com.ingenieriaalimentacion.domain.usecase.profile

import ar.com.ingenieriaalimentacion.domain.model.profile.PagedEstablishments
import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository

class GetAvailableEstablishmentsUseCase(private val repository: ProfileRepository) {
    suspend operator fun invoke(
        search: String?,
        page: Int,
        pageSize: Int,
    ): Result<PagedEstablishments> = repository.getAvailableEstablishments(search, page, pageSize)
}
