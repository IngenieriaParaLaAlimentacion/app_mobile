package ar.com.ingenieriaalimentacion.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
actual fun PostAuthNotificationPermissionEffect(isAuthenticated: Boolean) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("push_permission", Context.MODE_PRIVATE)
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

    LaunchedEffect(isAuthenticated) {
        if (
            isAuthenticated &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            !prefs.getBoolean("requested", false)
        ) {
            prefs.edit().putBoolean("requested", true).apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
