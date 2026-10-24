package ar.com.ingenieriaalimentacion.presentation.feature.food.recipes

import ar.com.ingenieriaalimentacion.domain.model.recipe.MealType

sealed interface RecipesAction {
    data object BackClick : RecipesAction

    data object RetryEstablishment : RecipesAction

    data class ChildrenChanged(val value: String) : RecipesAction

    data class ChildrenFocusChanged(val focused: Boolean) : RecipesAction

    data class MealSelected(val meal: MealType) : RecipesAction

    data object Generate : RecipesAction

    data class TabSelected(val tab: RecipesTab) : RecipesAction

    data object ReloadHistory : RecipesAction

    data object LoadMoreHistory : RecipesAction

    data class ShowRecipe(val requestId: String, val proposalId: String) : RecipesAction

    data class Choose(val proposalId: String) : RecipesAction

    data object Confirm : RecipesAction

    data object DismissConfirmation : RecipesAction

    data object RetryConfirmation : RecipesAction

    data class ReuseChildrenChanged(val value: String) : RecipesAction

    data class ReuseChildrenFocusChanged(val focused: Boolean) : RecipesAction

    data object PreviewReuse : RecipesAction

    data object ConfirmReuse : RecipesAction
}
