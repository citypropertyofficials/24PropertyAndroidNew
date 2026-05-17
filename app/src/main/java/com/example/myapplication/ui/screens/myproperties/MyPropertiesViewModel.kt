package com.example.myapplication.ui.screens.myproperties

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.R
import com.example.myapplication.data.model.Property
import com.example.myapplication.data.repository.AuthRepository
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
    val deletingPropertyId: String? = null
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
    private val propertyRepository: PropertyRepository
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

                Log.d(
                    TAG,
                    "loadProperties: reset=$reset keepRefreshState=$keepRefreshState filter=${currentFilter.value} userId=$userId viewerRole=$viewerRole cursor=${if (reset) null else nextCursor?.id}"
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

                _uiState.value = MyPropertiesUiState.Content(
                    MyPropertiesContentState(
                        properties = updatedProperties,
                        currentFilter = currentFilter,
                        hasMore = result.hasMore,
                        isLoadingMore = false,
                        isRefreshing = false,
                        deletingPropertyId = null
                    )
                )

                Log.d(
                    TAG,
                    "uiState updated: totalProperties=${updatedProperties.size} currentFilter=${currentFilter.value}"
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
}
