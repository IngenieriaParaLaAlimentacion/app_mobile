package ar.com.ingenieriaalimentacion.presentation.feature.register

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.komoui.themes.styles

@Composable
fun RegisterSuccessScreen() {
    val styles = MaterialTheme.styles
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(styles.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "¡Registro enviado!",
            style = MaterialTheme.typography.headlineSmall,
            color = styles.foreground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Tu registro fue enviado y está pendiente de aprobación. Te avisaremos cuando tu cuenta esté habilitada para ingresar.",
            style = MaterialTheme.typography.bodyMedium,
            color = styles.mutedForeground,
            textAlign = TextAlign.Center,
        )
    }
}
