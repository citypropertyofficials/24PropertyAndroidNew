package com.example.myapplication.ui.screens.propertydetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.Property
import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.data.repository.FavoritesRepository
import com.example.myapplication.data.repository.InterestedRepository
import com.example.myapplication.data.repository.PropertyRepository
import com.example.myapplication.utils.FirebaseConstants
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ── UI State ──────────────────────────────────────────────────────────────────

sealed class PropertyDetailsUiState {
    data object Loading : PropertyDetailsUiState()
    data class Loaded(val property: Property) : PropertyDetailsUiState()
    data class Error(val message: String) : PropertyDetailsUiState()
}

// ── Events (one-shot side effects) ────────────────────────────────────────────

sealed class PropertyDetailsEvent {
    data class ShowMessage(val message: String) : PropertyDetailsEvent()
    /** Phone number resolved – trigger intent in UI */
    data class DialNumber(val phoneNumber: String) : PropertyDetailsEvent()
    /** Phone number for WhatsApp with pre-filled message */
    data class OpenWhatsApp(val phoneNumber: String, val message: String) : PropertyDetailsEvent()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class PropertyDetailsViewModel(
    private val propertyRepository: PropertyRepository,
    private val favoritesRepository: FavoritesRepository,
    private val authRepository: AuthRepository,
    private val interestedRepository: InterestedRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PropertyDetailsUiState>(PropertyDetailsUiState.Loading)
    val uiState: StateFlow<PropertyDetailsUiState> = _uiState.asStateFlow()

    val currentUserId: String?
        get() = authRepository.getCurrentUserId()

    private val _events = MutableSharedFlow<PropertyDetailsEvent>()
    val events: SharedFlow<PropertyDetailsEvent> = _events.asSharedFlow()

    // Live favorite IDs from Firestore snapshot listener
    val favoriteIds: StateFlow<Set<String>> = favoritesRepository.observeFavoriteIds()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    // Live interested IDs from Firestore snapshot listener
    val interestedIds: StateFlow<Set<String>> = interestedRepository.observeInterestedPropertyIds()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    // Loading state for interested toggle
    private val _isInterestedLoading = MutableStateFlow(false)
    val isInterestedLoading: StateFlow<Boolean> = _isInterestedLoading.asStateFlow()

    // Image carousel position — managed here so it survives recompositions
    private val _currentImageIndex = MutableStateFlow(0)
    val currentImageIndex: StateFlow<Int> = _currentImageIndex.asStateFlow()

    fun loadProperty(propertyId: String) {
        viewModelScope.launch {
            _uiState.value = PropertyDetailsUiState.Loading
            try {
                val viewerRole = authRepository.getCurrentUserRole() ?: FirebaseConstants.ROLE_USER
                val viewerUserId = authRepository.getCurrentUserId()
                val property = propertyRepository.fetchPropertyById(
                    propertyId = propertyId,
                    viewerRole = viewerRole,
                    viewerUserId = viewerUserId
                )
                if (property != null) {
                    _uiState.value = PropertyDetailsUiState.Loaded(property)
                    _currentImageIndex.value = 0
                } else {
                    _uiState.value = PropertyDetailsUiState.Error("Property not found or not accessible")
                }
            } catch (e: Exception) {
                _uiState.value = PropertyDetailsUiState.Error(
                    e.message ?: "Failed to load property details"
                )
            }
        }
    }

    fun nextImage() {
        val property = (uiState.value as? PropertyDetailsUiState.Loaded)?.property ?: return
        val total = property.images.size.coerceAtLeast(1)
        _currentImageIndex.value = (_currentImageIndex.value + 1).coerceAtMost(total - 1)
    }

    fun prevImage() {
        _currentImageIndex.value = (_currentImageIndex.value - 1).coerceAtLeast(0)
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
                _events.emit(PropertyDetailsEvent.ShowMessage("Failed to update favourites"))
            }
        }
    }

    /**
     * Toggle interested state for the current property.
     */
    fun toggleInterested(propertyId: String) {
        val userId = authRepository.getCurrentUserId()
        if (userId == null) {
            viewModelScope.launch {
                _events.emit(PropertyDetailsEvent.ShowMessage("Please log in to manage interested properties."))
            }
            return
        }

        viewModelScope.launch {
            _isInterestedLoading.value = true
            try {
                interestedRepository.toggleInterestedProperty(userId, propertyId)
            } catch (e: Exception) {
                _events.emit(PropertyDetailsEvent.ShowMessage(
                    e.message ?: "Error updating interested status. Please try again."
                ))
            } finally {
                _isInterestedLoading.value = false
            }
        }
    }

    /** Fetches owner mobile and emits a DialNumber event */
    fun callOwner() {
        val property = (uiState.value as? PropertyDetailsUiState.Loaded)?.property ?: return
        viewModelScope.launch {
            try {
                val mobile = if (property.ownerMobile.isNotBlank()) {
                    property.ownerMobile
                } else {
                    propertyRepository.getOwnerMobileNumber(property.owner)
                }
                if (!mobile.isNullOrBlank()) {
                    _events.emit(PropertyDetailsEvent.DialNumber(mobile))
                } else {
                    _events.emit(PropertyDetailsEvent.ShowMessage("Owner contact not available"))
                }
            } catch (e: Exception) {
                _events.emit(PropertyDetailsEvent.ShowMessage("Unable to fetch owner contact"))
            }
        }
    }

    /** Fetches owner mobile and emits an OpenWhatsApp event */
    fun whatsappOwner() {
        val property = (uiState.value as? PropertyDetailsUiState.Loaded)?.property ?: return
        viewModelScope.launch {
            try {
                val mobile = if (property.ownerMobile.isNotBlank()) {
                    property.ownerMobile
                } else {
                    propertyRepository.getOwnerMobileNumber(property.owner)
                }
                if (!mobile.isNullOrBlank()) {
                    val msg = buildShareMessage(property)
                    _events.emit(PropertyDetailsEvent.OpenWhatsApp(mobile, msg))
                } else {
                    _events.emit(PropertyDetailsEvent.ShowMessage("Owner contact not available"))
                }
            } catch (e: Exception) {
                _events.emit(PropertyDetailsEvent.ShowMessage("Unable to fetch owner contact"))
            }
        }
    }

    private fun buildShareMessage(property: Property): String {
        val isBrokerProperty = property.ownerRole.lowercase() == "broker"
        return buildString {
            append("🏠 *${property.name}*\n")
            if (!isBrokerProperty && property.displayLocation.isNotBlank()) append("📍 ${property.displayLocation}\n")
            append("💰 ${property.displayPrice}\n")
            if (property.propertyType.isNotBlank()) append("🏢 ${property.propertyTypeDisplay}\n")
            append("\nShared via 24Property App")
        }
    }
}
