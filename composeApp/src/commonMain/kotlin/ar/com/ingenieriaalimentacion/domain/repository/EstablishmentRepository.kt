package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment

interface EstablishmentRepository {
    suspend fun getEstablishments(search: String? = null): Result<List<Establishment>>
}
