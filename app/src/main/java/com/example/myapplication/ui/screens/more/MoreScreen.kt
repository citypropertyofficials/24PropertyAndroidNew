package com.example.myapplication.ui.screens.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.myapplication.R

@Composable
fun MoreScreen(
    onNavigateToProfile: () -> Unit,
    onLogout: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Text(
            text = stringResource(R.string.more),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
        )

        MoreMenuItem(
            icon = Icons.Default.AccountCircle,
            label = stringResource(R.string.profile),
            onClick = onNavigateToProfile
        )
        Divider(modifier = Modifier.padding(horizontal = 16.dp))

        MoreMenuItem(
            icon = Icons.Default.Settings,
            label = stringResource(R.string.settings),
            onClick = { /* TODO */ }
        )
        Divider(modifier = Modifier.padding(horizontal = 16.dp))

        MoreMenuItem(
            icon = Icons.Default.Info,
            label = stringResource(R.string.about),
            onClick = { /* TODO */ }
        )
        Divider(modifier = Modifier.padding(horizontal = 16.dp))

        MoreMenuItem(
            icon = Icons.Default.Info,
            label = stringResource(R.string.help_support),
            onClick = { /* TODO */ }
        )
        Divider(modifier = Modifier.padding(horizontal = 16.dp))

        MoreMenuItem(
            icon = Icons.Default.Close,
            label = stringResource(R.string.logout),
            onClick = onLogout
        )
    }
}

@Composable
private fun MoreMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
