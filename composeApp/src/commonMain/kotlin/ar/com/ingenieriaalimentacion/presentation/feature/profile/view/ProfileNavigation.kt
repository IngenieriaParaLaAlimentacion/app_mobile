package ar.com.ingenieriaalimentacion.presentation.feature.profile.view

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import ar.com.ingenieriaalimentacion.presentation.feature.profile.edit.navigateToEditProfile
import ar.com.ingenieriaalimentacion.presentation.feature.profile.establishments.navigateToMyEstablishments
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToProfile(navOptions: NavOptions? = null) =
    navigate(Route.Profile, navOptions)

/**
 * Dispatches the Profile tab's destinations. Edit/Request push on the [outerNav]
 * (full-screen), while [ProfileDestination.LoggedOut] delegates to [onSignOut].
 */
internal fun navigateProfile(
    destination: ProfileDestination,
    outerNav: NavController,
    onSignOut: () -> Unit,
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

        ProfileDestination.LoggedOut -> onSignOut()
    }
}
