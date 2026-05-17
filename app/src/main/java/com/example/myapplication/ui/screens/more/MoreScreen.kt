package com.example.myapplication.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.ui.theme.AccentColor
import com.example.myapplication.ui.theme.BackgroundSecondary
import com.example.myapplication.ui.theme.BorderColor
import com.example.myapplication.ui.theme.DangerColor
import com.example.myapplication.ui.theme.GoldStart
import com.example.myapplication.ui.theme.PrimaryEnd
import com.example.myapplication.ui.theme.PrimaryStart
import com.example.myapplication.ui.theme.TextSecondary
import com.example.myapplication.utils.FirebaseConstants
import org.koin.androidx.compose.koinViewModel

@Composable
fun MoreScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToMyProperties: () -> Unit,
    onLogout: () -> Unit,
    viewModel: MoreViewModel = koinViewModel()
) {
    val scrollState = rememberScrollState()
    val uiState by viewModel.uiState.collectAsState()
    val showAdminTools = !uiState.isLoading && (
        uiState.userRole == FirebaseConstants.ROLE_SUPERADMIN ||
            uiState.userRole == FirebaseConstants.ROLE_DEVELOPER
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = stringResource(R.string.more),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )

        MoreSection(
            title = stringResource(R.string.more_quick_access)
        ) {
            MoreMenuCard(
                icon = Icons.Default.AccountCircle,
                iconTint = PrimaryStart,
                label = stringResource(R.string.profile),
                description = stringResource(R.string.more_profile_description),
                onClick = onNavigateToProfile
            )
            Spacer(modifier = Modifier.height(12.dp))
            MoreMenuCard(
                icon = Icons.Default.Home,
                iconTint = AccentColor,
                label = stringResource(R.string.my_properties),
                description = stringResource(R.string.more_my_properties_description),
                onClick = onNavigateToMyProperties
            )
        }

        if (showAdminTools) {
            MoreSection(
                title = stringResource(R.string.more_admin_tools)
            ) {
                MoreMenuCard(
                    icon = Icons.Default.Lock,
                    iconTint = GoldStart,
                    label = stringResource(R.string.dashboard),
                    description = stringResource(R.string.more_dashboard_description),
                    onClick = onNavigateToDashboard
                )
            }
        }

        MoreSection(
            title = stringResource(R.string.more_preferences_support)
        ) {
            MoreMenuCard(
                icon = Icons.Default.Settings,
                iconTint = PrimaryEnd,
                label = stringResource(R.string.settings),
                description = stringResource(R.string.more_settings_description),
                onClick = { }
            )
            Spacer(modifier = Modifier.height(12.dp))
            MoreMenuCard(
                icon = Icons.Default.Info,
                iconTint = PrimaryStart,
                label = stringResource(R.string.about),
                description = stringResource(R.string.more_about_description),
                onClick = { }
            )
            Spacer(modifier = Modifier.height(12.dp))
            MoreMenuCard(
                icon = Icons.Default.Info,
                iconTint = AccentColor,
                label = stringResource(R.string.help_support),
                description = stringResource(R.string.more_help_description),
                onClick = { }
            )
        }

        LogoutCard(
            onLogout = onLogout
        )
    }
}

@Composable
private fun MoreSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        content()
    }
}

@Composable
private fun MoreMenuCard(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = BackgroundSecondary)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(22.dp),
                        tint = iconTint
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp, end = 10.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = BorderColor.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun LogoutCard(
    onLogout: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLogout),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = BackgroundSecondary)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DangerColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.logout),
                    tint = DangerColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.logout),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.more_logout_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
