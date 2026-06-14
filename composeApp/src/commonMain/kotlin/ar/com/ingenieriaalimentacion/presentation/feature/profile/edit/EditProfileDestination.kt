package ar.com.ingenieriaalimentacion.presentation.feature.profile.edit

sealed class EditProfileDestination {
    data object Back : EditProfileDestination()
    data object Saved : EditProfileDestination()
}
