package ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.edit_characterization

import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationError
import ar.com.ingenieriaalimentacion.presentation.feature.establishment.characterization.common.CharacterizationFormState

data class EditCharacterizationState(
    val establishmentName: String,
    val destination: EditCharacterizationDestination? = null,
    val isLoading: Boolean = true,
    val loadError: CharacterizationError? = null,
    val form: CharacterizationFormState = CharacterizationFormState(),
    val isSaving: Boolean = false,
    val saveError: CharacterizationError? = null,
    /** Set when a save was blocked by client-side errors, which the form already marks inline. */
    val hasInvalidFields: Boolean = false,
) {
    val canEdit: Boolean get() = !isLoading && loadError == null && !isSaving
}
