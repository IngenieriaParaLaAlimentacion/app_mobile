package ar.com.ingenieriaalimentacion.presentation.feature.subject.uploads

import androidx.navigation.NavController

/** Resolves [SubjectUploadsDestination]. As a tab root the only destination is [Back]. */
internal fun navigateSubjectUploads(
    destination: SubjectUploadsDestination,
    navController: NavController,
) {
    when (destination) {
        SubjectUploadsDestination.Back -> navController.popBackStack()
    }
}
