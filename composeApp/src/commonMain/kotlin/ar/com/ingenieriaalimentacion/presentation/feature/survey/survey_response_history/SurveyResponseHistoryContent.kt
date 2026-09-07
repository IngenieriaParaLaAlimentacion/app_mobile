package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import ar.com.ingenieriaalimentacion.core.design_system.components.EmptyState
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButtonVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.dialog.AlConfirmDialog
import ar.com.ingenieriaalimentacion.core.design_system.components.dialog.AlConfirmTone
import ar.com.ingenieriaalimentacion.core.design_system.components.list.AlDateSeparator
import ar.com.ingenieriaalimentacion.core.design_system.components.list.AlListRow
import ar.com.ingenieriaalimentacion.core.design_system.components.pill.AlPill
import ar.com.ingenieriaalimentacion.core.design_system.components.pill.AlPillTone
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import ar.com.ingenieriaalimentacion.domain.model.survey.ResponseConflict
import ar.com.ingenieriaalimentacion.domain.model.survey.needsRequiredResponseNumber
import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary
import ar.com.ingenieriaalimentacion.presentation.common.today
import com.komoui.components.Spinner
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Composable
fun SurveyResponseHistoryContent(
    state: SurveyResponseHistoryState,
    onAction: (SurveyResponseHistoryAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentState by rememberUpdatedState(state)
    // Discard destroys answers that were never sent, so it is always confirmed first.
    var pendingDiscard by remember { mutableStateOf<SurveyResponseSummary?>(null) }
    val zone = TimeZone.currentSystemDefault()
    val days = remember(state.items) {
        // "Hoy"/"Ayer" resolve against the day this list was last built; every new or synced
        // response rebuilds it, so the labels cannot drift far past midnight.
        state.items.toDayGroups(today = today(zone), zone = zone)
    }
    // Headers occupy list slots too, so the near-end check counts lines, not responses.
    val lineCount by rememberUpdatedState(days.size + state.items.size)

    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= lineCount - 1 - LOAD_MORE_THRESHOLD
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && currentState.canLoadMore) onAction(SurveyResponseHistoryAction.LoadMore)
        }
    }

    when {
        state.isLoading -> Box(modifier.fillMaxSize(), Alignment.Center) {
            Spinner(size = 28.dp)
        }

        state.isEmpty -> EmptyState(
            title = "Sin respuestas todavía",
            caption = "Las encuestas que respondas aparecerán acá con su estado de sincronización.",
            modifier = modifier,
        )

        else -> LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
        ) {
            days.forEachIndexed { dayIndex, day ->
                stickyHeader(key = day.key, contentType = "date-separator") {
                    AlDateSeparator(
                        label = day.label,
                        // The first header sits right under the segmented control, which already
                        // carries its own bottom padding.
                        topPadding = if (dayIndex == 0) AlTheme.spacing.xs else AlTheme.spacing.lg,
                    )
                }
                itemsIndexed(
                    items = day.items,
                    key = { _, item -> item.id },
                    contentType = { _, _ -> "response" },
                ) { index, item ->
                    SurveyResponseHistoryRow(
                        item = item,
                        // The last row of a day closes its block: the next header is the divider.
                        showDivider = index != day.items.lastIndex,
                        onAction = onAction,
                        onRequestDiscard = { pendingDiscard = it },
                    )
                }
            }
            if (state.isLoadingMore) {
                item {
                    Spinner(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        size = 24.dp,
                    )
                }
            }
        }
    }

    AlConfirmDialog(
        open = pendingDiscard != null,
        title = "Descartar respuesta",
        description = pendingDiscard
            ?.let { "Se perderán las respuestas de \"${it.surveyName}\" que no llegaron al servidor." }
            .orEmpty(),
        confirmText = "Descartar",
        confirmTone = AlConfirmTone.Destructive,
        onConfirm = {
            pendingDiscard?.let { onAction(SurveyResponseHistoryAction.DiscardClick(it.id)) }
            pendingDiscard = null
        },
        onDismiss = { pendingDiscard = null },
    )
}

