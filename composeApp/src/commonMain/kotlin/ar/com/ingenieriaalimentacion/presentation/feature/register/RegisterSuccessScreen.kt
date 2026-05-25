package ar.com.ingenieriaalimentacion.presentation.feature.register

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.components.success.AlSuccessTick
import com.komoui.components.Button
import com.komoui.themes.styles
import kotlinx.coroutines.delay

@Composable
fun RegisterSuccessScreen(
    onLoginClick: () -> Unit,
) {
    val styles = MaterialTheme.styles
    var textVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(380)
        textVisible = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(styles.background)
            .safeDrawingPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AlSuccessTick()

            AnimatedVisibility(
                visible = textVisible,
                enter = fadeIn(animationSpec = tween(durationMillis = 320)) +
                    slideInVertically(
                        animationSpec = tween(durationMillis = 320),
                        initialOffsetY = { it / 4 },
                    ),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "¡Registro enviado!",
                        style = MaterialTheme.typography.headlineSmall,
                        color = styles.foreground,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Tu registro fue enviado y está pendiente de aprobación. " +
                            "Te avisaremos cuando tu cuenta esté habilitada para ingresar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = styles.mutedForeground,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Button(
            onClick = onLoginClick,
            modifier = Modifier.fillMaxWidth(),
            fullWidth = true,
        ) {
            Text("Iniciar sesión")
        }
    }
}
