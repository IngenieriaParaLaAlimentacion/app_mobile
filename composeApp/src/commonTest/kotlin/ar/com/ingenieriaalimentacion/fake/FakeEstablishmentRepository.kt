package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment
import ar.com.ingenieriaalimentacion.domain.repository.EstablishmentRepository

class FakeEstablishmentRepository : EstablishmentRepository {
    var result: Result<List<Establishment>> = Result.success(emptyList())
    var callCount: Int = 0

    /** Search terms forwarded to the repository, in call order — lets tests assert debounced queries. */
    val searches = mutableListOf<String?>()
    val lastSearch: String? get() = searches.lastOrNull()

    override suspend fun getEstablishments(search: String?): Result<List<Establishment>> {
        callCount++
        searches += search
        return result
    }
}
