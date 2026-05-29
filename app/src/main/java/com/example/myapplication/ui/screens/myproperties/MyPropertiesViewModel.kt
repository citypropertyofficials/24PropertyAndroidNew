package com.example.myapplication.ui.screens.myproperties

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.R
import com.example.myapplication.data.model.InterestedUser
import com.example.myapplication.data.model.Property
import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.data.repository.AuthResult
import com.example.myapplication.data.repository.InterestedRepository
import com.example.myapplication.data.repository.PropertyRepository
import com.example.myapplication.utils.FirebaseConstants
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MyPropertiesFilter(val value: String) {
    ALL("all"),
    RENT("rent"),
    SALE("sale"),
    DRAFT(FirebaseConstants.PROPERTY_STATUS_DRAFT)
}

data class MyPropertiesContentState(
    val properties: List<Property> = emptyList(),
    val currentFilter: MyPropertiesFilter = MyPropertiesFilter.ALL,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val deletingPropertyId: String? = null,
    val totalPropertyCount: Int = 0,
    val maxPropertiesAllowed: Int? = null
)

sealed class MyPropertiesUiState {
    data class Loading(val currentFilter: MyPropertiesFilter = MyPropertiesFilter.ALL) : MyPropertiesUiState()
    data class Content(val state: MyPropertiesContentState) : MyPropertiesUiState()
    data class Error(
        val messageResId: Int,
        val currentFilter: MyPropertiesFilter = MyPropertiesFilter.ALL
    ) : MyPropertiesUiState()
}

sealed class MyPropertiesEvent {
    data class ShowMessage(val messageResId: Int) : MyPropertiesEvent()
}

