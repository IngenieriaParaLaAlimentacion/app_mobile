package ar.com.ingenieriaalimentacion.presentation.feature.measurement.uploads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MeasurementUploadsContentRoot(
    modifier: Modifier = Modifier,
    viewModel: MeasurementUploadsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MeasurementUploadsContent(state = state, onAction = viewModel::onAction, modifier = modifier)
}
