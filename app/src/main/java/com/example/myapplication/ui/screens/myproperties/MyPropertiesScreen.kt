package com.example.myapplication.ui.screens.myproperties

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.data.model.Property
import com.example.myapplication.ui.common.AppFullScreenLoading
import com.example.myapplication.ui.common.InterestedUsersBottomSheet
import com.example.myapplication.ui.common.OwnerPropertyCard
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPropertiesScreen(
    onBack: () -> Unit,
    onNavigateToPropertyDetails: (String) -> Unit,
    onEditProperty: (Property) -> Unit,
    viewModel: MyPropertiesViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var propertyToDelete by remember { mutableStateOf<Property?>(null) }

    // Interested users bottom sheet state
    var interestedPropertyForSheet by remember { mutableStateOf<Property?>(null) }
    val interestedUsers by viewModel.interestedUsers.collectAsState()
    val isLoadingInterestedUsers by viewModel.isLoadingInterestedUsers.collectAsState()
    val interestedUsersError by viewModel.interestedUsersError.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MyPropertiesEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(event.messageResId)
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_properties)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is MyPropertiesUiState.Loading -> {
                    AppFullScreenLoading()
                }
                is MyPropertiesUiState.Error -> {
                    ErrorMyPropertiesContent(
                        message = stringResource(state.messageResId),
                        onRetry = viewModel::retry
                    )
                }
                is MyPropertiesUiState.Content -> {
                    val contentState = state.state
                    PullToRefreshBox(
                        isRefreshing = contentState.isRefreshing,
                        onRefresh = viewModel::refresh,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.my_properties_description),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    // Property count badge (shows limit for normal users)
                                    contentState.maxPropertiesAllowed?.let { maxAllowed ->
                                        val countText = stringResource(
                                            R.string.property_count_format,
                                            contentState.totalPropertyCount,
                                            maxAllowed
                                        )
                                        val isAtLimit = contentState.totalPropertyCount >= maxAllowed
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = countText,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (isAtLimit) {
                                                    MaterialTheme.colorScheme.error
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                            if (isAtLimit) {
                                                Text(
                                                    text = stringResource(R.string.property_limit_reached),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    MyPropertiesFilterRow(
                                        currentFilter = contentState.currentFilter,
                                        onFilterSelected = viewModel::updateFilter
                                    )
                                }
                            }

                            if (contentState.properties.isEmpty()) {
                                item {
                                    EmptyMyPropertiesContent(
                                        filter = contentState.currentFilter,
                                        onRetry = viewModel::retry
                                    )
                                }
                            } else {
                                items(contentState.properties, key = { it.id }) { property ->
                                    OwnerPropertyCard(
                                        property = property,
                                        isDeleting = contentState.deletingPropertyId == property.id,
                                        onViewDetails = {
                                            onNavigateToPropertyDetails(property.id)
                                        },
                                        onEdit = {
                                            onEditProperty(property)
                                        },
                                        onDelete = {
                                            propertyToDelete = property
                                        },
                                        onViewInterestedUsers = {
                                            interestedPropertyForSheet = property
                                            viewModel.loadInterestedUsers(property.id)
                                        }
                                    )
                                }

                                if (contentState.isLoadingMore) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator()
                                        }
                                    }
                                }

                                if (contentState.hasMore && !contentState.isLoadingMore) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Button(onClick = viewModel::loadMore) {
                                                Text(stringResource(R.string.load_more_properties))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    propertyToDelete?.let { property ->
        AlertDialog(
            onDismissRequest = { propertyToDelete = null },
            title = { Text(stringResource(R.string.delete_property_title)) },
            text = {
                Text(
                    stringResource(R.string.delete_property_message, property.name)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProperty(property.id)
                        propertyToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { propertyToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Interested Users Bottom Sheet
    interestedPropertyForSheet?.let { property ->
        InterestedUsersBottomSheet(
            propertyName = property.name,
            interestedUsers = interestedUsers,
            isLoading = isLoadingInterestedUsers,
            errorMessage = interestedUsersError,
            onDismiss = { interestedPropertyForSheet = null },
            onShare = {
                if (interestedUsers.isEmpty()) return@InterestedUsersBottomSheet
                val dateFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                val shareText = buildString {
                    appendLine("Interested Users for \"${property.name}\"")
                    appendLine()
                    interestedUsers.forEachIndexed { index, user ->
                        appendLine("${index + 1}. ${user.name.ifBlank { "Not Set" }}")
                        if (user.email.isNotBlank()) appendLine("   Email: ${user.email}")
                        if (user.mobile.isNotBlank()) appendLine("   Mobile: ${user.mobile}")
                        val dateStr = user.createdAt?.let { dateFormatter.format(it.toDate()) } ?: "—"
                        appendLine("   Date: $dateStr")
                        appendLine()
                    }
                    append("Total: ${interestedUsers.size} interested user${if (interestedUsers.size != 1) "s" else ""}")
                }
                val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, context.getString(R.string.share_interested_users_subject, property.name))
                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                }
                val chooser = android.content.Intent.createChooser(sendIntent, context.getString(R.string.share))
                context.startActivity(chooser)
            }
        )
    }
}