class MyPropertiesViewModel(
    private val authRepository: AuthRepository,
    private val propertyRepository: PropertyRepository,
    private val interestedRepository: InterestedRepository
) : ViewModel() {

    private companion object {
        const val TAG = "MyPropertiesVM"
    }

    private val _uiState = MutableStateFlow<MyPropertiesUiState>(MyPropertiesUiState.Loading())
    val uiState: StateFlow<MyPropertiesUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MyPropertiesEvent>()
    val events: SharedFlow<MyPropertiesEvent> = _events.asSharedFlow()

    private var currentFilter = MyPropertiesFilter.ALL
    private var nextCursor: DocumentSnapshot? = null
    private val pageSize = 8

    // Interested users state (for owner bottom sheet)
    private val _interestedUsers = MutableStateFlow<List<InterestedUser>>(emptyList())
    val interestedUsers: StateFlow<List<InterestedUser>> = _interestedUsers.asStateFlow()

    private val _isLoadingInterestedUsers = MutableStateFlow(false)
    val isLoadingInterestedUsers: StateFlow<Boolean> = _isLoadingInterestedUsers.asStateFlow()

    private val _interestedUsersError = MutableStateFlow<String?>(null)
    val interestedUsersError: StateFlow<String?> = _interestedUsersError.asStateFlow()

    init {
        loadProperties(reset = true)
    }

    fun updateFilter(filter: MyPropertiesFilter) {
        if (currentFilter == filter) return
        currentFilter = filter
        loadProperties(reset = true)
    }

    fun refresh() {
        val currentState = _uiState.value as? MyPropertiesUiState.Content
        if (currentState?.state?.isRefreshing == true) return

        if (currentState != null) {
            _uiState.value = MyPropertiesUiState.Content(
                currentState.state.copy(isRefreshing = true)
            )
        } else {
            _uiState.value = MyPropertiesUiState.Loading(currentFilter)
        }

        loadProperties(reset = true, keepRefreshState = true)
    }

    fun retry() {
        loadProperties(reset = true)
    }

    fun loadMore() {
        val currentState = _uiState.value as? MyPropertiesUiState.Content ?: return
        if (!currentState.state.hasMore || currentState.state.isLoadingMore) return

        _uiState.value = MyPropertiesUiState.Content(
            currentState.state.copy(isLoadingMore = true)
        )
        loadProperties(reset = false)
    }

    fun deleteProperty(propertyId: String) {
        val currentState = _uiState.value as? MyPropertiesUiState.Content ?: return
        if (currentState.state.deletingPropertyId != null) return

        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId()
            if (userId.isNullOrBlank()) {
                _events.emit(MyPropertiesEvent.ShowMessage(R.string.error_delete_property_requires_login))
                return@launch
            }

            _uiState.value = MyPropertiesUiState.Content(
                currentState.state.copy(deletingPropertyId = propertyId)
            )

            try {
                propertyRepository.deleteProperty(propertyId, userId)
                _events.emit(MyPropertiesEvent.ShowMessage(R.string.property_deleted_success))
                loadProperties(reset = true)
            } catch (e: Exception) {
                _uiState.value = MyPropertiesUiState.Content(
                    currentState.state.copy(deletingPropertyId = null)
                )
                _events.emit(MyPropertiesEvent.ShowMessage(R.string.error_delete_property))
            }
        }
    }

    private fun loadProperties(
        reset: Boolean,
        keepRefreshState: Boolean = false
    ) {
        viewModelScope.launch {
            val existingContent = _uiState.value as? MyPropertiesUiState.Content

            if (reset && !keepRefreshState) {
                _uiState.value = MyPropertiesUiState.Loading(currentFilter)
                nextCursor = null
            }

            try {
                val userId = authRepository.getCurrentUserId()
                    ?: throw IllegalStateException()
                val viewerRole = authRepository.getCurrentUserRole() ?: "user"

                // Fetch user data for property limit
                val userData = when (val result = authRepository.getUserData(userId)) {
                    is AuthResult.Success -> result.data
                    else -> null
                }
                val maxAllowed = userData?.maxPropertiesAllowed

                Log.d(
                    TAG,
                    "loadProperties: reset=$reset keepRefreshState=$keepRefreshState filter=${currentFilter.value} userId=$userId viewerRole=$viewerRole cursor=${if (reset) null else nextCursor?.id} maxAllowed=$maxAllowed"
                )

                val result = propertyRepository.fetchUserPropertiesPage(
                    userId = userId,
                    limitCount = pageSize,
                    cursor = if (reset) null else nextCursor,
                    listingType = currentFilter.value,
                    viewerRole = viewerRole
                )

                nextCursor = result.nextCursor

                Log.d(
                    TAG,
                    "loadProperties result: items=${result.items.size} hasMore=${result.hasMore} nextCursor=${result.nextCursor?.id}"
                )
                result.items.forEach { property ->
                    Log.d(
                        TAG,
                        "resultProperty id=${property.id} owner=${property.owner} ownerRole=${property.ownerRole} status=${property.status} listingType=${property.listingType}"
                    )
                }

                val updatedProperties = if (reset) {
                    result.items
                } else {
                    val currentProperties = existingContent?.state?.properties.orEmpty()
                    currentProperties + result.items
                }

                // Count all active properties (drafts + published) for limit display
                val totalCount = if (reset) {
                    propertyRepository.getUserActivePropertyCount(userId)
                } else {
                    existingContent?.state?.totalPropertyCount ?: 0
                }

                _uiState.value = MyPropertiesUiState.Content(
                    MyPropertiesContentState(
                        properties = updatedProperties,
                        currentFilter = currentFilter,
                        hasMore = result.hasMore,
                        isLoadingMore = false,
                        isRefreshing = false,
                        deletingPropertyId = null,
                        totalPropertyCount = totalCount,
                        maxPropertiesAllowed = maxAllowed
                    )
                )

                Log.d(
                    TAG,
                    "uiState updated: totalProperties=${updatedProperties.size} totalCount=$totalCount maxAllowed=$maxAllowed currentFilter=${currentFilter.value}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "loadProperties failed", e)
                if (existingContent != null) {
                    _uiState.value = MyPropertiesUiState.Content(
                        existingContent.state.copy(
                            isLoadingMore = false,
                            isRefreshing = false,
                            deletingPropertyId = null
                        )
                    )
                    _events.emit(MyPropertiesEvent.ShowMessage(R.string.error_loading_my_properties))
                } else {
                    _uiState.value = MyPropertiesUiState.Error(
                        messageResId = R.string.error_loading_my_properties,
                        currentFilter = currentFilter
                    )
                }
            }
        }
    }

    /**
     * Fetch interested users for a given property (owner view).
     * Matches web's getInterestedUsersForProperty().
     */
    fun loadInterestedUsers(propertyId: String) {
        viewModelScope.launch {
            _isLoadingInterestedUsers.value = true
            _interestedUsersError.value = null
            _interestedUsers.value = emptyList()

            try {
                _interestedUsers.value = interestedRepository.getInterestedUsersForProperty(propertyId)
            } catch (e: Exception) {
                _interestedUsersError.value = e.message ?: "Failed to load interested users"
            } finally {
                _isLoadingInterestedUsers.value = false
            }
        }
    }
}
