package ar.com.ingenieriaalimentacion.core.design_system.components.input

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.modifier.modifierLocalProvider

internal val ModifierLocalInputScrollViewport = modifierLocalOf<InputScrollViewport?> { null }

internal class InputScrollViewport(val scrollState: ScrollState) {
    var coordinates by mutableStateOf<LayoutCoordinates?>(null)
}

/**
 * Vertical scrolling for forms using [AlInput]'s keyboard/focus scrolling.
 * Records the viewport outside the scrolling content, so fields scroll relative
 * to its top rather than the app root (which also includes toolbars and insets).
 */
@Composable
fun Modifier.alInputScroll(scrollState: ScrollState): Modifier {
    val viewport = remember(scrollState) { InputScrollViewport(scrollState) }
    return this
        .modifierLocalProvider(ModifierLocalInputScrollViewport) { viewport }
        .onGloballyPositioned { viewport.coordinates = it }
        .verticalScroll(scrollState)
}
