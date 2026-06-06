package com.example.myapplication.ui.screens.addproperty

import com.example.myapplication.utils.FirebaseConstants
import com.example.myapplication.utils.encodeGeohash
import com.example.myapplication.utils.resolveStateCode
import com.google.firebase.firestore.FieldValue

internal fun Any?.asString(): String = when (this) {
    null -> ""
    is String -> this
    is Number -> toString()
    is Boolean -> if (this) "Yes" else "No"
    else -> toString()
}

internal fun Any?.asStringList(): List<String> = (this as? List<*>)?.mapNotNull {
    (it as? String)?.takeIf(String::isNotBlank)
} ?: emptyList()

internal fun Map<String, Any?>.selectedLocationOrNull(): SelectedLocation? {
    val geo = this[FirebaseConstants.FIELD_GEO] as? Map<*, *> ?: return null
    val lat = (geo["lat"] as? Number)?.toDouble() ?: return null
    val lng = (geo["lng"] as? Number)?.toDouble() ?: return null
    return SelectedLocation(
        lat = lat,
        lng = lng,
        placeId = geo["placeId"] as? String,
        formattedAddress = geo["formattedAddress"] as? String
    )
}

internal fun normalizePossessionStatusForForm(value: String): String {
    return when (value.trim().lowercase()) {
        "ready to move", "ready possession" -> "ready_possession"
        "under construction" -> "under_construction"
        else -> value
    }
}

internal fun resolveCommercialLiftType(passengerLift: String, serviceLift: String): String {
    return when {
        serviceLift.trim().equals("Yes", ignoreCase = true) -> "Service Lift"
        passengerLift.trim().equals("Yes", ignoreCase = true) -> "Passenger Lift"
        else -> "None"
    }
}

internal fun Map<String, Any?>.baseFieldValues(defaults: Map<String, String>): MutableMap<String, String> {
    val values = defaults.toMutableMap()
    defaults.keys.forEach { key ->
        values[key] = this[key].asString()
    }
    values["description"] = this[FirebaseConstants.FIELD_DESCRIPTION].asString()
    values["state"] = this[FirebaseConstants.FIELD_STATE].asString()
    values["city"] = this[FirebaseConstants.FIELD_CITY].asString()
    values["nameStreetArea"] = this[FirebaseConstants.FIELD_NAME_STREET_AREA].asString()
    values["landmark"] = this[FirebaseConstants.FIELD_LANDMARK].asString()
    values["propertyPrice"] = this[FirebaseConstants.FIELD_PRICE].asString()
    values["buildingName"] = this[FirebaseConstants.FIELD_BUILDING_NAME].asString()
    values["propertyAddress"] = this[FirebaseConstants.FIELD_BUILDING_NAME].asString()
    values["propertyName"] = this[FirebaseConstants.FIELD_NAME].asString()
    values[FirebaseConstants.FIELD_POSSESSION_STATUS] = normalizePossessionStatusForForm(
        this[FirebaseConstants.FIELD_POSSESSION_STATUS].asString()
    )

    // Backward-compatible split showing fields prefill
    val showingDate = this[FirebaseConstants.FIELD_SHOWING_DATE].asString()
    val showingStartTime = this[FirebaseConstants.FIELD_SHOWING_START_TIME].asString()
    val showingEndTime = this[FirebaseConstants.FIELD_SHOWING_END_TIME].asString()
    val legacyDateTime = this[FirebaseConstants.FIELD_SHOWING_DATE_TIME].asString()
    if (showingDate.isNotBlank()) {
        values["showingDate"] = showingDate
    } else if (legacyDateTime.isNotBlank()) {
        val split = splitLegacyShowingDateTime(legacyDateTime)
        if (split.showingDate.isNotBlank()) values["showingDate"] = split.showingDate
        if (split.showingStartTime.isNotBlank()) values["showingStartTime"] = split.showingStartTime
    }
    if (showingStartTime.isNotBlank()) values["showingStartTime"] = showingStartTime
    else if (legacyDateTime.isNotBlank()) {
        val split = splitLegacyShowingDateTime(legacyDateTime)
        if (split.showingStartTime.isNotBlank()) values["showingStartTime"] = split.showingStartTime
    }
    if (showingEndTime.isNotBlank()) values["showingEndTime"] = showingEndTime
    values["showingAvailability"] = this[FirebaseConstants.FIELD_SHOWING_AVAILABILITY].asString()

    return values
}

internal data class SplitShowingDateTime(
    val showingDate: String = "",
    val showingStartTime: String = ""
)

