package com.example.myapplication.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.data.repository.AuthResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed class SplashEvent {
    data object NavigateToMain : SplashEvent()
    data object NavigateToProfile : SplashEvent()
    data object NavigateToLogin : SplashEvent()
}

class SplashViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _events = MutableSharedFlow<SplashEvent>()
    val events: SharedFlow<SplashEvent> = _events.asSharedFlow()

    init {
        checkAuthState()
    }

    private fun checkAuthState() {
        viewModelScope.launch {
            if (authRepository.isUserSignedIn()) {
                val userId = authRepository.getCurrentUserId()
                if (userId != null) {
                    when (val result = authRepository.getUserData(userId)) {
                        is AuthResult.Success -> {
                            if (result.data.isProfileComplete) {
                                _events.emit(SplashEvent.NavigateToMain)
                            } else {
                                _events.emit(SplashEvent.NavigateToProfile)
                            }
                        }
                        else -> {
                            // Failed to fetch user data, treat as logged in but profile incomplete
                            _events.emit(SplashEvent.NavigateToProfile)
                        }
                    }
                } else {
                    _events.emit(SplashEvent.NavigateToLogin)
                }
            } else {
                _events.emit(SplashEvent.NavigateToLogin)
            }
        }
    }
}
