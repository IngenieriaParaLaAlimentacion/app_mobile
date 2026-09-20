package ar.com.ingenieriaalimentacion.presentation.feature.food.establishment_food_movement_detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.AlErrorState
import ar.com.ingenieriaalimentacion.core.design_system.components.card.AlCard
import ar.com.ingenieriaalimentacion.core.design_system.components.list.AlListRow
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.top_app_bar.AlBackTopAppBar
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementDetail
import ar.com.ingenieriaalimentacion.domain.model.food.EstablishmentFoodMovementDirection
import ar.com.ingenieriaalimentacion.domain.model.food.FoodMovementSource
import ar.com.ingenieriaalimentacion.presentation.common.formatQuantityWithUnit
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import com.komoui.components.Spinner
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Instant

@Composable
fun EstablishmentFoodMovementDetailScreenRoot(
    route: Route.EstablishmentFoodMovementDetail,
    viewModel: EstablishmentFoodMovementDetailViewModel = koinViewModel { parametersOf(route) },
    navigate: (EstablishmentFoodMovementDetailDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }
    EstablishmentFoodMovementDetailScreen(state, viewModel::onAction)
}

@Composable
fun EstablishmentFoodMovementDetailScreen(
    state: EstablishmentFoodMovementDetailState,
    onAction: (EstablishmentFoodMovementDetailAction) -> Unit,
) {
    AlScaffold {
        Column(Modifier.fillMaxSize()) {
            AlBackTopAppBar(
                title = "Detalle del movimiento",
                onBackClick = { onAction(EstablishmentFoodMovementDetailAction.BackClick) },
            )
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Spinner(size = 28.dp)
                }
                state.error != null -> AlErrorState(
                    title = when (state.error) {
                        EstablishmentFoodMovementDetailError.NotFound -> "Movimiento no encontrado"
                        EstablishmentFoodMovementDetailError.AccessDenied ->
                            "No tenés acceso a este movimiento"
                        else -> "No pudimos cargar el movimiento"
                    },
                    caption = if (
                        !state.isOnline &&
                        state.error == EstablishmentFoodMovementDetailError.Retryable
                    ) {
                        "Estás sin conexión. Este detalle se consulta en línea."
                    } else {
                        "Revisá tu conexión e intentá de nuevo."
                    },
                    onRetry = if (state.error == EstablishmentFoodMovementDetailError.Retryable) {
                        { onAction(EstablishmentFoodMovementDetailAction.RetryLoad) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                state.detail != null -> DetailContent(state.detail)
            }
        }
    }
}

@Composable
private fun DetailContent(detail: EstablishmentFoodMovementDetail) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // No verticalArrangement: the food lines below are AlListRows, which already close
        // themselves with a hairline, so spacing every item would leave a gap above a row and
        // none under its divider. The two items that are not rows carry their own gap instead.
        contentPadding = androidx.compose.foundation.layout.PaddingValues(AlTheme.spacing.lg),
    ) {
        item {
            AlCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm)) {
                    Text(
                        text = if (detail.direction == EstablishmentFoodMovementDirection.In) {
                            "Ingreso"
                        } else {
                            "Egreso"
                        },
                        style = AlTheme.typography.title,
                        color = AlTheme.colors.textBase,
                    )
                    DetailFact("Fecha", formatDetailDateTime(detail.occurredAtMillis), data = true)
                    DetailFact("Responsable", detail.responsible.fullName)
                    DetailFact(
                        "Origen",
                        if (detail.source == FoodMovementSource.Photo) "Foto" else "Manual",
                    )
                    detail.comments?.takeIf { it.isNotBlank() }?.let {
                        DetailFact("Comentarios", it)
                    }
                }
            }
        }
        item {
            Text(
                text = "Alimentos",
                style = AlTheme.typography.bodyStrong,
                color = AlTheme.colors.textBase,
                modifier = Modifier.padding(
                    top = AlTheme.spacing.md,
                    bottom = AlTheme.spacing.sm,
                ),
            )
        }
        itemsIndexed(
            items = detail.items,
            key = { index, line -> "$index-${line.foodId}" },
        ) { index, line ->
            AlListRow(
                title = line.foodName,
                value = formatQuantityWithUnit(line.quantity, line.unit),
                showDivider = index != detail.items.lastIndex,
            )
        }
    }
}

@Composable
private fun DetailFact(label: String, value: String, data: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs)) {
        Text(label, style = AlTheme.typography.caption, color = AlTheme.colors.textMuted)
        Text(
            value,
            style = if (data) AlTheme.typography.data else AlTheme.typography.body,
            color = AlTheme.colors.textBase,
        )
    }
}

private fun formatDetailDateTime(epochMillis: Long): String {
    val value = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "${value.day.toString().padStart(2, '0')}/" +
        "${value.month.number.toString().padStart(2, '0')}/${value.year} " +
        "${value.hour.toString().padStart(2, '0')}:" +
        value.minute.toString().padStart(2, '0')
}
