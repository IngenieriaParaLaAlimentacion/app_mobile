package ar.com.ingenieriaalimentacion.core.design_system.components.input

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButtonVariant
import ar.com.ingenieriaalimentacion.core.design_system.components.list.AlListRow
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import com.komoui.components.Spinner
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_search_line
import org.jetbrains.compose.resources.vectorResource

/** One row of the suggestion dropdown. [trailing] is a short right-aligned tag (e.g. a unit). */
data class AlSuggestion(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val trailing: String? = null,
)

private val MAX_DROPDOWN_HEIGHT = 280.dp

/**
 * Text field with a suggestion dropdown underneath it.
 *
 * **State-free by design**: the caller owns [value], [suggestions] and the loading/error flags, so
 * debouncing and request cancellation live in the ViewModel where they can be tested. The dropdown
 * renders only while [expanded] — the caller ties that to focus and a non-blank query.
 *
 * The dropdown expands **inline**, in the layout flow, rather than floating over the content: it
 * lives inside a scrolling form whose fields already scroll themselves clear of the keyboard, and an
 * inline panel cannot clip at a screen edge or fight that scroll. It is capped at a fixed max height
 * and scrolls internally; it deliberately does **not** paginate. The backend orders matches
 * alphabetically, not by relevance, so [footer] is where the caller tells the user to narrow the
 * query (e.g. "Mostrando 8 de 143").
 */
@Composable
fun AlAutocompleteInput(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<AlSuggestion>,
    onSuggestionClick: (AlSuggestion) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    enabled: Boolean = true,
    expanded: Boolean = false,
    isLoading: Boolean = false,
    emptyText: String = "Sin resultados",
    errorText: String? = null,
    onRetry: (() -> Unit)? = null,
    footer: String? = null,
    onFocusChange: (Boolean) -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
    ) {
        AlInput(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            enabled = enabled,
            onFocusChange = onFocusChange,
            trailingIcon = {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_mingcute_search_line),
                    contentDescription = null,
                    tint = AlTheme.colors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            },
        )

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AlTheme.corners.md))
                    .background(AlTheme.colors.surfaceRaised)
                    .border(
                        width = AlTheme.borders.thin,
                        color = AlTheme.colors.border,
                        shape = RoundedCornerShape(AlTheme.corners.md),
                    ),
            ) {
                when {
                    isLoading -> DropdownMessage {
                        Spinner(size = 20.dp)
                    }

                    errorText != null -> DropdownMessage {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AlTheme.spacing.sm),
                        ) {
                            Text(
                                text = errorText,
                                style = AlTheme.typography.body,
                                color = AlTheme.colors.textMuted,
                            )
                            if (onRetry != null) {
                                AlButton(onClick = onRetry, variant = AlButtonVariant.Outline) {
                                    Text("Reintentar")
                                }
                            }
                        }
                    }

                    suggestions.isEmpty() -> DropdownMessage {
                        Text(
                            text = emptyText,
                            style = AlTheme.typography.body,
                            color = AlTheme.colors.textMuted,
                        )
                    }

                    else -> LazyColumn(
                        // A finite max height is what makes this legal inside the form's
                        // verticalScroll column, which measures children with infinite height.
                        modifier = Modifier.heightIn(max = MAX_DROPDOWN_HEIGHT),
                    ) {
                        items(items = suggestions, key = { it.id }) { suggestion ->
                            AlListRow(
                                horizontalPadding = AlTheme.spacing.md,
                                title = suggestion.title,
                                subtitle = suggestion.subtitle,
                                value = suggestion.trailing,
                                titleStyle = AlTheme.typography.body,
                                showChevron = false,
                                onClick = { onSuggestionClick(suggestion) },
                            )
                        }
                    }
                }

                footer?.let {
                    Text(
                        text = it,
                        style = AlTheme.typography.caption,
                        color = AlTheme.colors.textMuted,
                        modifier = Modifier.padding(
                            horizontal = AlTheme.spacing.md,
                            vertical = AlTheme.spacing.sm,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun DropdownMessage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AlTheme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
