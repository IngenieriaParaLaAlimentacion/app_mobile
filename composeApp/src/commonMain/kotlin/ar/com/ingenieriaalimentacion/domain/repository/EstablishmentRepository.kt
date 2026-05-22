package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment

interface EstablishmentRepository {
    suspend fun getEstablishments(): Result<List<Establishment>>
}
