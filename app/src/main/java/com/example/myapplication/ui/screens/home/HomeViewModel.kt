package com.example.myapplication.ui.screens.home

import androidx.lifecycle.ViewModel
import com.example.myapplication.data.repository.AuthRepository

class HomeViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    fun getCurrentUserId(): String? = authRepository.getCurrentUserId()
}
