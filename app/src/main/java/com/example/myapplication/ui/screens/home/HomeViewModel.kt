package com.example.myapplication.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.data.repository.AuthResult
import com.example.myapplication.data.repository.FavoritesRepository
import com.example.myapplication.data.repository.InterestedRepository
import com.example.myapplication.data.repository.PropertyRepository
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class HomeEvent {
    data class ShowMessage(val message: String) : HomeEvent()
}

class HomeViewModel(
    private val authRepository: AuthRepository,
    private val propertyRepository: PropertyRepository,
    private val favoritesRepository: FavoritesRepository,
    private val interestedRepository: InterestedRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>()
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    val favoriteIds: StateFlow<Set<String>> = favoritesRepository.observeFavoriteIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val interestedIds: StateFlow<Set<String>> = interestedRepository.observeInterestedPropertyIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _interestedLoadingMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val interestedLoadingMap: StateFlow<Map<String, Boolean>> = _interestedLoadingMap.asStateFlow()

    private var nextCursor: DocumentSnapshot? = null
    private val pageSize = 8

    val currentUserId: String? get() = authRepository.getCurrentUserId()

    init {
        viewModelScope.launch {
            authRepository.observeAuthState()
                .distinctUntilChanged()
                .collect { isSignedIn ->
                    if (isSignedIn) {
                        hydrateLocationRestriction()
                        loadProperties(reset = true)
                    } else {
                        nextCursor = null
                        _uiState.value = _uiState.value.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            properties = emptyList(),
                            hasMore = false
                        )
                    }
                }
        }
    }

    private suspend fun hydrateLocationRestriction() {
        val userId = authRepository.getCurrentUserId() ?: return
        val user = when (val result = authRepository.getUserData(userId)) {
            is AuthResult.Success -> result.data
            else -> null
        } ?: return

        _uiState.value = _uiState.value.copy(
            locationRestriction = buildLocationRestriction(user.locationPreference)
        )
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
        loadProperties(reset = true)
    }

    fun retry() {
        loadProperties(reset = true)
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMore || state.isLoadingMore || state.isInitialLoading) return
        loadProperties(reset = false)
    }

    fun updateListingType(listingType: String) {
        if (_uiState.value.selectedListingType == listingType) return
        _uiState.value = _uiState.value.copy(
            selectedListingType = listingType,
            appliedFilters = HomeFilters()
        )
        loadProperties(reset = true)
    }

    fun updatePropertyType(propertyType: String) {
        if (_uiState.value.selectedPropertyType == propertyType) return
        _uiState.value = _uiState.value.copy(
            selectedPropertyType = propertyType,
            appliedFilters = HomeFilters()
        )
        loadProperties(reset = true)
    }

    fun applyFilters(filters: Map<String, Any>, searchAreas: List<HomeSearchArea>, locationRadiusKm: Int) {
        _uiState.value = _uiState.value.copy(
            appliedFilters = HomeFilters(filters),
            appliedSearchAreas = searchAreas,
            locationRadiusKm = locationRadiusKm
        )
        loadProperties(reset = true)
    }

    fun applyFiltersWithPropertyType(
        propertyType: String,
        filters: Map<String, Any>,
        searchAreas: List<HomeSearchArea>,
        locationRadiusKm: Int
    ) {
        _uiState.value = _uiState.value.copy(
            selectedPropertyType = propertyType,
            appliedFilters = HomeFilters(filters),
            appliedSearchAreas = searchAreas,
            locationRadiusKm = locationRadiusKm
        )
        loadProperties(reset = true)
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(appliedFilters = HomeFilters())
        loadProperties(reset = true)
    }

    private fun loadProperties(reset: Boolean) {
        viewModelScope.launch {
            val current = _uiState.value
            if (reset) {
                nextCursor = null
                _uiState.value = current.copy(
                    isInitialLoading = current.properties.isEmpty(),
                    isRefreshing = current.properties.isNotEmpty() && current.isRefreshing,
                    isLoadingMore = false,
                    errorMessage = null,
                    properties = if (current.properties.isEmpty()) emptyList() else current.properties
                )
            } else {
                _uiState.value = current.copy(isLoadingMore = true, errorMessage = null)
            }

            try {
                val viewerRole = authRepository.getCurrentUserRole() ?: "user"
                val state = _uiState.value
                val result = propertyRepository.fetchPropertiesPage(
                    limitCount = pageSize,
                    cursor = if (reset) null else nextCursor,
                    viewerRole = viewerRole,
                    propertyType = state.selectedPropertyType,
                    listingType = state.selectedListingType,
                    geoFilter = buildGeoFilter(state.appliedSearchAreas, state.locationRadiusKm),
                    filters = state.appliedFilters.values,
                    locationRestriction = state.locationRestriction
                )

                nextCursor = result.nextCursor
                val merged = if (reset) result.items else _uiState.value.properties + result.items
                _uiState.value = _uiState.value.copy(
                    isInitialLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    errorMessage = null,
                    properties = merged,
                    hasMore = result.hasMore
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isInitialLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    errorMessage = e.message ?: "Failed to load properties"
                )
                _events.emit(HomeEvent.ShowMessage(e.message ?: "Failed to load properties"))
            }
        }
    }

    fun toggleFavorite(propertyId: String) {
        viewModelScope.launch {
            try {
                if (favoriteIds.value.contains(propertyId)) {
                    favoritesRepository.removeFromFavorites(propertyId)
                } else {
                    favoritesRepository.addToFavorites(propertyId)
                }
            } catch (_: Exception) {
                _events.emit(HomeEvent.ShowMessage("Failed to update favorites"))
            }
        }
    }

    fun toggleInterested(propertyId: String) {
        val userId = authRepository.getCurrentUserId()
        if (userId == null) {
            viewModelScope.launch {
                _events.emit(HomeEvent.ShowMessage("Please log in to manage interested properties."))
            }
            return
        }

        viewModelScope.launch {
            _interestedLoadingMap.value = _interestedLoadingMap.value + (propertyId to true)
            try {
                val isNowInterested = interestedRepository.toggleInterestedProperty(userId, propertyId)
                _uiState.value = _uiState.value.copy(
                    properties = _uiState.value.properties.map { property ->
                        if (property.id == propertyId) {
                            val currentCount = property.interestedCount
                            property.copy(
                                interestedCount = if (isNowInterested) currentCount + 1 else maxOf(currentCount - 1, 0)
                            )
                        } else {
                            property
                        }
                    }
                )
            } catch (e: Exception) {
                _events.emit(
                    HomeEvent.ShowMessage(
                        e.message ?: "Error updating interested status. Please try again."
                    )
                )
            } finally {
                _interestedLoadingMap.value = _interestedLoadingMap.value - propertyId
            }
        }
    }
}
