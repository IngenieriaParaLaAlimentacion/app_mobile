package ar.com.ingenieriaalimentacion.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.data.auth.SessionLease
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import com.komoui.components.Spinner
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App(
    appViewModel: AppViewModel = koinViewModel(),
    onNavControllerChanged: (NavHostController?, isCurrentGraph: () -> Boolean) -> Unit = { _, _ -> },
) {
    val sessionStatus by appViewModel.sessionStatus.collectAsState()

    AlTheme {
        PostAuthNotificationPermissionEffect(
            isAuthenticated = sessionStatus is SessionStatus.Authenticated,
        )
        val startScreen = sessionStatus.startScreenOrNull()
        val authenticated = sessionStatus as? SessionStatus.Authenticated
        if (startScreen == null) {
            InitializingScreen()
        } else {
            key(startScreen, authenticated?.generation) {
                val lease = authenticated?.let { SessionLease(it.generation, requireNotNull(it.session.user).id) }
                SessionGraph(appViewModel, startScreen, onNavControllerChanged, lease)
            }
        }
    }
}

private fun SessionStatus.startScreenOrNull(): Route? = when (this) {
    SessionStatus.Initializing, SessionStatus.Closing -> null
    is SessionStatus.Authenticated -> Route.AuthedShell
    SessionStatus.AccountBlocked -> Route.Login
    is SessionStatus.NotAuthenticated -> Route.GetStarted
    SessionStatus.PasswordResetCompleted -> Route.ResetPasswordSuccess
}

@Composable
private fun SessionGraph(
    appViewModel: AppViewModel,
    startScreen: Route,
    onNavControllerChanged: (NavHostController?, isCurrentGraph: () -> Boolean) -> Unit,
    lease: SessionLease? = null,
) {
    val navController = rememberNavController()
    val onControllerChanged by rememberUpdatedState(onNavControllerChanged)
    val storeOwner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }

    CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
        AppNavHost(navController, startScreen, lease, showBlockNotice = startScreen == Route.Login)
    }
    DisposableEffect(navController) {
        onControllerChanged(navController) {
            val current = appViewModel.sessionStatus.value
            current.startScreenOrNull() == startScreen &&
                (current as? SessionStatus.Authenticated)?.generation == lease?.generation
        }
        onDispose {
            onControllerChanged(null) { false }
            storeOwner.viewModelStore.clear()
        }
    }

    if (lease != null) {
        LaunchedEffect(navController, lease) {
            appViewModel.pushDestinations.collectLatest { destination ->
                if (destination.generation != lease.generation) return@collectLatest
                navController.currentBackStackEntryFlow.first { entry ->
                    entry.destination.hasRoute<Route.AuthedShell>() ||
                        runCatching { navController.getBackStackEntry<Route.AuthedShell>() }.isSuccess
                }
                val current = appViewModel.sessionStatus.value as? SessionStatus.Authenticated
                if (current?.generation != lease.generation) return@collectLatest
                when (destination) {
                    is AppPushDestination.FoodStock -> navController.navigate(Route.FoodStock) {
                        launchSingleTop = true
                    }
                }
            }
        }
    }
}

@Composable
private fun InitializingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AlTheme.colors.surfaceBase),
        contentAlignment = Alignment.Center,
    ) {
        Spinner()
    }
}
