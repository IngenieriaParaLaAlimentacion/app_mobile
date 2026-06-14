package ar.com.ingenieriaalimentacion.presentation.feature.profile.edit

import ar.com.ingenieriaalimentacion.domain.model.profile.ProfileError

data class EditProfileState(
    val destination: EditProfileDestination? = null,
    val name: String = "",
    val lastName: String = "",
    val email: String = "",
    val role: String = "",
    val originalName: String = "",
    val originalLastName: String = "",
    val isSaving: Boolean = false,
    val saveError: ProfileError? = null,
) {
    val hasChanges: Boolean get() = name != originalName || lastName != originalLastName
    val canSave: Boolean
        get() = name.isNotBlank() && lastName.isNotBlank() && hasChanges && !isSaving
}
