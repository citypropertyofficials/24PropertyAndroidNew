package com.example.myapplication.ui.screens.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.myapplication.R
import com.example.myapplication.data.model.User
import com.example.myapplication.ui.common.AppPrimaryButton
import com.example.myapplication.ui.theme.PrimaryStart
import com.example.myapplication.utils.FirebaseConstants
import org.koin.androidx.compose.koinViewModel

@Composable
fun ProfileScreen(
    onNavigateToHome: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: ProfileViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProfileEvent.NavigateToHome -> onNavigateToHome()
                is ProfileEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is ProfileEvent.Logout -> onLogout()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            when (val state = uiState) {
                is ProfileUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is ProfileUiState.Error -> {
                    ProfileErrorContent(
                        message = state.message,
                        onRetry = { viewModel.loadProfile() }
                    )
                }
                is ProfileUiState.Loaded -> {
                    ProfileFormContent(
                        user = state.user,
                        pendingRequests = state.pendingRequests,
                        isNewUser = state.isNewUser,
                        isSaving = state.isSaving,
                        onSave = { name, mobile, role ->
                            viewModel.saveProfile(name, mobile, role)
                        },
                        onRoleRequest = { role ->
                            viewModel.submitRoleRequest(role)
                        },
                        onLogout = { viewModel.logout() },
                        onBack = onBack
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.error_loading_profile),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.retry))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileFormContent(
    user: User,
    pendingRequests: List<String>,
    isNewUser: Boolean,
    isSaving: Boolean,
    onSave: (name: String, mobile: String, role: String?) -> Unit,
    onRoleRequest: (String) -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit = {}
) {
    var name by rememberSaveable { mutableStateOf(user.name) }
    var mobile by rememberSaveable { mutableStateOf(user.mobile) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var mobileError by remember { mutableStateOf<String?>(null) }
    var requestedRole by rememberSaveable { mutableStateOf(FirebaseConstants.ROLE_USER) }

    val avatarUrl = user.photoUrl.takeIf { it.isNotBlank() }
        ?: "https://ui-avatars.com/api/?name=${user.name.takeIf { it.isNotBlank() } ?: "User"}&background=667eea&color=ffffff&size=150"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Back button
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        }

        // Header
        Text(
            text = if (isNewUser) stringResource(R.string.complete_your_profile) else stringResource(R.string.edit_profile),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Profile Image
        Image(
            painter = rememberAsyncImagePainter(model = avatarUrl),
            contentDescription = stringResource(R.string.profile_picture),
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isNewUser) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = stringResource(R.string.complete_profile_message),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Email (read-only)
        OutlinedTextField(
            value = user.email,
            onValueChange = { },
            readOnly = true,
            label = { Text(stringResource(R.string.email)) },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Name
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                nameError = null
            },
            label = { Text(stringResource(R.string.name_required)) },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            isError = nameError != null,
            supportingText = nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Mobile
        OutlinedTextField(
            value = mobile,
            onValueChange = {
                mobile = it
                mobileError = null
            },
            label = { Text(stringResource(R.string.mobile_required)) },
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            isError = mobileError != null,
            supportingText = mobileError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Role Section
        if (isNewUser) {
            NewUserRoleSection(
                requestedRole = requestedRole,
                onRoleChange = { requestedRole = it }
            )
        } else {
            ExistingUserRoleSection(
                currentRole = user.role,
                pendingRequests = pendingRequests,
                onRoleRequest = onRoleRequest
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Save Button
        val nameRequiredError = stringResource(R.string.name_required_error)
        val mobileRequiredError = stringResource(R.string.mobile_required_error)
        AppPrimaryButton(
            text = if (isNewUser) stringResource(R.string.complete_profile) else stringResource(R.string.save_changes),
            onClick = {
                nameError = if (name.isBlank()) nameRequiredError else null
                mobileError = if (mobile.isBlank()) mobileRequiredError else null

                if (nameError == null && mobileError == null) {
                    onSave(name, mobile, requestedRole)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            isLoading = isSaving
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Logout (for new users)
        if (isNewUser) {
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.logout))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewUserRoleSection(
    requestedRole: String,
    onRoleChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val roles = listOf(
        FirebaseConstants.ROLE_USER to "Normal User (Buyer)",
        FirebaseConstants.ROLE_BROKER to "Real Estate Broker",
        FirebaseConstants.ROLE_ADMIN to "Administrator"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.i_want_to_join_as),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = roles.firstOrNull { it.first == requestedRole }?.second ?: "",
                onValueChange = { },
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                roles.forEach { (role, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onRoleChange(role)
                            expanded = false
                        }
                    )
                }
            }
        }
        if (requestedRole != FirebaseConstants.ROLE_USER) {
            Text(
                text = stringResource(R.string.role_approval_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExistingUserRoleSection(
    currentRole: String,
    pendingRequests: List<String>,
    onRoleRequest: (String) -> Unit
) {
    var showRoleDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.current_role),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Badge(
                containerColor = PrimaryStart
            ) {
                Text(
                    text = currentRole.replaceFirstChar { it.uppercase() },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            if (currentRole != FirebaseConstants.ROLE_SUPERADMIN && currentRole != FirebaseConstants.ROLE_DEVELOPER) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.request_role_change),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showRoleDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.request_new_role))
                }
            }

            if (pendingRequests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.pending_requests, pendingRequests.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (showRoleDialog) {
        RoleRequestDialog(
            currentRole = currentRole,
            pendingRequests = pendingRequests,
            onConfirm = { role ->
                onRoleRequest(role)
                showRoleDialog = false
            },
            onDismiss = { showRoleDialog = false }
        )
    }
}

@Composable
private fun RoleRequestDialog(
    currentRole: String,
    pendingRequests: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val availableRoles = listOf(
        FirebaseConstants.ROLE_USER,
        FirebaseConstants.ROLE_BROKER,
        FirebaseConstants.ROLE_ADMIN
    ).filter { it != currentRole }

    var selectedRole by remember { mutableStateOf(availableRoles.firstOrNull() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.request_role_change)) },
        text = {
            Column {
                availableRoles.forEach { role ->
                    val isPending = pendingRequests.contains(role)
                    val label = when (role) {
                        FirebaseConstants.ROLE_USER -> "Normal User"
                        else -> role.replaceFirstChar { it.uppercase() }
                    } + if (isPending) " (Pending)" else ""

                    Button(
                        onClick = {
                            if (!isPending) {
                                selectedRole = role
                                onConfirm(role)
                            }
                        },
                        enabled = !isPending,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(label)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        confirmButton = { },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
