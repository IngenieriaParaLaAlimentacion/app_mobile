package ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.edit_characterization

import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationFormAction

sealed interface EditCharacterizationAction {
    data class Form(val action: CharacterizationFormAction) : EditCharacterizationAction
    data object SaveClick : EditCharacterizationAction
    data object RetryClick : EditCharacterizationAction
    data object BackClick : EditCharacterizationAction
}
