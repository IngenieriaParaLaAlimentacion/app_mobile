package ar.com.ingenieriaalimentacion

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import ar.com.ingenieriaalimentacion.app.App
import ar.com.ingenieriaalimentacion.data.notification.PushOpenBridge

class MainActivity : ComponentActivity() {

    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val controller = rememberNavController()
            SideEffect { navController = controller }
            App(navController = controller)
        }
        handlePushIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePushIntent(intent)
        navController?.handleDeepLink(intent)
    }

    private fun handlePushIntent(intent: Intent?) {
        val payload = mapOfNotNull(
            "type" to intent?.getStringExtra("type"),
            "establishment_id" to intent?.getStringExtra("establishment_id"),
            "stale_days" to intent?.getStringExtra("stale_days"),
        )
        PushOpenBridge.handle(payload)
        intent?.removeExtra("type")
        intent?.removeExtra("establishment_id")
        intent?.removeExtra("stale_days")
    }

    private fun mapOfNotNull(vararg pairs: Pair<String, String?>): Map<String, String> =
        pairs.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
}
