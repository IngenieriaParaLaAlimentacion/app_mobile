package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementDetail
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementFilters
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementPage
import ar.com.ingenieriaalimentacion.domain.repository.EstablishmentFoodMovementRepository

class FakeEstablishmentFoodMovementRepository : EstablishmentFoodMovementRepository {
    data class PageRequest(
        val establishmentId: String,
        val filters: EstablishmentFoodMovementFilters,
        val page: Int,
        val pageSize: Int,
    )

    data class DetailRequest(val establishmentId: String, val movementId: String)

    val pageRequests = mutableListOf<PageRequest>()
    val detailRequests = mutableListOf<DetailRequest>()
    var pageResults: List<Result<EstablishmentFoodMovementPage>> = emptyList()
    var detailResults: List<Result<EstablishmentFoodMovementDetail>> = emptyList()
    private var pageIndex = 0
    private var detailIndex = 0

    override suspend fun getPage(
        establishmentId: String,
        filters: EstablishmentFoodMovementFilters,
        page: Int,
        pageSize: Int,
    ): Result<EstablishmentFoodMovementPage> {
        pageRequests += PageRequest(establishmentId, filters, page, pageSize)
        return pageResults[pageIndex++]
    }

    override suspend fun getDetail(
        establishmentId: String,
        movementId: String,
    ): Result<EstablishmentFoodMovementDetail> {
        detailRequests += DetailRequest(establishmentId, movementId)
        return detailResults[detailIndex++]
    }
}
