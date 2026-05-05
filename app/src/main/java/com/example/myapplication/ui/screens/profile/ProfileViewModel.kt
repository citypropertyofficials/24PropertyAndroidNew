package com.example.myapplication.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.User
import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.data.repository.AuthResult
import com.example.myapplication.utils.FirebaseConstants
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ProfileUiState {
    data object Loading : ProfileUiState()
    data class Loaded(
        val user: User,
        val pendingRequests: List<String> = emptyList(),
        val isNewUser: Boolean = false,
        val isSaving: Boolean = false
    ) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

sealed class ProfileEvent {
    data object NavigateToHome : ProfileEvent()
    data class ShowMessage(val message: String) : ProfileEvent()
    data object Logout : ProfileEvent()
}

class ProfileViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ProfileEvent>()
    val events: SharedFlow<ProfileEvent> = _events.asSharedFlow()

    private var currentUser: User? = null

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                _events.emit(ProfileEvent.Logout)
                return@launch
            }
            when (val result = authRepository.getUserData(userId)) {
                is AuthResult.Success -> {
                    currentUser = result.data
                    val pendingResult = authRepository.fetchPendingRoleRequests(userId)
                    val pending = if (pendingResult is AuthResult.Success) pendingResult.data else emptyList()
                    _uiState.value = ProfileUiState.Loaded(
                        user = result.data,
                        pendingRequests = pending,
                        isNewUser = !result.data.isProfileComplete
                    )
                }
                is AuthResult.Error -> {
                    _uiState.value = ProfileUiState.Error(result.message)
                }
                AuthResult.Loading -> { /* no-op */ }
            }
        }
    }

    fun saveProfile(
        name: String,
        mobile: String,
        requestedRole: String? = null
    ) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                _events.emit(ProfileEvent.Logout)
                return@launch
            }

            // Set saving state
            val currentState = _uiState.value
            if (currentState is ProfileUiState.Loaded) {
                _uiState.value = currentState.copy(isSaving = true)
            }

            val current = currentUser
            val isNew = current == null || !current.isProfileComplete

            val profileData = mutableMapOf<String, Any>(
                FirebaseConstants.FIELD_NAME to name.trim(),
                FirebaseConstants.FIELD_MOBILE to mobile.trim(),
                FirebaseConstants.FIELD_EMAIL to (current?.email ?: authRepository.getCurrentUserEmail() ?: "")
            )
            if (current?.photoUrl?.isNotBlank() == true) {
                profileData[FirebaseConstants.FIELD_PHOTO_URL] = current.photoUrl
            }

            when (val updateResult = authRepository.updateUserProfile(userId, profileData)) {
                is AuthResult.Success -> {
                    // Handle role request for new users
                    if (isNew && requestedRole != null &&
                        requestedRole != FirebaseConstants.ROLE_USER &&
                        requestedRole != (current?.role ?: FirebaseConstants.ROLE_USER)
                    ) {
                        val existing = authRepository.checkExistingRoleRequest(userId, requestedRole)
                        if (existing is AuthResult.Success && !existing.data) {
                            authRepository.submitRoleRequest(userId, requestedRole)
                        }
                    }

                    // Re-fetch user data
                    when (val refreshResult = authRepository.getUserData(userId)) {
                        is AuthResult.Success -> {
                            currentUser = refreshResult.data
                            _events.emit(ProfileEvent.ShowMessage("Profile saved successfully!"))
                            if (refreshResult.data.isProfileComplete) {
                                _events.emit(ProfileEvent.NavigateToHome)
                            } else {
                                loadProfile()
                            }
                        }
                        else -> {
                            if (name.isNotBlank() && mobile.isNotBlank()) {
                                _events.emit(ProfileEvent.NavigateToHome)
                            } else {
                                _events.emit(ProfileEvent.ShowMessage("Profile saved! Please complete all required fields."))
                            }
                        }
                    }
                }
                is AuthResult.Error -> {
                    _events.emit(ProfileEvent.ShowMessage("Failed to save: ${updateResult.message}"))
                    // Clear saving state on error
                    val s = _uiState.value
                    if (s is ProfileUiState.Loaded) {
                        _uiState.value = s.copy(isSaving = false)
                    }
                }
                AuthResult.Loading -> { }
            }
        }
    }

    fun submitRoleRequest(role: String) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            when (val existing = authRepository.checkExistingRoleRequest(userId, role)) {
                is AuthResult.Success -> {
                    if (existing.data) {
                        _events.emit(ProfileEvent.ShowMessage("Request already pending."))
                        return@launch
                    }
                }
                else -> { }
            }
            when (val result = authRepository.submitRoleRequest(userId, role)) {
                is AuthResult.Success -> {
                    _events.emit(ProfileEvent.ShowMessage("Request for $role submitted."))
                    loadProfile()
                }
                is AuthResult.Error -> {
                    _events.emit(ProfileEvent.ShowMessage(result.message))
                }
                AuthResult.Loading -> { }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.signOut()
            _events.emit(ProfileEvent.Logout)
        }
    }
}
