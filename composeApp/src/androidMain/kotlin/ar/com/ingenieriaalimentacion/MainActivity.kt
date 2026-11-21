package ar.com.ingenieriaalimentacion

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavHostController
import ar.com.ingenieriaalimentacion.app.App
import ar.com.ingenieriaalimentacion.data.network.ApiConfig
import ar.com.ingenieriaalimentacion.data.notification.PushOpenBridge
import ar.com.ingenieriaalimentacion.presentation.navigation.DeepLinks

class MainActivity : ComponentActivity() {

    private var navController: NavHostController? = null
    private var isCurrentGraph: () -> Boolean = { false }
    private val pendingPublicLinks = ArrayDeque<Intent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handlePushIntent(intent)
        if (savedInstanceState == null && intent.isPublicLink()) {
            pendingPublicLinks.addLast(intent)
        }
        if (intent.isPublicLink()) setIntent(Intent(intent).setData(null))
        savedInstanceState?.getStringArrayList(PENDING_PUBLIC_LINKS)?.forEach { uri ->
            pendingPublicLinks.addLast(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        }
        setContent { App(onNavControllerChanged = ::bindNavController) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePushIntent(intent)
        if (intent.isPublicLink()) {
            setIntent(Intent(intent).setData(null))
            pendingPublicLinks.addLast(intent)
            deliverPublicLinks()
        } else {
            setIntent(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(
            PENDING_PUBLIC_LINKS,
            ArrayList(pendingPublicLinks.mapNotNull { it.dataString }),
        )
        super.onSaveInstanceState(outState)
    }

    private fun bindNavController(controller: NavHostController?, isCurrentGraph: () -> Boolean) {
        navController = controller
        this.isCurrentGraph = isCurrentGraph
        deliverPublicLinks()
    }

    private fun deliverPublicLinks() {
        val controller = navController ?: return
        while (pendingPublicLinks.isNotEmpty() && isCurrentGraph()) {
            controller.handleDeepLink(pendingPublicLinks.removeFirst())
        }
    }

    private fun Intent?.isPublicLink(): Boolean {
        val uri = this?.data ?: return false
        return (uri.scheme == "ingenieriaalimentacion" && uri.host == "reset-password") ||
            (uri.scheme == "https" && uri.host == ApiConfig.WEB_HOST &&
                uri.path == DeepLinks.VERIFY_EMAIL_PATH)
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

    private companion object {
        const val PENDING_PUBLIC_LINKS = "pending-public-links"
    }
}
