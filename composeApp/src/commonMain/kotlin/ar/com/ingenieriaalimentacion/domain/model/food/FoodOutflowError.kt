package ar.com.ingenieriaalimentacion.domain.model.food

sealed interface FoodOutflowError {
    data class InsufficientStock(
        val foodId: String,
        val requested: String,
        val available: String,
    ) : FoodOutflowError

    data object Unauthorized : FoodOutflowError
    data object EstablishmentAccessDenied : FoodOutflowError
    data object EstablishmentNotFound : FoodOutflowError
    data object FoodNotFound : FoodOutflowError
    data object InvalidData : FoodOutflowError
    data object Network : FoodOutflowError
    data class Unknown(val cause: Throwable?) : FoodOutflowError
}

class FoodOutflowFailure(val error: FoodOutflowError, cause: Throwable?) :
    Exception(error.toString(), cause)
