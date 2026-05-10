package com.example.myapplication.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.DashboardStats
import com.example.myapplication.data.model.PropertyRequest
import com.example.myapplication.data.model.RoleRequest
import com.example.myapplication.data.model.User
import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.data.repository.DashboardRepository
import com.example.myapplication.data.repository.DashboardResult
import com.example.myapplication.utils.FirebaseConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = false,
    val stats: DashboardStats = DashboardStats(),
    val users: List<User> = emptyList(),
    val roleRequests: List<RoleRequest> = emptyList(),
    val propertyRequests: List<PropertyRequest> = emptyList(),
    val error: String? = null
)

class DashboardViewModel(
    private val dashboardRepository: DashboardRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var currentUserRole: String = FirebaseConstants.ROLE_SUPERADMIN

    init {
        viewModelScope.launch {
            currentUserRole = authRepository.getCurrentUserRole() ?: FirebaseConstants.ROLE_SUPERADMIN
            loadDashboardData()
        }
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            when (val statsResult = dashboardRepository.getDashboardStats(currentUserRole)) {
                is DashboardResult.Success -> {
                    _uiState.value = _uiState.value.copy(stats = statsResult.data)
                }
                is DashboardResult.Error -> {
                    _uiState.value = _uiState.value.copy(error = statsResult.message)
                }
                else -> {}
            }
            
            loadUsers("all")
            loadRoleRequests("broker")
            loadPropertyRequests()
            
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun loadUsers(filter: String) {
        viewModelScope.launch {
            when (val usersResult = dashboardRepository.loadUsers(filter, currentUserRole)) {
                is DashboardResult.Success -> {
                    _uiState.value = _uiState.value.copy(users = usersResult.data)
                }
                else -> {}
            }
        }
    }

    fun loadRoleRequests(roleType: String) {
        viewModelScope.launch {
            when (val requestsResult = dashboardRepository.loadRoleRequests(roleType, currentUserRole)) {
                is DashboardResult.Success -> {
                    _uiState.value = _uiState.value.copy(roleRequests = requestsResult.data)
                }
                else -> {}
            }
        }
    }

    fun loadPropertyRequests() {
        viewModelScope.launch {
            when (val requestsResult = dashboardRepository.loadPropertyRequests(currentUserRole)) {
                is DashboardResult.Success -> {
                    _uiState.value = _uiState.value.copy(propertyRequests = requestsResult.data)
                }
                else -> {}
            }
        }
    }

    fun toggleUserBlock(userId: String) {
        viewModelScope.launch {
            dashboardRepository.toggleUserBlock(userId, currentUserRole)
            loadUsers("all") // reload
        }
    }

    fun changeUserRole(userId: String, newRole: String) {
        viewModelScope.launch {
            val actorId = authRepository.getCurrentUserId() ?: return@launch
            dashboardRepository.updateUserRole(userId, newRole, actorId, currentUserRole)
            loadUsers("all") // reload
        }
    }

    fun approveRoleRequest(requestId: String, userId: String, newRole: String) {
        viewModelScope.launch {
            val actorId = authRepository.getCurrentUserId() ?: return@launch
            dashboardRepository.approveRoleRequest(requestId, userId, newRole, actorId, currentUserRole)
            loadRoleRequests(newRole)
        }
    }

    fun rejectRoleRequest(requestId: String, roleType: String) {
        viewModelScope.launch {
            dashboardRepository.rejectRoleRequest(requestId, currentUserRole)
            loadRoleRequests(roleType)
        }
    }
}
