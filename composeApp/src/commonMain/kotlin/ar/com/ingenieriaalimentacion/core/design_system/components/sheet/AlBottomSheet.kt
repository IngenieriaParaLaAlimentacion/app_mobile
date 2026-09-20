package ar.com.ingenieriaalimentacion.core.design_system.components.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme

/**
 * `surfaceRaised`, not the sunken tone the Home quick-action tiles use: a sheet floats
 * *above* the page, so it must read a half-step lighter than the paper behind it or the
 * overlay looks recessed. Use `surfaceMuted` for selected rows *inside* the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(
            topStart = AlTheme.corners.lg,
            topEnd = AlTheme.corners.lg,
        ),
        containerColor = AlTheme.colors.surfaceRaised,
        contentColor = AlTheme.colors.textBase,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AlTheme.colors.border) },
    ) {
        Column(modifier = Modifier.navigationBarsPadding(), content = content)
    }
}
