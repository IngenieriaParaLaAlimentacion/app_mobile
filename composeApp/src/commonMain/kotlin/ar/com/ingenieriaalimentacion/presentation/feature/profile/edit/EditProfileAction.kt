package ar.com.ingenieriaalimentacion.presentation.feature.profile.edit

sealed interface EditProfileAction {
    data class NameChanged(val value: String) : EditProfileAction
    data class LastNameChanged(val value: String) : EditProfileAction
    data object SaveClick : EditProfileAction
    data object BackClick : EditProfileAction
}
