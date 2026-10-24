package ar.com.ingenieriaalimentacion.presentation.feature.food.recipes

import ar.com.ingenieriaalimentacion.domain.model.recipe.*
import ar.com.ingenieriaalimentacion.presentation.common.formatQuantityWithUnit
import kotlin.math.round
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal fun recipeNumber(value: Double): String =
    if (value > 0 && value < 0.05) "menos de 0,1"
    else (round(value * 10) / 10).toString().removeSuffix(".0").replace('.', ',')

internal fun recipeDate(value: String, zone: TimeZone = TimeZone.currentSystemDefault()): String =
    runCatching {
        Instant.parse(value).toLocalDateTime(zone).date.toString()
    }
    .getOrDefault(value.substringBefore('T'))

internal fun mealLabel(value: String) =
    MealType.entries.firstOrNull { it.wireValue == value }?.label ?: value

internal fun nutrientLabel(value: String) =
    when (value) {
        "calcio" -> "Calcio"
        "vitamina_a" -> "Vitamina A"
        "folato" -> "Folato"
        "vitamina_b12" -> "Vitamina B12"
        "vitamina_c" -> "Vitamina C"
        "hierro" -> "Hierro"
        "zinc" -> "Zinc"
        "proteinas" -> "Proteínas"
        "lipidos" -> "Grasas"
        "carbohidratos" -> "Carbohidratos"
        "energy" -> "Energía"
        else -> value.replace('_', ' ')
    }

internal fun gapaLabel(value: String) =
    when (value) {
        "verduras_y_frutas" -> "Verduras y frutas"
        "legumbres_cereales_papa_pan_y_pastas" -> "Legumbres, cereales, papa, pan y pastas"
        "leche_yogur_y_quesos" -> "Leche, yogur y quesos"
        "carnes_y_huevos" -> "Carnes y huevos"
        "aceites_frutas_secas_y_semillas" -> "Aceites, frutos secos y semillas"
        "consumo_opcional" -> "Consumo opcional"
        else -> value.replace('_', ' ')
    }

internal fun shortfallMessage(value: RecipeShortfall): String {
    val amount = "${recipeNumber(value.amount)} ${value.unit}"
    return when (value.kind) {
        "stock_insufficient" -> "Faltan $amount para alcanzar la energía prevista."
        "macro_below_min" -> "${nutrientLabel(value.subject)}: $amount por debajo del objetivo."
        "macro_above_max" -> "${nutrientLabel(value.subject)}: $amount por encima del objetivo."
        "gapa_group_below_min" -> "${gapaLabel(value.subject)}: faltan $amount para el objetivo."
        else -> "Hay un objetivo nutricional sin cumplir ($amount)."
    }
}

internal fun recipeErrorMessage(error: RecipeError, ingredients: List<RecipeIngredient>): String =
    when (error) {
        RecipeError.NoUsableStock ->
            "No hay alimentos utilizables en el stock para armar una receta. Revisá los alimentos disponibles y sus equivalencias."
        RecipeError.AccessDenied ->
            "Ya no tenés acceso aprobado a este establecimiento. Elegí otro en Inicio."
        RecipeError.Unauthorized -> "Tu sesión venció. Volvé a iniciar sesión."
        RecipeError.NotFound ->
            "No encontramos el establecimiento o la receta. Actualizá el historial."
        RecipeError.AlreadyChosen ->
            "Ya se eligió otra propuesta de este pedido. Revisá el historial."
        RecipeError.IdempotencyConflict ->
            "Esta confirmación no coincide con el pedido anterior. Actualizá el historial antes de volver a cocinar."
        RecipeError.InvalidInput ->
            "Revisá la cantidad de chicos y el tipo de comida e intentá de nuevo."
        RecipeError.GenerationFailed ->
            "No pudimos generar las recetas. Reintentá sin cambiar los datos ingresados."
        RecipeError.Network -> "No pudimos comprobar la respuesta. Revisá la conexión y reintentá."
        is RecipeError.InsufficientStock -> {
            val food = ingredients.firstOrNull { it.foodId == error.foodId }
            val available =
                food?.let { formatQuantityWithUnit(error.available, it.unit) } ?: error.available
            val requested =
                food?.let { formatQuantityWithUnit(error.requested, it.unit) } ?: error.requested
            "No alcanza el stock de ${food?.name ?: "un alimento de la receta"}: hay $available y se necesitan $requested. No se registró la salida."
        }
    }

internal fun recipeWarningMessages(warnings: List<String>): List<String> = buildList {
    if ("recipes/cultural-unavailable" in warnings) {
        add("No se obtuvo una receta con inspiración cultural del establecimiento.")
    }
    if ("recipes/fewer-alternatives" in warnings) {
        add(
            "Se obtuvieron menos alternativas de las esperadas. Podés usar las recetas disponibles."
        )
    }
    val nutritionWarnings = warnings.filterNot {
        it == "recipes/cultural-unavailable" || it == "recipes/fewer-alternatives"
    }
    if (nutritionWarnings.any { it.contains("conversion", ignoreCase = true) }) {
        add(
            "Algunos alimentos no se usaron porque falta su equivalencia en gramos. Los indicadores pueden ser parciales."
        )
    }
    if (nutritionWarnings.any { !it.contains("conversion", ignoreCase = true) }) {
        add(
            "Faltan datos de composición de algunos alimentos. Los indicadores nutricionales pueden ser parciales."
        )
    }
}
