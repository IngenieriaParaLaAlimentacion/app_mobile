package ar.com.ingenieriaalimentacion.presentation.feature.food.food_movement_history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * Hub-section root. No `LocalOuterNavController` wiring and no `clearNavigation()`: food-movement
 * rows are not navigable — there is no `GET /api/v1/food-intakes/{id}`, so the feed payload is the
 * whole story and the row renders it inline.
 */
@Composable
fun FoodMovementHistoryContentRoot(
    modifier: Modifier = Modifier,
    viewModel: FoodMovementHistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FoodMovementHistoryContent(state = state, onAction = viewModel::onAction, modifier = modifier)
}
