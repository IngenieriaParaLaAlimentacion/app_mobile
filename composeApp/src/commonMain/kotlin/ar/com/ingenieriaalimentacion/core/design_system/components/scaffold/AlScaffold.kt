package ar.com.ingenieriaalimentacion.core.design_system.components.scaffold

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme

@Composable
fun AlScaffold(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AlTheme.colors.surfaceBase),
    ) {
        content()
    }
}
