package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment
import ar.com.ingenieriaalimentacion.domain.repository.EstablishmentRepository

class FakeEstablishmentRepository : EstablishmentRepository {
    var result: Result<List<Establishment>> = Result.success(emptyList())
    var callCount: Int = 0

    override suspend fun getEstablishments(): Result<List<Establishment>> {
        callCount++
        return result
    }
}
