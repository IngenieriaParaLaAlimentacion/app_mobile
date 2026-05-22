package ar.com.ingenieriaalimentacion.domain.usecase.establishment

import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment
import ar.com.ingenieriaalimentacion.domain.repository.EstablishmentRepository

class GetEstablishmentsUseCase(private val repository: EstablishmentRepository) {
    suspend operator fun invoke(search: String? = null): Result<List<Establishment>> =
        repository.getEstablishments(search)
}
