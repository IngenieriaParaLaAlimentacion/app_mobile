package ar.com.ingenieriaalimentacion.domain.model.food

data class NewFoodOutflow(
    val id: String,
    val establishmentId: String,
    val occurredAtEpochMillis: Long,
    val comments: String?,
    val items: List<NewFoodOutflowLine>,
)

data class NewFoodOutflowLine(val foodId: String, val quantity: String)
