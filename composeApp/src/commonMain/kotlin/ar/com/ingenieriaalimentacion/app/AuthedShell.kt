package ar.com.ingenieriaalimentacion.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import ar.com.ingenieriaalimentacion.data.network.connectivity.ConnectivityObserver
import ar.com.ingenieriaalimentacion.data.subject.sync.SyncCoordinator
import ar.com.ingenieriaalimentacion.domain.usecase.activity.WarmActivityCacheUseCase
import ar.com.ingenieriaalimentacion.domain.usecase.profile.RefreshMyEstablishmentsUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.minutes
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ar.com.ingenieriaalimentacion.core.design_system.components.navbar.AlBottomNavBar
import ar.com.ingenieriaalimentacion.core.design_system.components.navbar.AlBottomNavTab
import ar.com.ingenieriaalimentacion.presentation.feature.home.HomeDestination
import ar.com.ingenieriaalimentacion.presentation.feature.history.HistoryHubScreenRoot
import ar.com.ingenieriaalimentacion.presentation.feature.home.HomeScreenRoot
import ar.com.ingenieriaalimentacion.presentation.feature.measurement.create_measurement.navigateToCreateMeasurement
import ar.com.ingenieriaalimentacion.presentation.feature.profile.view.ProfileScreenRoot
import ar.com.ingenieriaalimentacion.presentation.feature.profile.view.navigateProfile
import ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject.navigateToCreateSubject
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

@Composable
fun AuthedShell(onSignOut: () -> Unit) {
    SyncTriggers()

    val outerNav = LocalOuterNavController.current
    val innerNav = rememberNavController()
    val entry by innerNav.currentBackStackEntryAsState()
    val selectedTab = when {
        entry?.destination?.hasRoute<Route.Profile>() == true -> AlBottomNavTab.Profile
        entry?.destination?.hasRoute<Route.History>() == true -> AlBottomNavTab.History
        else -> AlBottomNavTab.Home
    }

    Scaffold(
        bottomBar = {
            AlBottomNavBar(
                selected = selectedTab,
                onSelected = { tab ->
                    val target: Route = when (tab) {
                        AlBottomNavTab.Home -> Route.Home
                        AlBottomNavTab.History -> Route.History
                        AlBottomNavTab.Profile -> Route.Profile
                    }
                    innerNav.navigate(target) {
                        // Save/restore each tab's back stack entry so its ViewModel
                        // survives tab switches instead of being recreated (which would
                        // re-fire ProfileViewModel.init -> /me on every Profile re-entry).
                        popUpTo(innerNav.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { padding: PaddingValues ->
        NavHost(
            navController = innerNav,
            startDestination = Route.Home,
            modifier = Modifier.padding(padding),
        ) {
            composable<Route.Home> {
                HomeScreenRoot(
                    navigate = { destination ->
                        when (destination) {
                            HomeDestination.Back -> outerNav.popBackStack()
                            HomeDestination.NavigateToCreateSubject ->
                                outerNav.navigateToCreateSubject()
                            HomeDestination.NavigateToCreateMeasurement ->
                                outerNav.navigateToCreateMeasurement()
                            HomeDestination.NavigateToHistory ->
                                innerNav.navigate(Route.History) {
                                    // Mirror the bottom bar's save/restore so the History tab's
                                    // back stack + ViewModel are preserved across the switch.
                                    popUpTo(innerNav.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                        }
                    },
                )
            }
            composable<Route.History> {
                HistoryHubScreenRoot()
            }
            composable<Route.Profile> {
                ProfileScreenRoot(
                    navigate = { dest -> navigateProfile(dest, outerNav, onSignOut) },
                )
            }
        }
    }
}

/**
 * Drives foreground + periodic sync while the authed shell is shown. Connectivity-driven sync and
 * crash recovery live in [SyncCoordinator.start]; this adds the lifecycle triggers.
 */
@Composable
private fun SyncTriggers(
    coordinator: SyncCoordinator = koinInject(),
    connectivity: ConnectivityObserver = koinInject(),
    refreshMyEstablishments: RefreshMyEstablishmentsUseCase = koinInject(),
    warmActivityCache: WarmActivityCacheUseCase = koinInject(),
) {
    LifecycleStartEffect(Unit) {
        coordinator.requestSync()                    // sync each time the app returns to foreground
        onStopOrDispose { }
    }
    LaunchedEffect(Unit) {
        // One-shot: warm the establishment cache so the measurement picker works offline later.
        if (connectivity.isOnline()) refreshMyEstablishments()
    }
    LaunchedEffect(Unit) {
        // One-shot: warm the activity cache so Home/History have fresh data before first open.
        if (connectivity.isOnline()) warmActivityCache()
    }
    LaunchedEffect(Unit) {
        while (isActive) {                           // periodic backstop while foregrounded
            delay(15.minutes)
            coordinator.requestSync()
        }
    }
}
