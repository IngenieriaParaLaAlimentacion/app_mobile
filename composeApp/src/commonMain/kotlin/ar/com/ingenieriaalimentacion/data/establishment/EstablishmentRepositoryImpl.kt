package ar.com.ingenieriaalimentacion.data.establishment

import ar.com.ingenieriaalimentacion.data.establishment.mapper.toDomain
import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment
import ar.com.ingenieriaalimentacion.domain.repository.EstablishmentRepository

class EstablishmentRepositoryImpl(private val api: EstablishmentApi) : EstablishmentRepository {
    override suspend fun getEstablishments(): Result<List<Establishment>> = runCatching {
        api.getEstablishments().items.map { it.toDomain() }
    }
}
