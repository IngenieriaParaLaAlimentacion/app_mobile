package ar.com.ingenieriaalimentacion.presentation.feature.profile.view

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import ar.com.ingenieriaalimentacion.presentation.feature.profile.edit.navigateToEditProfile
import ar.com.ingenieriaalimentacion.presentation.feature.profile.establishments.navigateToMyEstablishments
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToProfile(navOptions: NavOptions? = null) =
    navigate(Route.Profile, navOptions)

internal fun navigateProfile(
    destination: ProfileDestination,
    outerNav: NavController,
) {
    when (destination) {
        is ProfileDestination.EditProfile ->
            outerNav.navigateToEditProfile(
                name = destination.name,
                lastName = destination.lastName,
                email = destination.email,
                role = destination.role,
            )

        is ProfileDestination.MyEstablishments ->
            outerNav.navigateToMyEstablishments(role = destination.role)
    }
}
