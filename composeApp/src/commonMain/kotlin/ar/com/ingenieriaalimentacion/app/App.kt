package ar.com.ingenieriaalimentacion.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.rememberNavController
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import com.komoui.components.Spinner
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App(
    appViewModel: AppViewModel = koinViewModel(),
    navController: NavHostController = rememberNavController(),
) {
    val sessionStatus by appViewModel.sessionStatus.collectAsStateWithLifecycle()

    AlTheme {
        PostAuthNotificationPermissionEffect(
            isAuthenticated = sessionStatus is SessionStatus.Authenticated,
        )
        LaunchedEffect(appViewModel, navController) {
            appViewModel.pushDestinations.collectLatest { destination ->
                // Authentication can arrive before login installs the shell; after that, the
                // shell may remain below another outer destination.
                navController.currentBackStackEntryFlow.first { entry ->
                    entry.destination.hasRoute<Route.AuthedShell>() ||
                        runCatching {
                            navController.getBackStackEntry<Route.AuthedShell>()
                        }.isSuccess
                }
                when (destination) {
                    AppPushDestination.FoodStock -> navController.navigate(Route.FoodStock) {
                        launchSingleTop = true
                    }
                }
            }
        }
        when (sessionStatus) {
            // Avoid a flash of the wrong screen before bootstrap resolves.
            SessionStatus.Initializing -> InitializingScreen()
            else -> AppNavHost(
                navController = navController,
                startScreen = startDestinationFor(sessionStatus),
            )
        }
    }
}

private fun startDestinationFor(sessionStatus: SessionStatus): Route = when (sessionStatus) {
    is SessionStatus.Authenticated -> Route.AuthedShell
    // NotAuthenticated / RefreshFailure / Initializing → get_started (Initializing handled separately).
    else -> Route.GetStarted
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
