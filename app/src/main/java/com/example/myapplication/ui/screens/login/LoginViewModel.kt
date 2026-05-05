package com.example.myapplication.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.User
import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.data.repository.AuthResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()
    data class Success(val user: User, val needsProfileCompletion: Boolean) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

sealed class LoginEvent {
    data class NavigateToMain(val needsProfileCompletion: Boolean) : LoginEvent()
    data class ShowError(val message: String) : LoginEvent()
}

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LoginEvent>()
    val events: SharedFlow<LoginEvent> = _events.asSharedFlow()

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            when (val result = authRepository.signInWithGoogle(idToken)) {
                is AuthResult.Success -> {
                    val user = result.data
                    val needsProfile = !user.isProfileComplete
                    _uiState.value = LoginUiState.Success(user, needsProfile)
                    _events.emit(LoginEvent.NavigateToMain(needsProfile))
                }
                is AuthResult.Error -> {
                    _uiState.value = LoginUiState.Error(result.message)
                    _events.emit(LoginEvent.ShowError(result.message))
                }
                AuthResult.Loading -> {
                    _uiState.value = LoginUiState.Loading
                }
            }
        }
    }

    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }
}
