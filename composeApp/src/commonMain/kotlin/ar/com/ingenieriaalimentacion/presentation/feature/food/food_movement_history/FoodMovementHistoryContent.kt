package ar.com.ingenieriaalimentacion.presentation.feature.food.food_movement_history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.components.AlErrorState
import ar.com.ingenieriaalimentacion.core.design_system.components.EmptyState
import ar.com.ingenieriaalimentacion.core.design_system.components.list.AlDateSeparator
import ar.com.ingenieriaalimentacion.core.design_system.components.list.AlListRow
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovement
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovementDirection
import ar.com.ingenieriaalimentacion.domain.model.food.lineCountLabel
import ar.com.ingenieriaalimentacion.domain.model.food.movementTitle
import ar.com.ingenieriaalimentacion.presentation.common.formatQuantityWithUnit
import ar.com.ingenieriaalimentacion.presentation.common.today
import com.komoui.components.Spinner
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_package_2_line
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_package_line
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.DrawableResource

@Composable
fun FoodMovementHistoryContent(
    state: FoodMovementHistoryState,
    onAction: (FoodMovementHistoryAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentState by rememberUpdatedState(state)
    val zone = TimeZone.currentSystemDefault()
    val items = state.items
    val days = remember(items) { items.toDayGroups(today = today(zone), zone = zone) }
    // Headers occupy list slots too, so the near-end check counts lines, not movements.
    val lineCount by rememberUpdatedState(days.size + items.size)

    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= lineCount - 1 - LOAD_MORE_THRESHOLD
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && currentState.canLoadMore) onAction(FoodMovementHistoryAction.LoadMore)
        }
    }

    when {
        state.isLoading -> Box(modifier.fillMaxSize(), Alignment.Center) { Spinner(size = 28.dp) }

        // A recoverable failure with nothing to fall back on. Full-surface, not a toast, so it
        // cannot be missed, and the retry sits next to the explanation.
        state.showsBlockingError -> AlErrorState(
            title = "No pudimos cargar los movimientos",
            caption = "Revisá tu conexión e intentá de nuevo. No se perdió ningún registro.",
            onRetry = { onAction(FoodMovementHistoryAction.RetryLoad) },
            modifier = modifier,
        )

        state.isEmpty -> EmptyState(
            title = "Sin movimientos de alimentos",
            caption = "Los ingresos y salidas de alimentos que registres aparecerán acá.",
            modifier = modifier,
        )

        else -> LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
            days.forEachIndexed { dayIndex, day ->
                stickyHeader(key = day.key, contentType = "date-separator") {
                    AlDateSeparator(
                        label = day.label,
                        topPadding = if (dayIndex == 0) AlTheme.spacing.xs else AlTheme.spacing.lg,
                    )
                }
                itemsIndexed(
                    items = day.items,
                    // Movement ids are unique per direction but the two directions share this
                    // LazyColumn, so the key is namespaced — the same reasoning as the cache row id.
                    key = { _, item -> "${item.direction.name}:${item.id}" },
                    contentType = { _, _ -> "movement" },
                ) { index, item ->
                    // The last row of a day closes its block: the next header is the divider.
                    FoodMovementRow(item = item, showDivider = index != day.items.lastIndex)
                }
            }
            if (state.isLoadingMore) {
                item {
                    Spinner(modifier = Modifier.fillMaxWidth().padding(16.dp), size = 24.dp)
                }
            }
        }
    }
}

@Composable
private fun FoodMovementRow(item: FoodMovement, showDivider: Boolean) {
    Column {
        AlListRow(
            horizontalPadding = AlTheme.spacing.md,
            title = item.direction.movementTitle,
            subtitle = item.establishmentName,
            leadingIcon = item.direction.icon(),
            // No `onClick`: there is no detail endpoint. That also suppresses the chevron, so the
            // row never promises a screen that doesn't exist.
            showDivider = showDivider,
            trailing = {
                Text(
                    text = item.lineCountLabel,
                    style = AlTheme.typography.caption,
                    color = AlTheme.colors.textMuted,
                )
            },
        )
        Column(
            modifier = Modifier.padding(
                start = AlTheme.spacing.md,
                end = AlTheme.spacing.md,
                bottom = AlTheme.spacing.xs,
            ),
            verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs),
        ) {
            // Rendered AS STORED: no distinctBy, no grouping by food. The same food may legally
            // appear twice in one movement (there is no unique (movement_id, food_id) constraint)
            // and collapsing the lines would misreport what was registered. These are plain
            // children of a Column, not lazy items, so duplicates need no keys at all.
            item.lines.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
                ) {
                    Text(
                        text = line.foodName,
                        style = AlTheme.typography.body,
                        color = AlTheme.colors.textBase,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatQuantityWithUnit(line.quantity, line.unit),
                        style = AlTheme.typography.data,
                        color = AlTheme.colors.textMuted,
                    )
                }
            }
            item.comments?.let {
                Text(text = it, style = AlTheme.typography.caption, color = AlTheme.colors.textMuted)
            }
        }
    }
}

// ic_mingcute_bowl_line is already Home's "Alimentos" quick action — a history row must not look
// like the button that creates one. Direction is always carried by the title text too, never by
// the icon alone.
private fun FoodMovementDirection.icon(): DrawableResource = when (this) {
    FoodMovementDirection.Intake -> Res.drawable.ic_mingcute_package_2_line
    FoodMovementDirection.Outflow -> Res.drawable.ic_mingcute_package_line
}

private const val LOAD_MORE_THRESHOLD = 3
