package com.example.myapplication.data.repository

import com.example.myapplication.data.model.DashboardStats
import com.example.myapplication.data.model.PropertyRequest
import com.example.myapplication.data.model.RoleRequest
import com.example.myapplication.data.model.User

sealed class DashboardResult<out T> {
    data class Success<T>(val data: T) : DashboardResult<T>()
    data class Error(val message: String) : DashboardResult<Nothing>()
    data object Loading : DashboardResult<Nothing>()
}

interface DashboardRepository {
    suspend fun getDashboardStats(viewerRole: String): DashboardResult<DashboardStats>
    suspend fun loadUsers(filter: String, viewerRole: String): DashboardResult<List<User>>
    suspend fun loadPropertyRequests(viewerRole: String): DashboardResult<List<PropertyRequest>>
    suspend fun loadRoleRequests(roleTypeFilter: String, viewerRole: String): DashboardResult<List<RoleRequest>>
    suspend fun toggleUserBlock(userId: String, actorRole: String): DashboardResult<Boolean>
    suspend fun updateUserRole(userId: String, newRole: String, actorUserId: String, actorRole: String): DashboardResult<Boolean>
    suspend fun approveRoleRequest(requestId: String, userId: String, newRole: String, actorUserId: String, actorRole: String): DashboardResult<Boolean>
    suspend fun rejectRoleRequest(requestId: String, actorRole: String): DashboardResult<Boolean>
}
