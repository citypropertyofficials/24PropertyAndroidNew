package com.example.myapplication.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.Property
import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.data.repository.PropertyPageResult
import com.example.myapplication.data.repository.PropertyRepository
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class HomeUiState {
    data object Loading : HomeUiState()
    data class Loaded(
        val properties: List<Property>,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = false
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
    data object Empty : HomeUiState()
}

sealed class HomeEvent {
    data class ShowMessage(val message: String) : HomeEvent()
}

class HomeViewModel(
    private val authRepository: AuthRepository,
    private val propertyRepository: PropertyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>()
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    private var nextCursor: DocumentSnapshot? = null
    private val pageSize = 10

    init {
        loadProperties(reset = true)
    }

    fun loadProperties(reset: Boolean = false) {
        viewModelScope.launch {
            if (reset) {
                _uiState.value = HomeUiState.Loading
                nextCursor = null
            } else {
                val current = _uiState.value
                if (current is HomeUiState.Loaded) {
                    _uiState.value = current.copy(isLoadingMore = true)
                }
            }

            try {
                val viewerRole = authRepository.getCurrentUserRole() ?: "user"

                val result = propertyRepository.fetchPropertiesPage(
                    limitCount = pageSize,
                    cursor = if (reset) null else nextCursor,
                    viewerRole = viewerRole
                )

                if (result.items.isEmpty() && reset) {
                    _uiState.value = HomeUiState.Empty
                    return@launch
                }

                nextCursor = result.nextCursor

                if (reset) {
                    _uiState.value = HomeUiState.Loaded(
                        properties = result.items,
                        hasMore = result.hasMore
                    )
                } else {
                    val current = _uiState.value
                    if (current is HomeUiState.Loaded) {
                        _uiState.value = current.copy(
                            properties = current.properties + result.items,
                            isLoadingMore = false,
                            hasMore = result.hasMore
                        )
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error loading properties"
                _uiState.value = HomeUiState.Error(errorMsg)
                _events.emit(HomeEvent.ShowMessage("Error: $errorMsg"))
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value
        if (current is HomeUiState.Loaded && current.hasMore && !current.isLoadingMore) {
            loadProperties(reset = false)
        }
    }

    fun retry() {
        loadProperties(reset = true)
    }
}
