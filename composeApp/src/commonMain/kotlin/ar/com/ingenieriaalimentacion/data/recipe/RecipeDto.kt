package ar.com.ingenieriaalimentacion.data.recipe

import ar.com.ingenieriaalimentacion.data.food.dto.QuantitySerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GenerateRecipeDto(
    @SerialName("establishment_id") val establishmentId: String,
    @SerialName("children_count") val childrenCount: Int,
    @SerialName("meal_type") val mealType: String,
)

@Serializable data class PreviewRecipeDto(@SerialName("children_count") val childrenCount: Int)

@Serializable
data class ReuseRecipeDto(val id: String, @SerialName("children_count") val childrenCount: Int)

@Serializable
data class RecipeIngredientDto(
    @SerialName("food_id") val foodId: String,
    val name: String,
    @Serializable(with = QuantitySerializer::class) val quantity: String,
    val unit: String,
)

@Serializable
data class RecipeShortfallDto(
    val kind: String,
    val subject: String,
    val amount: Double,
    val unit: String,
)

@Serializable
data class RecipeProposalDto(
    val id: String,
    val name: String,
    val portions: Int,
    val ingredients: List<RecipeIngredientDto>,
    val steps: List<String>,
    @SerialName("nutrient_adequacy_percent") val nutrientAdequacyPercent: Map<String, Double>,
    @SerialName("macronutrient_percent") val macronutrientPercent: Map<String, Double>,
    @SerialName("gapa_groups") val gapaGroups: List<String>,
    @SerialName("unmet_constraints") val unmetConstraints: List<RecipeShortfallDto>,
    val warnings: List<String>,
    @SerialName("target_energy_kcal") val targetEnergyKcal: Double,
    @SerialName("energy_kcal") val energyKcal: Double,
    @SerialName("cultural_identity") val culturalIdentity: String? = null,
)

@Serializable
data class RecipeSelectionDto(
    @SerialName("proposal_id") val proposalId: String,
    @SerialName("outflow_id") val outflowId: String,
)

@Serializable
data class RecipeRequestDto(
    val id: String,
    @SerialName("establishment_id") val establishmentId: String,
    @SerialName("children_count") val childrenCount: Int,
    @SerialName("meal_type") val mealType: String,
    @SerialName("created_at") val createdAt: String,
    val proposals: List<RecipeProposalDto>,
    val selection: RecipeSelectionDto? = null,
)

@Serializable
data class RecipeHistoryDto(
    val items: List<RecipeRequestDto>,
    val page: Int,
    @SerialName("total_pages") val totalPages: Int,
)