internal fun splitLegacyShowingDateTime(dateTime: String): SplitShowingDateTime {
    if (dateTime.isBlank()) return SplitShowingDateTime()
    val parts = dateTime.split("T")
    return if (parts.size == 2) {
        SplitShowingDateTime(showingDate = parts[0], showingStartTime = parts[1])
    } else {
        SplitShowingDateTime()
    }
}

internal fun buildLegacyShowingDateTime(showingDate: String, showingStartTime: String): String {
    return if (showingDate.isNotBlank() && showingStartTime.isNotBlank()) {
        "${showingDate}T${showingStartTime}"
    } else if (showingDate.isNotBlank()) {
        "${showingDate}T00:00"
    } else {
        ""
    }
}

internal fun buildImageList(
    existingImageUrls: List<String>,
    uploadedImageUrls: List<String>
): List<String> = existingImageUrls + uploadedImageUrls

internal fun buildResidentialPropertyPayload(
    state: AddResidentialPropertyUiState,
    userId: String,
    ownerName: String,
    ownerEmail: String,
    ownerPhoto: String,
    ownerRole: String,
    isDraft: Boolean,
    includeCreatedAt: Boolean,
    imageUrls: List<String>
): Map<String, Any?> {
    val values = state.fieldValues
    val buildingName = values["propertyAddress"].orEmpty().trim()
    val location = state.selectedLocation
    val structuredAddress = listOf(
        buildingName,
        values["nameStreetArea"].orEmpty().trim(),
        values["landmark"].orEmpty().trim(),
        values["city"].orEmpty().trim(),
        values["state"].orEmpty().trim()
    ).filter { it.isNotBlank() }.joinToString(", ")
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
            key !in setOf("propertyPrice", "propertyAddress", "description", "state", "city", "nameStreetArea", "landmark")
        }
        .mapValues { (_, value) -> value.trim() }
        .toMutableMap<String, Any?>()
    dynamicValues[FirebaseConstants.FIELD_POSSESSION_STATUS] = normalizePossessionStatusForForm(values["possessionStatus"].orEmpty())
    dynamicValues[FirebaseConstants.FIELD_SHOWING_DATE_TIME] = buildLegacyShowingDateTime(
        values["showingDate"].orEmpty(),
        values["showingStartTime"].orEmpty()
    )

    val payload = mutableMapOf<String, Any?>(
        FirebaseConstants.FIELD_NAME to if (buildingName.isBlank()) "Untitled Property" else buildingName,
        FirebaseConstants.FIELD_DESCRIPTION to values["description"].orEmpty().trim(),
        FirebaseConstants.FIELD_PRICE to values["propertyPrice"].orEmpty().toLongOrNull(),
        FirebaseConstants.FIELD_PROPERTY_TYPE to RESIDENTIAL_PROPERTY_TYPE,
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
        FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
        FirebaseConstants.FIELD_IMAGES to imageUrls
    )
    if (includeCreatedAt) {
        payload[FirebaseConstants.FIELD_CREATED_AT] = FieldValue.serverTimestamp()
    }
    return payload + dynamicValues.filterValues { value ->
        when (value) {
            null -> false
            is String -> true
            else -> true
        }
    }
}

internal fun buildCommercialPropertyPayload(
    state: AddCommercialPropertyUiState,
    userId: String,
    ownerName: String,
    ownerEmail: String,
    ownerPhoto: String,
    ownerRole: String,
    isDraft: Boolean,
    includeCreatedAt: Boolean,
    imageUrls: List<String>
): Map<String, Any?> {
    val values = state.fieldValues
    val buildingName = values["buildingName"].orEmpty().trim()
    val location = state.selectedLocation
    val structuredAddress = listOf(
        buildingName,
        values["nameStreetArea"].orEmpty().trim(),
        values["landmark"].orEmpty().trim(),
        values["city"].orEmpty().trim(),
        values["state"].orEmpty().trim()
    ).filter { it.isNotBlank() }.joinToString(", ")
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
        .mapValues { (_, value) -> value.trim() }
        .toMutableMap<String, Any?>()
    if (values["possessionStatus"].orEmpty().isNotBlank()) {
        dynamicValues[FirebaseConstants.FIELD_POSSESSION_STATUS] = normalizePossessionStatusForForm(values["possessionStatus"].orEmpty())
    }
    dynamicValues[FirebaseConstants.FIELD_SHOWING_DATE_TIME] = buildLegacyShowingDateTime(
        values["showingDate"].orEmpty(),
        values["showingStartTime"].orEmpty()
    )

    val payload = mutableMapOf<String, Any?>(
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
        FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
        FirebaseConstants.FIELD_IMAGES to imageUrls
    )
    val liftType = values["liftType"].orEmpty()
    payload[FirebaseConstants.FIELD_PASSENGER_LIFT] = if (liftType in setOf("Passenger Lift", "Personal", "Common")) "Yes" else "No"
    payload[FirebaseConstants.FIELD_SERVICE_LIFT] = if (liftType == "Service Lift") "Yes" else "No"

    if (includeCreatedAt) {
        payload[FirebaseConstants.FIELD_CREATED_AT] = FieldValue.serverTimestamp()
    }
    return payload + dynamicValues
}