@Composable
private fun SurveyResponseHistoryRow(
    item: SurveyResponseSummary,
    showDivider: Boolean,
    onAction: (SurveyResponseHistoryAction) -> Unit,
    onRequestDiscard: (SurveyResponseSummary) -> Unit,
) {
    // A conflict outranks the raw sync state: the row is FAILED underneath, but "Error" would
    // read as something a retry could fix, and nothing here can be retried.
    val (label, tone) = when {
        item.conflict != null && item.syncState == SyncState.Failed -> "Conflicto" to AlPillTone.Danger
        else -> when (item.syncState) {
            SyncState.Pending -> "Pendiente" to AlPillTone.Warning
            SyncState.Syncing -> "Sincronizando" to AlPillTone.Neutral
            SyncState.Synced -> "Sincronizado" to AlPillTone.Success
            SyncState.Failed -> "Error" to AlPillTone.Danger
            SyncState.Merged -> "Sincronizado" to AlPillTone.Success // unreachable for responses
        }
    }
    Column {
        AlListRow(
            horizontalPadding = AlTheme.spacing.md,
            title = item.surveyName,
            subtitle = item.targetLabel ?: "Anónima",
            onClick = { onAction(SurveyResponseHistoryAction.ResponseClick(item.surveyId, item.id)) },
            trailing = {
                item.responseNumber?.let { Text("#$it", style = AlTheme.typography.dataSmall, color = AlTheme.colors.textMuted) }
                AlPill(text = label, tone = tone)
            },
            showDivider = showDivider,
        )
        when {
            item.conflict != null && item.syncState == SyncState.Failed -> Column(
                modifier = Modifier.padding(horizontal = AlTheme.spacing.md, vertical = AlTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs),
            ) {
                Text(
                    text = conflictMessage(item.conflict),
                    style = AlTheme.typography.caption,
                    color = AlTheme.colors.textMuted,
                )
                AlButton(onClick = { onRequestDiscard(item) }) { Text("Descartar") }
            }

            item.needsRequiredResponseNumber -> Column(
                modifier = Modifier.padding(horizontal = AlTheme.spacing.md, vertical = AlTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs),
            ) {
                item.errorMessage?.let { Text(it, style = AlTheme.typography.caption, color = AlTheme.colors.textMuted) }
                Row(horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs)) {
                    AlButton(onClick = { onAction(SurveyResponseHistoryAction.FixResponseNumberClick(item.id, item.surveyName)) }) { Text("Agregar número") }
                    AlButton(onClick = { onRequestDiscard(item) }, variant = AlButtonVariant.Outline) { Text("Descartar") }
                }
            }

            item.syncState == SyncState.Failed -> Column(
                modifier = Modifier.padding(horizontal = AlTheme.spacing.md, vertical = AlTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs),
            ) {
                // CA9: the engine's reason is rendered, never silently dropped.
                item.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = AlTheme.typography.caption,
                        color = AlTheme.colors.textMuted,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs)) {
                    AlButton(onClick = { onAction(SurveyResponseHistoryAction.RetryUploadClick(item.id)) }) {
                        Text("Reintentar")
                    }
                    AlButton(
                        onClick = { onRequestDiscard(item) },
                        variant = AlButtonVariant.Outline,
                    ) {
                        Text("Descartar")
                    }
                }
            }
        }
    }
}

/** Falls back to a bare "ya fue respondida" when the server omitted `event_at` — a half-formed
 *  date reads worse than none. */
private fun conflictMessage(conflict: ResponseConflict): String {
    val who = conflict.submittedByName
    val on = formatConflictDate(conflict.eventAt)
    return if (on == null) "Ya la respondió $who." else "Ya la respondió $who el $on."
}

private fun formatConflictDate(isoInstant: String): String? = runCatching {
    val ldt = Instant.parse(isoInstant).toLocalDateTime(TimeZone.currentSystemDefault())
    val dd = ldt.dayOfMonth.toString().padStart(2, '0')
    val mm = ldt.monthNumber.toString().padStart(2, '0')
    "$dd/$mm/${ldt.year}"
}.getOrNull()

private const val LOAD_MORE_THRESHOLD = 3
