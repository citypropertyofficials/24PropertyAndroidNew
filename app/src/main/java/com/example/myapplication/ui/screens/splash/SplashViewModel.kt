package com.example.myapplication.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.data.repository.AuthResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed class SplashEvent {
    data object Idle : SplashEvent()
    data object NavigateToMain : SplashEvent()
    data object NavigateToProfile : SplashEvent()
    data object NavigateToLogin : SplashEvent()
}

class SplashViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _navigationEvent = MutableStateFlow<SplashEvent>(SplashEvent.Idle)
    val navigationEvent: StateFlow<SplashEvent> = _navigationEvent.asStateFlow()

    init {
        checkAuthState()
    }

    private fun checkAuthState() {
        viewModelScope.launch {
            try {
                // Small delay so splash screen is visible briefly for a polished feel
                delay(1_000)

                if (authRepository.isUserSignedIn()) {
                    val userId = authRepository.getCurrentUserId()
                    if (userId != null) {
                        val result = withTimeoutOrNull(8_000) {
                            authRepository.getUserData(userId)
                        }
                        when (result) {
                            is AuthResult.Success -> {
                                if (result.data.isProfileComplete) {
                                    _navigationEvent.value = SplashEvent.NavigateToMain
                                } else {
                                    _navigationEvent.value = SplashEvent.NavigateToProfile
                                }
                            }
                            else -> {
                                // Timeout (null) or Error — send to login
                                _navigationEvent.value = SplashEvent.NavigateToLogin
                            }
                        }
                    } else {
                        _navigationEvent.value = SplashEvent.NavigateToLogin
                    }
                } else {
                    _navigationEvent.value = SplashEvent.NavigateToLogin
                }
            } catch (e: Exception) {
                _navigationEvent.value = SplashEvent.NavigateToLogin
            }
        }
    }
}
