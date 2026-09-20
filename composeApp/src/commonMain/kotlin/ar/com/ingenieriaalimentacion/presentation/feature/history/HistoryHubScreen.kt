package ar.com.ingenieriaalimentacion.presentation.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.components.segmented.AlSegmentedControl
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.presentation.feature.food.food_movement_history.FoodMovementHistoryContentRoot
import ar.com.ingenieriaalimentacion.presentation.feature.measurement.uploads.MeasurementUploadsContentRoot
import ar.com.ingenieriaalimentacion.presentation.feature.subject.uploads.SubjectUploadsContentRoot
import ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_history.SurveyResponseHistoryContentRoot

/**
 * Combined "Historial" hub: a Sujetos | Mediciones | Encuestas | Alimentos segmented control
 * hosting each segment's content body. Sync runs automatically via
 * [ar.com.ingenieriaalimentacion.app.AuthedShell]'s SyncTriggers (foreground, periodic
 * and connectivity-driven), so the hub no longer needs a manual "Sincronizar" action.
 */
@Composable
fun HistoryHubScreenRoot() {
    var segment by rememberSaveable { mutableStateOf(HistorySegment.Subjects) }
    HistoryHubScreen(
        segment = segment,
        onSegmentChange = { segment = it },
    )
}

@Composable
private fun HistoryHubScreen(
    segment: HistorySegment,
    onSegmentChange: (HistorySegment) -> Unit,
) {
    AlScaffold {
        Column(Modifier.fillMaxSize()) {
            HubHeader(segment, onSegmentChange)
            when (segment) {
                HistorySegment.Subjects -> SubjectUploadsContentRoot(Modifier.fillMaxSize())
                HistorySegment.Measurements -> MeasurementUploadsContentRoot(Modifier.fillMaxSize())
                HistorySegment.SurveyResponses -> SurveyResponseHistoryContentRoot(Modifier.fillMaxSize())
                HistorySegment.FoodMovements -> FoodMovementHistoryContentRoot(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun HubHeader(
    segment: HistorySegment,
    onSegmentChange: (HistorySegment) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Roomier above the title than around it: this is the first thing under the status
            // bar, and at a flat `md` the display face sat too close to the top edge.
            .padding(
                start = AlTheme.spacing.md,
                end = AlTheme.spacing.md,
                top = AlTheme.spacing.lg,
                bottom = AlTheme.spacing.md,
            ),
        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
    ) {
        Text(
            text = "Historial",
            style = AlTheme.typography.title,
            color = AlTheme.colors.textBase,
        )
        AlSegmentedControl(
            segments = HistorySegment.entries.map { it.label },
            selectedIndex = segment.ordinal,
            onSelect = { onSegmentChange(HistorySegment.entries[it]) },
        )
    }
}
