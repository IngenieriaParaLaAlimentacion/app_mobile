package ar.com.ingenieriaalimentacion.presentation.feature.auth.reset_password

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import com.komoui.themes.styles

@Composable
fun ResetPasswordSuccessScreen(
    onContinue: () -> Unit,
) {
    val styles = MaterialTheme.styles
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(styles.background)
            .safeDrawingPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "¡Contraseña actualizada!",
            style = MaterialTheme.typography.headlineSmall,
            color = styles.foreground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Tu contraseña fue actualizada. Iniciá sesión con tus nuevas credenciales.",
            style = MaterialTheme.typography.bodyMedium,
            color = styles.mutedForeground,
            textAlign = TextAlign.Center,
        )
        AlButton(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            fullWidth = true,
        ) {
            Text("Volver al inicio")
        }
    }
}
