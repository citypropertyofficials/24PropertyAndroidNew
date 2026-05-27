package com.example.myapplication.ui.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.Property
import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.data.repository.FavoritesRepository
import com.example.myapplication.data.repository.InterestedRepository
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class FavoritesUiState {
    data object Loading : FavoritesUiState()
    data class Loaded(
        val properties: List<Property>,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = false,
        val isRefreshing: Boolean = false
    ) : FavoritesUiState()
    data class Error(val message: String) : FavoritesUiState()
    data object Empty : FavoritesUiState()
}

sealed class FavoritesEvent {
    data class ShowMessage(val message: String) : FavoritesEvent()
}

class FavoritesViewModel(
    private val authRepository: AuthRepository,
    private val favoritesRepository: FavoritesRepository,
    private val interestedRepository: InterestedRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<FavoritesUiState>(FavoritesUiState.Loading)
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FavoritesEvent>()
    val events: SharedFlow<FavoritesEvent> = _events.asSharedFlow()

    val favoriteIds: StateFlow<Set<String>> = favoritesRepository.observeFavoriteIds()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    // Interested property IDs — real-time listener (same pattern as favoriteIds)
    val interestedIds: StateFlow<Set<String>> = interestedRepository.observeInterestedPropertyIds()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    // Per-property loading state for interested toggle
    private val _interestedLoadingMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val interestedLoadingMap: StateFlow<Map<String, Boolean>> = _interestedLoadingMap.asStateFlow()

    private var nextCursor: DocumentSnapshot? = null
    private val pageSize = 10

    // Current user ID for ownership check
    val currentUserId: String? get() = authRepository.getCurrentUserId()

    init {
        loadFavorites(reset = true)
    }

    fun refresh() {
        val current = _uiState.value
        if (current is FavoritesUiState.Loaded && current.isRefreshing) return
        viewModelScope.launch {
            val current = _uiState.value
            if (current is FavoritesUiState.Loaded) {
                _uiState.value = current.copy(isRefreshing = true)
            } else {
                _uiState.value = FavoritesUiState.Loading
            }
            nextCursor = null
            try {
                val viewerRole = authRepository.getCurrentUserRole() ?: "user"
                val result = favoritesRepository.fetchUserFavoritesPage(
                    limitCount = pageSize,
                    cursor = null,
                    viewerRole = viewerRole
                )
                if (result.items.isEmpty()) {
                    _uiState.value = FavoritesUiState.Empty
                    return@launch
                }
                nextCursor = result.nextCursor
                _uiState.value = FavoritesUiState.Loaded(
                    properties = result.items,
                    hasMore = result.hasMore
                )
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error refreshing favorites"
                val prev = _uiState.value
                if (prev is FavoritesUiState.Loaded) {
                    _uiState.value = prev.copy(isRefreshing = false)
                } else {
                    _uiState.value = FavoritesUiState.Error(errorMsg)
                }
                _events.emit(FavoritesEvent.ShowMessage("Refresh failed: $errorMsg"))
            }
        }
    }

    fun loadFavorites(reset: Boolean = false) {
        viewModelScope.launch {
            if (reset) {
                _uiState.value = FavoritesUiState.Loading
                nextCursor = null
            } else {
                val current = _uiState.value
                if (current is FavoritesUiState.Loaded) {
                    _uiState.value = current.copy(isLoadingMore = true)
                }
            }

            try {
                val viewerRole = authRepository.getCurrentUserRole() ?: "user"

                val result = favoritesRepository.fetchUserFavoritesPage(
                    limitCount = pageSize,
                    cursor = if (reset) null else nextCursor,
                    viewerRole = viewerRole
                )

                if (result.items.isEmpty() && reset) {
                    _uiState.value = FavoritesUiState.Empty
                    return@launch
                }

                nextCursor = result.nextCursor

                if (reset) {
                    _uiState.value = FavoritesUiState.Loaded(
                        properties = result.items,
                        hasMore = result.hasMore
                    )
                } else {
                    val current = _uiState.value
                    if (current is FavoritesUiState.Loaded) {
                        _uiState.value = current.copy(
                            properties = current.properties + result.items,
                            isLoadingMore = false,
                            hasMore = result.hasMore
                        )
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error loading favorites"
                _uiState.value = FavoritesUiState.Error(errorMsg)
                _events.emit(FavoritesEvent.ShowMessage("Error: $errorMsg"))
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value
        if (current is FavoritesUiState.Loaded && current.hasMore && !current.isLoadingMore) {
            loadFavorites(reset = false)
        }
    }

    fun retry() {
        loadFavorites(reset = true)
    }

    fun toggleFavorite(propertyId: String) {
        viewModelScope.launch {
            val isFav = favoriteIds.value.contains(propertyId)
            try {
                if (isFav) {
                    favoritesRepository.removeFromFavorites(propertyId)
                } else {
                    favoritesRepository.addToFavorites(propertyId)
                }
            } catch (e: Exception) {
                _events.emit(FavoritesEvent.ShowMessage("Failed to update favorites"))
            }
        }
    }

    /**
     * Toggle interested state for a property.
     * Matches web's Favorites.jsx handleToggleInterested exactly.
     */
    fun toggleInterested(propertyId: String) {
        val userId = authRepository.getCurrentUserId()
        if (userId == null) {
            viewModelScope.launch {
                _events.emit(FavoritesEvent.ShowMessage("Please log in to manage interested properties."))
            }
            return
        }

        viewModelScope.launch {
            _interestedLoadingMap.value = _interestedLoadingMap.value + (propertyId to true)

            try {
                val isNowInterested = interestedRepository.toggleInterestedProperty(userId, propertyId)

                // Optimistic count update (matches web's Favorites.jsx L145-149)
                val current = _uiState.value
                if (current is FavoritesUiState.Loaded) {
                    _uiState.value = current.copy(
                        properties = current.properties.map { property ->
                            if (property.id == propertyId) {
                                val currentCount = property.interestedCount
                                property.copy(
                                    interestedCount = if (isNowInterested) currentCount + 1
                                    else maxOf(currentCount - 1, 0)
                                )
                            } else property
                        }
                    )
                }
            } catch (e: Exception) {
                _events.emit(FavoritesEvent.ShowMessage(
                    e.message ?: "Error updating interested status. Please try again."
                ))
            } finally {
                _interestedLoadingMap.value = _interestedLoadingMap.value - propertyId
            }
        }
    }
}
