package com.example.myapplication.ui.screens.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onBack: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var selectedUser by remember { mutableStateOf<com.example.myapplication.data.model.User?>(null) }
    var selectedPropertyRequest by remember { mutableStateOf<com.example.myapplication.data.model.PropertyRequest?>(null) }
    val tabs = listOf("Overview", "Users", "Broker Requests", "Admin Requests", "Property Requests")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Admin Dashboard",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { 
                            selectedTabIndex = index 
                            when(index) {
                                0 -> viewModel.loadDashboardData()
                                1 -> viewModel.loadUsers("all")
                                2 -> viewModel.loadRoleRequests("broker")
                                3 -> viewModel.loadRoleRequests("admin")
                                4 -> viewModel.loadPropertyRequests()
                            }
                        },
                        text = { 
                            Text(
                                text = title,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium
                            ) 
                        }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> DashboardOverview(stats = uiState.stats)
                1 -> UsersSection(
                    users = uiState.users,
                    onUserClick = { selectedUser = it },
                    onToggleBlock = { viewModel.toggleUserBlock(it) },
                    onFilterChange = { viewModel.loadUsers(it) }
                )
                2 -> RoleRequestsSection(
                    requests = uiState.roleRequests,
                    onApprove = { reqId, uId -> viewModel.approveRoleRequest(reqId, uId, "broker") },
                    onReject = { reqId -> viewModel.rejectRoleRequest(reqId, "broker") }
                )
                3 -> RoleRequestsSection(
                    requests = uiState.roleRequests,
                    onApprove = { reqId, uId -> viewModel.approveRoleRequest(reqId, uId, "admin") },
                    onReject = { reqId -> viewModel.rejectRoleRequest(reqId, "admin") }
                )
                4 -> PropertyRequestsSection(
                    requests = uiState.propertyRequests,
                    onRequestClick = { selectedPropertyRequest = it }
                )
            }
        }
        
        selectedUser?.let { user ->
            UserDetailsModal(
                user = user,
                onDismiss = { selectedUser = null },
                onToggleBlock = { 
                    viewModel.toggleUserBlock(user.uid)
                    selectedUser = user.copy(blocked = !user.blocked)
                },
                onChangeRole = { newRole -> 
                    viewModel.changeUserRole(user.uid, newRole)
                    selectedUser = user.copy(role = newRole)
                }
            )
        }
        
        selectedPropertyRequest?.let { request ->
            PropertyRequestDetailsModal(
                request = request,
                onDismiss = { selectedPropertyRequest = null }
            )
        }
    }
}
