package ar.com.ingenieriaalimentacion.core.design_system.components.navbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme

enum class AlBottomNavTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Settings("Settings", Icons.Default.Settings),
}

@Composable
fun AlBottomNavBar(
    selected: AlBottomNavTab,
    onSelected: (AlBottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AlTheme.colors.surfaceBase)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(64.dp)
            .padding(horizontal = AlTheme.spacing.sm),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlBottomNavTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val tint = if (isSelected) AlTheme.colors.accent else AlTheme.colors.textMuted
            Column(
                modifier = Modifier
                    .clickable { onSelected(tab) }
                    .padding(AlTheme.spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = tab.label,
                    style = AlTheme.typography.caption,
                    color = tint,
                )
            }
        }
    }
}
