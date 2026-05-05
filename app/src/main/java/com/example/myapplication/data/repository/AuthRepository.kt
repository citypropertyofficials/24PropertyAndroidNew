package com.example.myapplication.data.repository

import com.example.myapplication.data.model.User
import kotlinx.coroutines.flow.Flow

sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val message: String) : AuthResult<Nothing>()
    data object Loading : AuthResult<Nothing>()
}

interface AuthRepository {
    fun isUserSignedIn(): Boolean
    fun getCurrentUserId(): String?
    fun getCurrentUserEmail(): String?
    suspend fun getCurrentUserRole(): String?
    suspend fun signInWithGoogle(idToken: String): AuthResult<User>
    suspend fun signOut(): AuthResult<Unit>
    suspend fun getUserData(userId: String): AuthResult<User>
    fun observeAuthState(): Flow<Boolean>

    // Profile
    suspend fun updateUserProfile(userId: String, profileData: Map<String, Any>): AuthResult<Unit>

    // Role Requests
    suspend fun submitRoleRequest(userId: String, requestedRole: String): AuthResult<String>
    suspend fun fetchPendingRoleRequests(userId: String): AuthResult<List<String>>
    suspend fun checkExistingRoleRequest(userId: String, requestedRole: String): AuthResult<Boolean>
}
