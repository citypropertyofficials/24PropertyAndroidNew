package com.example.myapplication.ui.screens.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.utils.FirebaseConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MoreUiState(
    val userRole: String = FirebaseConstants.ROLE_USER,
    val isLoading: Boolean = true
)

class MoreViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MoreUiState())
    val uiState: StateFlow<MoreUiState> = _uiState.asStateFlow()

    init {
        fetchUserRole()
    }

    private fun fetchUserRole() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val role = authRepository.getCurrentUserRole() ?: FirebaseConstants.ROLE_USER
            _uiState.value = _uiState.value.copy(
                userRole = role,
                isLoading = false
            )
        }
    }
}
