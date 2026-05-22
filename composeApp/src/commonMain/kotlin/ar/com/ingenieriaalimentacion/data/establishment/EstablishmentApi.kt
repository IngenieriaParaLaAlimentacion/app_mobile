package ar.com.ingenieriaalimentacion.data.establishment

import ar.com.ingenieriaalimentacion.data.establishment.dto.PaginatedEstablishmentsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * Establishments listing endpoint. Unauthenticated, so it is constructed against the
 * no-auth refresh [HttpClient] (Koin qualifier `"refresh"`).
 */
class EstablishmentApi(private val refreshClient: HttpClient) {

    suspend fun getEstablishments(pageSize: Int = DEFAULT_PAGE_SIZE): PaginatedEstablishmentsResponse =
        refreshClient.get("api/v1/establishments") {
            parameter("page", 1)
            parameter("page_size", pageSize)
        }.body()

    private companion object {
        const val DEFAULT_PAGE_SIZE = 100
    }
}
