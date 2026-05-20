package com.example.myapplication.ui.screens.addproperty

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.AddPropertyRepository
import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.data.repository.AuthResult
import com.example.myapplication.utils.FirebaseConstants
import com.example.myapplication.utils.encodeGeohash
import com.example.myapplication.utils.resolveStateCode
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AddCommercialPropertyViewModel(
    private val authRepository: AuthRepository,
    private val addPropertyRepository: AddPropertyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddCommercialPropertyUiState())
    val uiState: StateFlow<AddCommercialPropertyUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AddCommercialPropertyEvent>()
    val events: SharedFlow<AddCommercialPropertyEvent> = _events.asSharedFlow()

    fun updateListingType(value: String) {
        _uiState.value = _uiState.value.copy(listingType = value, fieldErrors = emptyMap())
    }

    fun updateField(fieldId: String, value: String) {
        val nextValues = _uiState.value.fieldValues.toMutableMap()
        nextValues[fieldId] = value
        val nextErrors = _uiState.value.fieldErrors - fieldId
        _uiState.value = _uiState.value.copy(fieldValues = nextValues, fieldErrors = nextErrors)
    }

    fun toggleAmenity(amenity: String, selected: Boolean) {
        val next = _uiState.value.amenities.toMutableSet()
        if (selected) next.add(amenity) else next.remove(amenity)
        _uiState.value = _uiState.value.copy(amenities = next)
    }

    fun setImages(images: List<Uri>) {
        val deduped = (_uiState.value.imageUris + images).distinct().take(MAX_PROPERTY_IMAGES)
        _uiState.value = _uiState.value.copy(imageUris = deduped)
        if (deduped.size < _uiState.value.imageUris.size + images.size) {
            viewModelScope.launch {
                _events.emit(AddCommercialPropertyEvent.ShowMessage("Maximum $MAX_PROPERTY_IMAGES images allowed."))
            }
        }
    }

    fun removeImage(uri: Uri) {
        _uiState.value = _uiState.value.copy(imageUris = _uiState.value.imageUris.filterNot { it == uri })
    }

    fun updateLocation(location: SelectedLocation, suggestedFields: Map<String, String> = emptyMap()) {
        val nextValues = _uiState.value.fieldValues.toMutableMap()
        suggestedFields.forEach { (key, value) ->
            if (value.isNotBlank()) nextValues[key] = value
        }
        _uiState.value = _uiState.value.copy(
            selectedLocation = location,
            fieldValues = nextValues,
            fieldErrors = _uiState.value.fieldErrors - "mapLocation"
        )
    }

    fun saveDraft(contentResolver: ContentResolver) {
        submit(contentResolver, isDraft = true)
    }

    fun publish(contentResolver: ContentResolver) {
        submit(contentResolver, isDraft = false)
    }

    private fun submit(contentResolver: ContentResolver, isDraft: Boolean) {
        val currentState = _uiState.value
        if (currentState.isSavingDraft || currentState.isPublishing) return

        val errors = if (isDraft) emptyMap() else validate(currentState)
        if (errors.isNotEmpty()) {
            _uiState.value = currentState.copy(fieldErrors = errors)
            viewModelScope.launch {
                _events.emit(AddCommercialPropertyEvent.ShowMessage("Complete the required commercial property fields."))
            }
            return
        }

        _uiState.value = currentState.copy(
            isSavingDraft = isDraft,
            isPublishing = !isDraft,
            fieldErrors = errors
        )

        viewModelScope.launch {
            try {
                val userId = authRepository.getCurrentUserId()
                    ?: error("Please sign in again to add a property.")
                val userData = when (val result = authRepository.getUserData(userId)) {
                    is AuthResult.Success -> result.data
                    is AuthResult.Error -> error(result.message)
                    AuthResult.Loading -> error("Unable to load your user profile.")
                }
                val ownerRole = authRepository.getCurrentUserRole() ?: FirebaseConstants.ROLE_USER

                if (!isDraft && userData.maxPropertiesAllowed != null) {
                    val liveCount = addPropertyRepository.getUserPropertyCount(userId)
                    if (liveCount >= userData.maxPropertiesAllowed) {
                        error("You have reached your property limit (${liveCount}/${userData.maxPropertiesAllowed}).")
                    }
                }

                val payload = buildPropertyPayload(
                    state = _uiState.value,
                    userId = userId,
                    ownerName = userData.name.ifBlank { "Property Owner" },
                    ownerEmail = userData.email,
                    ownerPhoto = userData.photoUrl,
                    ownerRole = ownerRole,
                    isDraft = isDraft
                )
                val propertyId = addPropertyRepository.createProperty(payload)
                if (_uiState.value.imageUris.isNotEmpty()) {
                    val imageUrls = addPropertyRepository.uploadPropertyImages(
                        propertyId = propertyId,
                        imageUris = _uiState.value.imageUris,
                        contentResolver = contentResolver
                    )
                    addPropertyRepository.updateProperty(
                        propertyId,
                        mapOf(FirebaseConstants.FIELD_IMAGES to imageUrls)
                    )
                }

                _events.emit(
                    AddCommercialPropertyEvent.ShowMessage(
                        if (isDraft) "Commercial property saved as draft." else "Commercial property added successfully."
                    )
                )
                _events.emit(AddCommercialPropertyEvent.PropertySaved)
                _uiState.value = AddCommercialPropertyUiState()
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(isSavingDraft = false, isPublishing = false)
                _events.emit(AddCommercialPropertyEvent.ShowMessage(t.message ?: "Unable to save property."))
            }
        }
    }

    private fun validate(state: AddCommercialPropertyUiState): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        fun requireValue(key: String) {
            if (state.fieldValues[key].isNullOrBlank()) errors[key] = "Required"
        }

        requireValue("propertyPrice")
        requireValue("buildingName")
        requireValue("state")
        requireValue("city")
        requireValue("nameStreetArea")
        requireValue("landmark")
        if (state.selectedLocation == null) errors["mapLocation"] = "Select on map"

        commercialSections.flatMap { it.fields }.forEach { field ->
            if (field.required && isFieldVisible(field, state.listingType, state.fieldValues)) {
                if (state.fieldValues[field.id].isNullOrBlank()) errors[field.id] = "Required"
            }
        }
        return errors
    }

    private fun buildPropertyPayload(
        state: AddCommercialPropertyUiState,
        userId: String,
        ownerName: String,
        ownerEmail: String,
        ownerPhoto: String,
        ownerRole: String,
        isDraft: Boolean
    ): Map<String, Any?> {
        val values = state.fieldValues
        val buildingName = values["buildingName"].orEmpty().trim()
        val structuredAddress = listOf(
            buildingName,
            values["nameStreetArea"].orEmpty().trim(),
            values["landmark"].orEmpty().trim(),
            values["city"].orEmpty().trim(),
            values["state"].orEmpty().trim()
        ).filter { it.isNotBlank() }.joinToString(", ")
        val location = state.selectedLocation
        val geo = location?.let {
            mapOf(
                "lat" to it.lat,
                "lng" to it.lng,
                "placeId" to it.placeId,
                "formattedAddress" to it.formattedAddress
            )
        }

        val dynamicValues = values
            .filterKeys { key ->
                key !in setOf("propertyPrice", "buildingName", "description", "state", "city", "nameStreetArea", "landmark")
            }
            .filterValues { it.isNotBlank() }
            .toMutableMap<String, Any>()

        // Normalize possessionStatus if present
        values["possessionStatus"]?.let {
            if (it.isNotBlank()) {
                dynamicValues[FirebaseConstants.FIELD_POSSESSION_STATUS] = normalizePossessionStatus(it)
            }
        }

        return mapOf(
            FirebaseConstants.FIELD_NAME to if (buildingName.isBlank()) "Untitled Commercial Property" else buildingName,
            FirebaseConstants.FIELD_DESCRIPTION to values["description"].orEmpty().trim(),
            FirebaseConstants.FIELD_PRICE to values["propertyPrice"].orEmpty().toLongOrNull(),
            FirebaseConstants.FIELD_PROPERTY_TYPE to COMMERCIAL_PROPERTY_TYPE,
            FirebaseConstants.FIELD_LISTING_TYPE to state.listingType,
            FirebaseConstants.FIELD_STATUS to if (isDraft) FirebaseConstants.PROPERTY_STATUS_DRAFT else FirebaseConstants.PROPERTY_STATUS_PUBLISHED,
            FirebaseConstants.FIELD_BUILDING_NAME to buildingName,
            FirebaseConstants.FIELD_ADDRESS to structuredAddress,
            FirebaseConstants.FIELD_STATE to values["state"].orEmpty().trim(),
            FirebaseConstants.FIELD_STATE_CODE to resolveStateCode(values["state"].orEmpty()),
            FirebaseConstants.FIELD_CITY to values["city"].orEmpty().trim(),
            FirebaseConstants.FIELD_NAME_STREET_AREA to values["nameStreetArea"].orEmpty().trim(),
            FirebaseConstants.FIELD_LANDMARK to values["landmark"].orEmpty().trim(),
            FirebaseConstants.FIELD_GEO to geo,
            FirebaseConstants.FIELD_GEOHASH to encodeGeohash(location?.lat, location?.lng),
            FirebaseConstants.FIELD_AMENITIES to state.amenities.toList(),
            FirebaseConstants.FIELD_USER_ID to userId,
            FirebaseConstants.FIELD_OWNER to userId,
            FirebaseConstants.FIELD_OWNER_NAME to ownerName,
            FirebaseConstants.FIELD_OWNER_EMAIL to ownerEmail,
            FirebaseConstants.FIELD_OWNER_PHOTO to ownerPhoto,
            FirebaseConstants.FIELD_OWNER_ROLE to ownerRole,
            FirebaseConstants.FIELD_IS_ACTIVE to true,
            FirebaseConstants.FIELD_CREATED_AT to FieldValue.serverTimestamp(),
            FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
            FirebaseConstants.FIELD_IMAGES to emptyList<String>()
        ) + dynamicValues
    }

    private fun normalizePossessionStatus(value: String): String {
        return when (value.trim().lowercase()) {
            "ready to move", "ready possession" -> "ready_possession"
            "under construction" -> "under_construction"
            else -> value
        }
    }
}
