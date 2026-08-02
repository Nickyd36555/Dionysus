@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.dionysus.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.dionysus.tv.ui.navigation.TopLevelDestination

/**
 * The persistent left navigation rail shared by all top-level screens. Focus
 * moves naturally between the rail and the screen content to its right.
 */
@Composable
fun MainScaffold(
    selected: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Dionysus",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp, bottom = 16.dp),
            )
            TopLevelDestination.entries.forEach { dest ->
                ListItem(
                    selected = dest == selected,
                    onClick = { onSelect(dest) },
                    leadingContent = { Icon(iconFor(dest), contentDescription = dest.label) },
                    headlineContent = { Text(dest.label) },
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Box(modifier = Modifier
            .weight(1f)
            .fillMaxHeight()) {
            content()
        }
    }
}

private fun iconFor(dest: TopLevelDestination): ImageVector = when (dest) {
    TopLevelDestination.HOME -> Icons.Default.Home
    TopLevelDestination.SEARCH -> Icons.Default.Search
    TopLevelDestination.DOWNLOADS -> Icons.Default.Download
    TopLevelDestination.SETTINGS -> Icons.Default.Settings
}
