package ar.com.ingenieriaalimentacion.domain.model.food

enum class EstablishmentFoodMovementDirection(val wireValue: String, val label: String) {
    In("in", "Ingresos"),
    Out("out", "Egresos"),
}

data class ResponsibleRef(val id: String, val name: String, val lastName: String) {
    val fullName: String get() = "$name $lastName".trim()
}

data class EstablishmentFoodMovementSummary(
    val id: String,
    val direction: EstablishmentFoodMovementDirection,
    val occurredAtMillis: Long,
    val responsible: ResponsibleRef,
    val foodCount: Int,
    val originType: FoodIntakeOrigin? = null,
    val isRecurring: Boolean? = null,
    val category: FoodOutflowCategory? = null,
)

data class EstablishmentFoodMovementLine(
    val foodId: String,
    val foodName: String,
    val unit: FoodUnit,
    val quantity: String,
)

enum class FoodMovementSource { Manual, Photo }

data class EstablishmentFoodMovementDetail(
    val id: String,
    val direction: EstablishmentFoodMovementDirection,
    val occurredAtMillis: Long,
    val source: FoodMovementSource,
    val comments: String?,
    val responsible: ResponsibleRef,
    val items: List<EstablishmentFoodMovementLine>,
    val originType: FoodIntakeOrigin? = null,
    val isRecurring: Boolean? = null,
    val category: FoodOutflowCategory? = null,
)

data class EstablishmentFoodMovementFilters(
    val direction: EstablishmentFoodMovementDirection? = null,
    val occurredFromIso: String? = null,
    val occurredToIso: String? = null,
    val foodId: String? = null,
)

data class EstablishmentFoodMovementPage(
    val items: List<EstablishmentFoodMovementSummary>,
    val hasMore: Boolean,
)

sealed interface EstablishmentFoodMovementError {
    data object Unauthorized : EstablishmentFoodMovementError
    data object EstablishmentAccessDenied : EstablishmentFoodMovementError
    data object EstablishmentNotFound : EstablishmentFoodMovementError
    data object MovementNotFound : EstablishmentFoodMovementError
    data object InvalidRequest : EstablishmentFoodMovementError
    data object Network : EstablishmentFoodMovementError
}

class EstablishmentFoodMovementFailure(
    val error: EstablishmentFoodMovementError,
    cause: Throwable? = null,
) : Exception(error.toString(), cause)