internal fun buildIndustrialPropertyPayload(
    state: AddIndustrialPropertyUiState,
    userId: String,
    ownerName: String,
    ownerEmail: String,
    ownerPhoto: String,
    ownerRole: String,
    isDraft: Boolean,
    includeCreatedAt: Boolean,
    imageUrls: List<String>
): Map<String, Any?> {
    val values = state.fieldValues
    val buildingName = values["buildingName"].orEmpty().trim()
    val location = state.selectedLocation
    val structuredAddress = listOf(
        buildingName,
        values["nameStreetArea"].orEmpty().trim(),
        values["landmark"].orEmpty().trim(),
        values["city"].orEmpty().trim(),
        values["state"].orEmpty().trim()
    ).filter { it.isNotBlank() }.joinToString(", ")
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
        .mapValues { (_, value) -> value.trim() }
        .toMutableMap<String, Any?>()
    if (values["possessionStatus"].orEmpty().isNotBlank()) {
        dynamicValues[FirebaseConstants.FIELD_POSSESSION_STATUS] = normalizePossessionStatusForForm(values["possessionStatus"].orEmpty())
    }
    dynamicValues[FirebaseConstants.FIELD_SHOWING_DATE_TIME] = buildLegacyShowingDateTime(
        values["showingDate"].orEmpty(),
        values["showingStartTime"].orEmpty()
    )

    val payload = mutableMapOf<String, Any?>(
        FirebaseConstants.FIELD_NAME to if (buildingName.isBlank()) "Untitled Industrial Property" else buildingName,
        FirebaseConstants.FIELD_DESCRIPTION to values["description"].orEmpty().trim(),
        FirebaseConstants.FIELD_PRICE to values["propertyPrice"].orEmpty().toLongOrNull(),
        FirebaseConstants.FIELD_PROPERTY_TYPE to INDUSTRIAL_PROPERTY_TYPE,
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
        FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
        FirebaseConstants.FIELD_IMAGES to imageUrls
    )
    if (includeCreatedAt) {
        payload[FirebaseConstants.FIELD_CREATED_AT] = FieldValue.serverTimestamp()
    }
    return payload + dynamicValues
}

internal fun buildLandPropertyPayload(
    state: AddLandPropertyUiState,
    userId: String,
    ownerName: String,
    ownerEmail: String,
    ownerPhoto: String,
    ownerRole: String,
    isDraft: Boolean,
    includeCreatedAt: Boolean,
    imageUrls: List<String>
): Map<String, Any?> {
    val values = state.fieldValues
    val propertyName = values["propertyName"].orEmpty().trim()
    val buildingName = propertyName
    val location = state.selectedLocation
    val structuredAddress = listOf(
        buildingName,
        values["nameStreetArea"].orEmpty().trim(),
        values["landmark"].orEmpty().trim(),
        values["city"].orEmpty().trim(),
        values["state"].orEmpty().trim()
    ).filter { it.isNotBlank() }.joinToString(", ")
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
            key !in setOf("propertyName", "propertyPrice", "propertyAddress", "description", "state", "city", "nameStreetArea", "landmark")
        }
        .mapValues { (_, value) -> value.trim() }
        .toMutableMap<String, Any?>()

    val payload = mutableMapOf<String, Any?>(
        FirebaseConstants.FIELD_NAME to if (propertyName.isBlank()) (if (buildingName.isBlank()) "Untitled Land Property" else buildingName) else propertyName,
        FirebaseConstants.FIELD_DESCRIPTION to values["description"].orEmpty().trim(),
        FirebaseConstants.FIELD_PRICE to values["propertyPrice"].orEmpty().toLongOrNull(),
        FirebaseConstants.FIELD_PROPERTY_TYPE to LAND_PROPERTY_TYPE,
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
        FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
        FirebaseConstants.FIELD_IMAGES to imageUrls
    )
    if (includeCreatedAt) {
        payload[FirebaseConstants.FIELD_CREATED_AT] = FieldValue.serverTimestamp()
    }
    return payload + dynamicValues
}
