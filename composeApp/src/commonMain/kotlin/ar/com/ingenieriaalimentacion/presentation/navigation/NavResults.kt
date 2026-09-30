package ar.com.ingenieriaalimentacion.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.SavedStateHandle

/**
 * One-shot result keys passed back through a [SavedStateHandle] on the navigation back
 * stack. A child editor writes the key on success right before `popBackStack()`; the
 * parent observes it with [RefreshOnNavResult] and refreshes once.
 *
 * See CLAUDE.md → "Navigate-back-with-result to refresh (CMP pattern)".
 */
const val PROFILE_UPDATED_RESULT = "PROFILE_UPDATED_RESULT"
const val ESTABLISHMENT_REQUESTED_RESULT = "ESTABLISHMENT_REQUESTED_RESULT"
/** Written to the AuthedShell entry when the user's establishment links changed, so Home
 *  re-reads its selector (and drops a current establishment the user just left). */
const val ESTABLISHMENTS_CHANGED_RESULT = "ESTABLISHMENTS_CHANGED_RESULT"
const val ESTABLISHMENT_ABSENCE_NOTIFIED_RESULT = "ESTABLISHMENT_ABSENCE_NOTIFIED_RESULT"
const val SURVEY_RESPONSE_EDITED_RESULT = "SURVEY_RESPONSE_EDITED_RESULT"
const val FOOD_MOVEMENT_CREATED_RESULT = "FOOD_MOVEMENT_CREATED_RESULT"

/**
 * Observes [key] on [savedStateHandle] and invokes [onResult] each time the child sets
 * it to a non-null value, then clears it so it fires once (consume-once).
 *
 * [savedStateHandle] is nullable because the back stack entry it comes from can be
 * momentarily absent during navigation transitions; a null handle is simply ignored.
 */
@Composable
fun RefreshOnNavResult(
    savedStateHandle: SavedStateHandle?,
    key: String,
    onResult: () -> Unit,
) {
    LaunchedEffect(savedStateHandle, key) {
        val handle = savedStateHandle ?: return@LaunchedEffect
        handle.getStateFlow<Boolean?>(key, null).collect { result ->
            if (result != null) {
                onResult()
                handle.remove<Boolean?>(key)
            }
        }
    }
}
