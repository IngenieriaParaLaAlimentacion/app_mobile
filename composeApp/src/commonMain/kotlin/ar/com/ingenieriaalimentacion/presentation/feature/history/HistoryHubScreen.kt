package ar.com.ingenieriaalimentacion.presentation.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButtonVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.segmented.AlSegmentedControl
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.presentation.feature.measurement.uploads.MeasurementUploadsContentRoot
import ar.com.ingenieriaalimentacion.presentation.feature.subject.uploads.SubjectUploadsContentRoot
import org.koin.compose.koinInject

/**
 * Combined "Historial" hub: a Sujetos | Mediciones segmented control over a single
 * "Sincronizar" action, hosting each segment's content body. The injected
 * [SyncCoordinator] mirrors [ar.com.ingenieriaalimentacion.app.AuthedShell]'s SyncTriggers;
 * [SyncCoordinator.requestSync] drives every engine (subjects + measurements) in one pass,
 * so one button serves both segments.
 */
@Composable
fun HistoryHubScreenRoot(coordinator: SyncCoordinator = koinInject()) {
    var segment by rememberSaveable { mutableStateOf(HistorySegment.Subjects) }
    HistoryHubScreen(
        segment = segment,
        onSegmentChange = { segment = it },
        onSyncNow = { coordinator.requestSync() },
    )
}

@Composable
private fun HistoryHubScreen(
    segment: HistorySegment,
    onSegmentChange: (HistorySegment) -> Unit,
    onSyncNow: () -> Unit,
) {
    AlScaffold {
        Column(Modifier.fillMaxSize()) {
            HubHeader(segment, onSegmentChange, onSyncNow)
            when (segment) {
                HistorySegment.Subjects -> SubjectUploadsContentRoot(Modifier.fillMaxSize())
                HistorySegment.Measurements -> MeasurementUploadsContentRoot(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun HubHeader(
    segment: HistorySegment,
    onSegmentChange: (HistorySegment) -> Unit,
    onSyncNow: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AlTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Historial",
                style = AlTheme.typography.title,
                color = AlTheme.colors.textBase,
            )
            AlButton(onClick = onSyncNow, variant = AlButtonVariant.Outline) {
                Text("Sincronizar")
            }
        }
        AlSegmentedControl(
            segments = HistorySegment.entries.map { it.label },
            selectedIndex = segment.ordinal,
            onSelect = { onSegmentChange(HistorySegment.entries[it]) },
        )
    }
}
