package ar.com.ingenieriaalimentacion.data.establishment

import ar.com.ingenieriaalimentacion.data.establishment.mapper.toDomain
import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment
import ar.com.ingenieriaalimentacion.domain.repository.EstablishmentRepository

class EstablishmentRepositoryImpl(private val api: EstablishmentApi) : EstablishmentRepository {
    override suspend fun getEstablishments(search: String?): Result<List<Establishment>> = runCatching {
        api.getEstablishments(search = search).items.map { it.toDomain() }
    }
}
