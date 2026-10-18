package ar.com.ingenieriaalimentacion.presentation.feature.food.recipes

import ar.com.ingenieriaalimentacion.core.validation.TextFieldState
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.recipe.*

enum class RecipesTab {
    Generate,
    History,
}

data class RecipeDetail(val requestId: String, val proposal: RecipeProposal)

data class RecipeMutation(
    val establishmentId: String,
    val requestId: String,
    val proposalId: String,
    val reuseId: String? = null,
    val childrenCount: Int? = null,
)

data class RecipeConfirmation(val mutation: RecipeMutation, val name: String, val portions: Int)

data class RecipesState(
    val destination: RecipesDestination? = null,
    val isLoading: Boolean = true,
    val establishment: EstablishmentWithStatus? = null,
    val scopeError: String? = null,
    val isOnline: Boolean = false,
    val children: TextFieldState = TextFieldState(),
    val mealType: MealType = MealType.Lunch,
    val tab: RecipesTab = RecipesTab.Generate,
    val isGenerating: Boolean = false,
    val request: RecipeRequest? = null,
    val screenError: RecipeError? = null,
    val history: List<RecipeRequest> = emptyList(),
    val historyPage: Int = 0,
    val historyHasMore: Boolean = false,
    val isHistoryLoading: Boolean = false,
    val historyError: RecipeError? = null,
    val detail: RecipeDetail? = null,
    val reuseChildren: TextFieldState = TextFieldState(),
    val reusePreview: RecipeProposal? = null,
    val isPreviewing: Boolean = false,
    val confirmation: RecipeConfirmation? = null,
    val pendingMutation: RecipeMutation? = null,
    val isSubmitting: Boolean = false,
    val blockedRequestIds: Set<String> = emptySet(),
    val successMessage: String? = null,
    val mutationVersion: Int = 0,
) {
    val isBusy: Boolean
        get() = isLoading || isGenerating || isPreviewing || isSubmitting

    val canEdit: Boolean
        get() = !isBusy && pendingMutation == null

    val canGenerate: Boolean
        get() =
            canEdit && isOnline && establishment != null && children.value.toIntOrNull() in 1..10000

    val canPreviewReuse: Boolean
        get() =
            canEdit && isOnline && detail != null && reuseChildren.value.toIntOrNull() in 1..10000
}
