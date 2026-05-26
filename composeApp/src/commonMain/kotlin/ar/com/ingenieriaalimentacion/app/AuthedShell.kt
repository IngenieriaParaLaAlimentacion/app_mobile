package ar.com.ingenieriaalimentacion.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ar.com.ingenieriaalimentacion.core.design_system.components.navbar.AlBottomNavBar
import ar.com.ingenieriaalimentacion.core.design_system.components.navbar.AlBottomNavTab
import ar.com.ingenieriaalimentacion.presentation.feature.home.HomeDestination
import ar.com.ingenieriaalimentacion.presentation.feature.home.HomeScreenRoot
import ar.com.ingenieriaalimentacion.presentation.feature.settings.SettingsScreenRoot
import ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject.navigateToCreateSubject
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

@Composable
fun AuthedShell(onSignOut: () -> Unit) {
    val outerNav = LocalOuterNavController.current
    val innerNav = rememberNavController()
    val entry by innerNav.currentBackStackEntryAsState()
    val selectedTab = when {
        entry?.destination?.hasRoute<Route.Settings>() == true -> AlBottomNavTab.Settings
        else -> AlBottomNavTab.Home
    }

    Scaffold(
        bottomBar = {
            AlBottomNavBar(
                selected = selectedTab,
                onSelected = { tab ->
                    val target: Route = when (tab) {
                        AlBottomNavTab.Home -> Route.Home
                        AlBottomNavTab.Settings -> Route.Settings
                    }
                    innerNav.navigate(target) {
                        popUpTo(innerNav.graph.findStartDestination().id) {
                            saveState = false
                        }
                        launchSingleTop = true
                        restoreState = false
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
                        }
                    },
                )
            }
            composable<Route.Settings> {
                SettingsScreenRoot(onLoggedOut = onSignOut)
            }
        }
    }
}
