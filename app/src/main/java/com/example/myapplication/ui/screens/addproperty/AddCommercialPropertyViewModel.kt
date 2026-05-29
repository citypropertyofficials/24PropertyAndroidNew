package com.example.myapplication.ui.screens.addproperty

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.AddPropertyRepository
import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.data.repository.AuthResult
import com.example.myapplication.utils.FirebaseConstants
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AddCommercialPropertyViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val addPropertyRepository: AddPropertyRepository
) : ViewModel() {

    private val propertyId: String? = savedStateHandle["propertyId"]

    private val _uiState = MutableStateFlow(
        AddCommercialPropertyUiState(
            propertyId = propertyId,
            isEditMode = !propertyId.isNullOrBlank(),
            isInitialLoading = !propertyId.isNullOrBlank()
        )
    )
    val uiState: StateFlow<AddCommercialPropertyUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AddCommercialPropertyEvent>()
    val events: SharedFlow<AddCommercialPropertyEvent> = _events.asSharedFlow()

    init {
        if (!propertyId.isNullOrBlank()) {
            loadProperty(propertyId)
        }
    }

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

    fun removeExistingImage(imageUrl: String) {
        _uiState.value = _uiState.value.copy(
            existingImageUrls = _uiState.value.existingImageUrls.filterNot { it == imageUrl }
        )
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
        if (currentState.isSavingDraft || currentState.isPublishing || currentState.isInitialLoading) return

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

                if (currentState.propertyId == null && userData.maxPropertiesAllowed != null) {
                    val liveCount = addPropertyRepository.getUserPropertyCount(userId)
                    if (liveCount >= userData.maxPropertiesAllowed) {
                        error("You have reached your property limit (${liveCount}/${userData.maxPropertiesAllowed}).")
                    }
                }

                val draftPayload = buildCommercialPropertyPayload(
                    state = currentState,
                    userId = userId,
                    ownerName = userData.name.ifBlank { "Property Owner" },
                    ownerEmail = userData.email,
                    ownerPhoto = userData.photoUrl,
                    ownerRole = ownerRole,
                    isDraft = isDraft,
                    includeCreatedAt = true,
                    imageUrls = emptyList()
                )
                val resolvedPropertyId = currentState.propertyId ?: addPropertyRepository.createProperty(draftPayload)

                val removedImages = currentState.originalImageUrls.filterNot { imageUrl ->
                    currentState.existingImageUrls.contains(imageUrl)
                }
                if (removedImages.isNotEmpty()) {
                    addPropertyRepository.deletePropertyImages(removedImages)
                }

                val uploadedImageUrls = if (currentState.imageUris.isNotEmpty()) {
                    addPropertyRepository.uploadPropertyImages(
                        propertyId = resolvedPropertyId,
                        imageUris = currentState.imageUris,
                        contentResolver = contentResolver
                    )
                } else {
                    emptyList()
                }

                val payload = buildCommercialPropertyPayload(
                    state = currentState,
                    userId = userId,
                    ownerName = userData.name.ifBlank { "Property Owner" },
                    ownerEmail = userData.email,
                    ownerPhoto = userData.photoUrl,
                    ownerRole = ownerRole,
                    isDraft = isDraft,
                    includeCreatedAt = false,
                    imageUrls = buildImageList(currentState.existingImageUrls, uploadedImageUrls)
                )
                addPropertyRepository.updateProperty(resolvedPropertyId, payload)

                _events.emit(
                    AddCommercialPropertyEvent.ShowMessage(
                        when {
                            currentState.isEditMode && isDraft -> "Commercial property saved as draft."
                            currentState.isEditMode -> "Commercial property updated successfully."
                            isDraft -> "Commercial property saved as draft."
                            else -> "Commercial property added successfully."
                        }
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

    private fun loadProperty(propertyId: String) {
        viewModelScope.launch {
            try {
                val property = addPropertyRepository.getProperty(propertyId)
                    ?: error("Property not found.")
                val images = property[FirebaseConstants.FIELD_IMAGES].asStringList()
                val fieldValues = property.baseFieldValues(defaultCommercialFieldValues()).apply {
                    if (this["liftType"].isNullOrBlank()) {
                        this["liftType"] = resolveCommercialLiftType(
                            passengerLift = property[FirebaseConstants.FIELD_PASSENGER_LIFT].asString(),
                            serviceLift = property[FirebaseConstants.FIELD_SERVICE_LIFT].asString()
                        )
                    }
                }
                _uiState.value = AddCommercialPropertyUiState(
                    listingType = property[FirebaseConstants.FIELD_LISTING_TYPE].asString().ifBlank { LISTING_TYPE_RENT },
                    fieldValues = fieldValues,
                    amenities = property[FirebaseConstants.FIELD_AMENITIES].asStringList().toSet(),
                    propertyId = propertyId,
                    isEditMode = true,
                    isInitialLoading = false,
                    originalImageUrls = images,
                    existingImageUrls = images,
                    selectedLocation = property.selectedLocationOrNull(),
                    mapsApiConfigured = _uiState.value.mapsApiConfigured
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(isInitialLoading = false)
                _events.emit(AddCommercialPropertyEvent.ShowMessage(t.message ?: "Unable to load property."))
            }
        }
    }
}
