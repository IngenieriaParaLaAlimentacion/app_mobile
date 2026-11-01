package ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.edit_characterization

sealed class EditCharacterizationDestination {
    data object Back : EditCharacterizationDestination()
    data object Saved : EditCharacterizationDestination()
}
