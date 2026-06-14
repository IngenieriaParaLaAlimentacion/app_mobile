package ar.com.ingenieriaalimentacion.core.design_system.components.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButtonVariant
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import com.komoui.components.AlertDialog

/** Confirm-button emphasis: [Destructive] (red) for risky actions, [Neutral] otherwise. */
enum class AlConfirmTone { Destructive, Neutral }

/**
 * Design-system confirm/cancel modal, ported from the web `ExConfirmDialog`: a
 * title over a muted description, with a right-aligned outline [cancelText]
 * button next to a filled [confirmText] button. Built on the KomoUI
 * [AlertDialog] primitive so it inherits the app's modal scaffold, scrim, and
 * dismiss-on-outside-tap.
 *
 * Use for ANY two-action confirm/cancel prompt (sign out, delete, cancel…).
 * [confirmTone] switches the confirm button between the destructive red default
 * and a neutral tone; [loading] spins the confirm button and blocks dismissal.
 */
@Composable
fun AlConfirmDialog(
    open: Boolean,
    title: String,
    description: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    cancelText: String = "Cancelar",
    confirmTone: AlConfirmTone = AlConfirmTone.Destructive,
    loading: Boolean = false,
) {
    AlertDialog(
        open = open,
        onDismissRequest = { if (!loading) onDismiss() },
        modifier = modifier,
        title = { Text(title) },
        description = { Text(description) },
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs)) {
                AlButton(
                    onClick = onDismiss,
                    enabled = !loading,
                    variant = AlButtonVariant.Outline,
                ) {
                    Text(cancelText)
                }
                AlButton(
                    onClick = onConfirm,
                    loading = loading,
                    variant = when (confirmTone) {
                        AlConfirmTone.Destructive -> AlButtonVariant.Destructive
                        AlConfirmTone.Neutral -> AlButtonVariant.Default
                    },
                ) {
                    Text(confirmText)
                }
            }
        },
    )
}
