package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.model.Property
import com.example.myapplication.utils.FirebaseConstants
import com.example.myapplication.utils.getGeohashQueries
import com.example.myapplication.utils.haversineDistanceKm
import com.example.myapplication.utils.normalizeCoordinate
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Locale

interface PropertyRepository {
    suspend fun fetchPropertiesPage(
        limitCount: Int,
        cursor: DocumentSnapshot?,
        viewerRole: String,
        propertyType: String = "all",
        listingType: String? = null,
        geoFilter: GeoFilter? = null,
        filters: Map<String, Any> = emptyMap(),
        locationRestriction: LocationRestriction? = null
    ): PropertyPageResult

    suspend fun fetchUserPropertiesPage(
        userId: String,
        limitCount: Int,
        cursor: DocumentSnapshot?,
        listingType: String,
        viewerRole: String
    ): PropertyPageResult

    /**
     * Fetch a single property by ID for the details screen.
     * Returns null if:
     *  - not found
     *  - viewer does not have access to developer-owned content
     *  - property is a draft and viewer is not the owner
     * Matches web's loadPropertyDetails() guards exactly.
     */
    suspend fun fetchPropertyById(
        propertyId: String,
        viewerRole: String = "user",
        viewerUserId: String? = null
    ): Property?

    /** Fetch the mobile number of the property owner. Returns null if not found. */
    suspend fun getOwnerMobileNumber(ownerUid: String): String?

    suspend fun deleteProperty(propertyId: String, deletedBy: String)

    /** Count all active properties (published + drafts) owned by a user. */
    suspend fun getUserActivePropertyCount(userId: String): Int
}

data class PropertyPageResult(
    val items: List<Property>,
    val nextCursor: DocumentSnapshot?,
    val hasMore: Boolean
)

data class SearchArea(
    val placeId: String? = null,
    val displayName: String = "",
    val formattedAddress: String = "",
    val lat: Double? = null,
    val lng: Double? = null
)

data class GeoFilter(
    val centers: List<SearchArea> = emptyList(),
    val radiusKm: Double? = null
)

data class LocationRestriction(
    val stateCode: String = "",
    val stateName: String = "",
    val districtName: String = ""
)

class PropertyRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val interestedRepository: InterestedRepository
) : PropertyRepository {

    private companion object {
        const val TAG = "MyPropertiesRepo"
    }

    override suspend fun fetchPropertiesPage(
        limitCount: Int,
        cursor: DocumentSnapshot?,
        viewerRole: String,
        propertyType: String,
        listingType: String?,
        geoFilter: GeoFilter?,
        filters: Map<String, Any>,
        locationRestriction: LocationRestriction?
    ): PropertyPageResult {
        val baseCollection = firestore.collection(FirebaseConstants.COLLECTION_PROPERTIES)
        val allDocs = mutableListOf<DocumentSnapshot>()
        val isGeoQuery = !geoFilter?.centers.isNullOrEmpty() && (geoFilter?.radiusKm ?: 0.0) > 0.0

        fun buildBaseQuery(): Query {
            var query: Query = baseCollection.whereEqualTo(FirebaseConstants.FIELD_IS_ACTIVE, true)
            if (propertyType.isNotBlank() && propertyType != "all") {
                query = query.whereEqualTo(FirebaseConstants.FIELD_PROPERTY_TYPE, propertyType)
            }
            if (!listingType.isNullOrBlank()) {
                query = query.whereEqualTo(FirebaseConstants.FIELD_LISTING_TYPE, listingType)
            }
            return query
        }

        if (isGeoQuery) {
            val uniqueRanges = geoFilter!!.centers
                .flatMap { center -> getGeohashQueries(center.lat, center.lng, geoFilter.radiusKm) }
                .distinctBy { "${it.start}:${it.end}" }

            uniqueRanges.forEach { range ->
                val snapshot = buildBaseQuery()
                    .whereGreaterThanOrEqualTo(FirebaseConstants.FIELD_GEOHASH, range.start)
                    .whereLessThanOrEqualTo(FirebaseConstants.FIELD_GEOHASH, range.end)
                    .orderBy(FirebaseConstants.FIELD_GEOHASH)
                    .orderBy(FirebaseConstants.FIELD_CREATED_AT, Query.Direction.DESCENDING)
                    .limit((limitCount * 10).toLong())
                    .get()
                    .await()

                snapshot.documents.forEach { doc ->
                    if (allDocs.none { it.id == doc.id }) {
                        allDocs += doc
                    }
                }
            }
            allDocs.retainAll { doc -> matchesGeoFilter(doc.data.orEmpty(), geoFilter) }
            allDocs.sortByDescending { (it.getTimestamp(FirebaseConstants.FIELD_CREATED_AT)?.seconds ?: 0L) }
        } else {
            var query = buildBaseQuery()
                .orderBy(FirebaseConstants.FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .limit((limitCount * 10 + 1).toLong())
            if (cursor != null) {
                query = query.startAfter(cursor)
            }
            val snapshot = query.get().await()
            allDocs += snapshot.documents
        }

        val filteredDocs = allDocs.filter { doc ->
            val data = doc.data.orEmpty()
            isPropertyPublished(data) &&
                canAccessDeveloperOwnedContent(data[FirebaseConstants.FIELD_OWNER_ROLE] as? String ?: "", viewerRole) &&
                matchesProperty(data, filters, propertyType, locationRestriction)
        }

        val paginatedDocs = if (isGeoQuery && cursor != null) {
            val cursorIndex = filteredDocs.indexOfFirst { it.id == cursor.id }
            if (cursorIndex >= 0) filteredDocs.drop(cursorIndex + 1) else emptyList()
        } else {
            filteredDocs
        }
        val hasMore = paginatedDocs.size > limitCount
        val pageDocs = paginatedDocs.take(limitCount)
        val nextCursor = if (hasMore && pageDocs.isNotEmpty()) {
            allDocs.find { it.id == pageDocs.last().id }
        } else {
            null
        }

        Log.d(
            TAG,
            "homeFetch propertyType=$propertyType listingType=$listingType viewerRole=$viewerRole " +
                "geo=$isGeoQuery raw=${allDocs.size} filtered=${filteredDocs.size} page=${pageDocs.size} " +
                "hasMore=$hasMore filters=${filters.keys} locationRestriction=$locationRestriction"
        )

        return PropertyPageResult(
            items = pageDocs.mapNotNull(::mapToProperty),
            nextCursor = nextCursor,
            hasMore = hasMore
        )
    }

    override suspend fun fetchUserPropertiesPage(
        userId: String,
        limitCount: Int,
        cursor: DocumentSnapshot?,
        listingType: String,
        viewerRole: String
    ): PropertyPageResult {
        Log.d(
            TAG,
            "fetchUserPropertiesPage: userId=$userId filter=$listingType viewerRole=$viewerRole cursor=${cursor?.id} limit=$limitCount"
        )

        var query = firestore
            .collection(FirebaseConstants.COLLECTION_PROPERTIES)
            .whereEqualTo(FirebaseConstants.FIELD_OWNER, userId)
            .whereEqualTo(FirebaseConstants.FIELD_IS_ACTIVE, true)

        query = when (listingType) {
            FirebaseConstants.PROPERTY_STATUS_DRAFT -> {
                query.whereEqualTo(
                    FirebaseConstants.FIELD_STATUS,
                    FirebaseConstants.PROPERTY_STATUS_DRAFT
                )
            }
            "rent", "sale" -> {
                query.whereEqualTo(FirebaseConstants.FIELD_LISTING_TYPE, listingType)
            }
            else -> query
        }

        val snapshot = query.get().await()
        val allDocs = snapshot.documents

        Log.d(TAG, "fetchUserPropertiesPage: rawMatchedDocs=${allDocs.size}")
        allDocs.forEach { doc ->
            val data = doc.data.orEmpty()
            Log.d(
                TAG,
                "rawDoc id=${doc.id} owner=${data[FirebaseConstants.FIELD_OWNER]} userId=${data[FirebaseConstants.FIELD_USER_ID]} ownerRole=${data[FirebaseConstants.FIELD_OWNER_ROLE]} isActive=${data[FirebaseConstants.FIELD_IS_ACTIVE]} status=${data[FirebaseConstants.FIELD_STATUS]} listingType=${data[FirebaseConstants.FIELD_LISTING_TYPE]}"
            )
        }

        var properties = allDocs.mapNotNull { doc ->
            mapToProperty(doc)
        }.filter { property ->
            property.owner == userId || canAccessDeveloperOwnedContent(property.ownerRole, viewerRole)
        }

        Log.d(TAG, "afterRoleFilter: properties=${properties.size}")

        if (
            listingType == "all" ||
            listingType == "rent" ||
            listingType == "sale"
        ) {
            properties = properties.filter { property ->
                property.status != FirebaseConstants.PROPERTY_STATUS_DRAFT
            }
            Log.d(TAG, "afterDraftFilter: properties=${properties.size}")
        }

        val sortedProperties = properties.sortedByDescending { it.createdAt?.seconds ?: 0L }
        sortedProperties.forEach { property ->
            Log.d(
                TAG,
                "sortedProperty id=${property.id} owner=${property.owner} ownerRole=${property.ownerRole} status=${property.status} createdAt=${property.createdAt?.seconds}"
            )
        }

        val startIndex = if (cursor != null) {
            val cursorIndex = sortedProperties.indexOfFirst { it.id == cursor.id }
            if (cursorIndex >= 0) cursorIndex + 1 else 0
        } else null

        val safeStartIndex = startIndex ?: 0
        val endIndex = minOf(safeStartIndex + limitCount, sortedProperties.size)
        val pageItems = sortedProperties.subList(safeStartIndex, endIndex)
        val hasMore = endIndex < sortedProperties.size

        val nextCursor = if (hasMore && pageItems.isNotEmpty()) {
            allDocs.find { it.id == pageItems.last().id }
        } else null

        return PropertyPageResult(
            items = pageItems,
            nextCursor = nextCursor,
            hasMore = hasMore
        )
    }

    override suspend fun fetchPropertyById(
        propertyId: String,
        viewerRole: String,
        viewerUserId: String?
    ): Property? {
        return try {
            val doc = firestore
                .collection(FirebaseConstants.COLLECTION_PROPERTIES)
                .document(propertyId)
                .get()
                .await()
            if (!doc.exists()) return null

            val property = mapToProperty(doc) ?: return null

            // Guard 1: Developer-owned content is hidden from non-developers
            // Matches: canAccessDeveloperOwnedContent(propertyData.ownerRole, viewerRole)
            if (!canAccessDeveloperOwnedContent(property.ownerRole, viewerRole)) return null

            // Guard 2: Draft properties are only visible to their owner
            // Matches: isPropertyDraft(data) && owner !== viewerUserId && userId !== viewerUserId
            val isDraft = property.status == FirebaseConstants.PROPERTY_STATUS_DRAFT
            if (isDraft && property.owner != viewerUserId) return null

            property
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun getOwnerMobileNumber(ownerUid: String): String? {
        return try {
            if (ownerUid.isBlank()) return null
            val doc = firestore
                .collection(FirebaseConstants.COLLECTION_USERS)
                .document(ownerUid)
                .get()
                .await()
            doc.getString(FirebaseConstants.FIELD_MOBILE)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun deleteProperty(propertyId: String, deletedBy: String) {
        firestore
            .collection(FirebaseConstants.COLLECTION_PROPERTIES)
            .document(propertyId)
            .update(
                mapOf(
                    FirebaseConstants.FIELD_IS_ACTIVE to false,
                    FirebaseConstants.FIELD_DELETED_AT to FieldValue.serverTimestamp(),
                    FirebaseConstants.FIELD_DELETED_BY to deletedBy
                )
            )
            .await()

        // Cascade: sync interested property snapshots (matches web's myPropertiesService.js:151)
        try {
            interestedRepository.syncInterestedPropertySnapshots(propertyId)
        } catch (e: Exception) {
            Log.e(TAG, "Error cascading interested sync after delete for $propertyId", e)
        }
    }

    override suspend fun getUserActivePropertyCount(userId: String): Int {
        return try {
            val snapshot = firestore.collection(FirebaseConstants.COLLECTION_PROPERTIES)
                .whereEqualTo(FirebaseConstants.FIELD_OWNER, userId)
                .whereEqualTo(FirebaseConstants.FIELD_IS_ACTIVE, true)
                .get()
                .await()
            snapshot.size()
        } catch (e: Exception) {
            Log.e(TAG, "Error counting active properties for user $userId", e)
            0
        }
    }

    private fun str(data: Map<String, Any?>, key: String): String = when (val value = data[key]) {
        null -> ""
        is String -> value
        is Number -> value.toString()
        else -> value.toString()
    }

    private fun isPropertyPublished(data: Map<String, Any?>): Boolean {
        return (data[FirebaseConstants.FIELD_STATUS] as? String ?: "") !=
            FirebaseConstants.PROPERTY_STATUS_DRAFT
    }

    private fun normalizeText(value: Any?): String {
        return value?.toString()?.trim()?.lowercase(Locale.US).orEmpty()
    }

    private fun normalizeCompactText(value: Any?): String {
        return normalizeText(value).replace(Regex("\\s+"), " ")
    }

    private fun propertyLocationText(property: Map<String, Any?>): String {
        val geo = property[FirebaseConstants.FIELD_GEO] as? Map<*, *>
        return normalizeCompactText(
            listOf(
                property[FirebaseConstants.FIELD_STATE_CODE],
                property[FirebaseConstants.FIELD_STATE],
                property[FirebaseConstants.FIELD_CITY],
                property[FirebaseConstants.FIELD_CITY_STATE],
                property[FirebaseConstants.FIELD_DISTRICT],
                property[FirebaseConstants.FIELD_NAME_STREET_AREA],
                property[FirebaseConstants.FIELD_LANDMARK],
                property[FirebaseConstants.FIELD_ADDRESS],
                property[FirebaseConstants.FIELD_LOCATION],
                property[FirebaseConstants.FIELD_LOCATION_DETAILS],
                geo?.get("formattedAddress"),
                geo?.get("displayName")
            ).filterNotNull().joinToString(", ")
        )
    }

    private fun exactOrContainedLocationMatch(candidateValues: List<Any?>, expectedValue: String): Boolean {
        val expected = normalizeCompactText(expectedValue)
        if (expected.isBlank()) return true
        return candidateValues.any { candidate ->
            val normalizedCandidate = normalizeCompactText(candidate)
            normalizedCandidate == expected || normalizedCandidate.contains(expected)
        }
    }

    private fun matchesLocationRestriction(
        property: Map<String, Any?>,
        locationRestriction: LocationRestriction?
    ): Boolean {
        if (locationRestriction == null) return true

        val geo = property[FirebaseConstants.FIELD_GEO] as? Map<*, *>
        val combinedLocationText = propertyLocationText(property)
        var hasMatchingStateCode = false

        if (locationRestriction.stateCode.isNotBlank()) {
            val propertyStateCode = normalizeText(property[FirebaseConstants.FIELD_STATE_CODE])
            if (propertyStateCode.isNotBlank() &&
                propertyStateCode != normalizeText(locationRestriction.stateCode)
            ) {
                return false
            }
            hasMatchingStateCode = propertyStateCode.isNotBlank()
        }

        if (!hasMatchingStateCode && locationRestriction.stateName.isNotBlank() &&
            !exactOrContainedLocationMatch(
                listOf(
                    property[FirebaseConstants.FIELD_STATE],
                    property[FirebaseConstants.FIELD_CITY_STATE],
                    geo?.get("formattedAddress"),
                    property[FirebaseConstants.FIELD_ADDRESS],
                    property[FirebaseConstants.FIELD_LOCATION_DETAILS],
                    combinedLocationText
                ),
                locationRestriction.stateName
            )
        ) {
            return false
        }

        if (locationRestriction.districtName.isNotBlank() &&
            !exactOrContainedLocationMatch(
                listOf(
                    property[FirebaseConstants.FIELD_CITY],
                    property[FirebaseConstants.FIELD_CITY_STATE],
                    property[FirebaseConstants.FIELD_DISTRICT],
                    geo?.get("formattedAddress"),
                    property[FirebaseConstants.FIELD_ADDRESS],
                    property[FirebaseConstants.FIELD_LOCATION_DETAILS],
                    combinedLocationText
                ),
                locationRestriction.districtName
            )
        ) {
            return false
        }

        return true
    }

    private fun matchesGeoFilter(property: Map<String, Any?>, geoFilter: GeoFilter?): Boolean {
        if (geoFilter?.radiusKm == null || geoFilter.radiusKm <= 0.0) return true
        val geo = property[FirebaseConstants.FIELD_GEO] as? Map<*, *>
        val propLat = normalizeCoordinate(geo?.get("lat"))
        val propLng = normalizeCoordinate(geo?.get("lng"))
        if (propLat == null || propLng == null) return false

        val centers = geoFilter.centers.filter { it.lat != null && it.lng != null }
        if (centers.isEmpty()) return true

        return centers.any { center ->
            val centerLat = center.lat ?: return@any false
            val centerLng = center.lng ?: return@any false
            haversineDistanceKm(centerLat, centerLng, propLat, propLng) <= geoFilter.radiusKm
        }
    }

    private fun orderedIndexMatch(valueOrder: List<String>, propertyValue: String, filterValue: String): Boolean {
        val filterIndex = valueOrder.indexOf(filterValue)
        val propertyIndex = valueOrder.indexOf(propertyValue)
        return propertyIndex >= filterIndex
    }

    private fun filterValues(filterValue: Any?): List<String>? {
        return when (filterValue) {
            is List<*> -> filterValue.mapNotNull { it as? String }.filter { it.isNotBlank() }
            is String -> if (filterValue.isNotBlank() && filterValue != "Any") listOf(filterValue) else null
            else -> null
        }
    }

    private fun matchesBhk(propertyBhk: String, filterValues: List<String>): Boolean {
        return filterValues.any { filterValue ->
            if (filterValue == "4+ BHK") {
                val numeric = Regex("(\\d+\\.?\\d*)").find(propertyBhk)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                numeric >= 4.0
            } else {
                propertyBhk == filterValue
            }
        }
    }

    private fun matchesParking(property: Map<String, Any?>, filterValues: List<String>): Boolean {
        val hasCoveredParking = str(property, FirebaseConstants.FIELD_COVERED_PARKING).isNotBlank() &&
            str(property, FirebaseConstants.FIELD_COVERED_PARKING) != "No"
        val hasOpenParking = str(property, FirebaseConstants.FIELD_OPEN_PARKING).isNotBlank() &&
            str(property, FirebaseConstants.FIELD_OPEN_PARKING) != "No"
        val hasAnyParking = hasCoveredParking || hasOpenParking
        return filterValues.any { filterValue ->
            when (filterValue) {
                "Yes" -> hasAnyParking
                "No" -> !hasAnyParking
                else -> false
            }
        }
    }

    private fun matchesProperty(
        property: Map<String, Any?>,
        filters: Map<String, Any>,
        propertyType: String,
        locationRestriction: LocationRestriction?
    ): Boolean {
        if (!matchesLocationRestriction(property, locationRestriction)) return false
        if (filters.isEmpty()) return true

        val price = (property[FirebaseConstants.FIELD_PRICE] as? Number)?.toDouble()
        (filters["priceMin"] as? Number)?.toDouble()?.let { minPrice ->
            if (price == null || price < minPrice) return false
        }
        (filters["priceMax"] as? Number)?.toDouble()?.let { maxPrice ->
            if (price == null || price > maxPrice) return false
        }

        (filters["ownerRole"] as? String)?.takeIf { it.isNotBlank() }?.let { roleFilter ->
            val propRole = str(property, FirebaseConstants.FIELD_OWNER_ROLE).ifBlank { "user" }.lowercase()
            if (propRole != roleFilter.lowercase()) return false
        }

        when (propertyType) {
            "residential" -> {
                filterValues(filters["residentialType"])?.let { values ->
                    if (str(property, FirebaseConstants.FIELD_RESIDENTIAL_TYPE) !in values) return false
                }

                filterValues(filters["bhkType"])?.let { values ->
                    if (!matchesBhk(str(property, FirebaseConstants.FIELD_BHK_CONFIG), values)) return false
                }

                filterValues(filters["possessionStatus"])?.let { values ->
                    val propStatus = normalizePossessionStatus(str(property, FirebaseConstants.FIELD_POSSESSION_STATUS))
                    if (values.none { normalizePossessionStatus(it) == propStatus }) return false
                }

                filterValues(filters["hasParking"])?.let { values ->
                    if (!matchesParking(property, values)) return false
                }

                if (!matchesMinMaxStringNumber(property, FirebaseConstants.FIELD_BUILT_UP_AREA, filters["builtUpAreaMin"], filters["builtUpAreaMax"])) return false

                filterValues(filters["furnishing"])?.let { values ->
                    if (str(property, FirebaseConstants.FIELD_FURNISHING) !in values) return false
                }

                filterValues(filters["facing"])?.let { values ->
                    if (str(property, FirebaseConstants.FIELD_FACING) !in values) return false
                }
            }
            "commercial" -> {
                filterValues(filters["commercialType"])?.let { values ->
                    if (str(property, FirebaseConstants.FIELD_COMMERCIAL_TYPE) !in values) return false
                }
                if (!matchesMinMaxStringNumber(property, FirebaseConstants.FIELD_BUILT_UP_AREA, filters["builtUpAreaMin"], filters["builtUpAreaMax"])) return false

                filterValues(filters["possessionStatus"])?.let { values ->
                    val propStatus = normalizePossessionStatus(str(property, FirebaseConstants.FIELD_POSSESSION_STATUS))
                    if (values.none { normalizePossessionStatus(it) == propStatus }) return false
                }

                filterValues(filters["parkingType"])?.let { values ->
                    if (str(property, FirebaseConstants.FIELD_PARKING_TYPE) !in values) return false
                }

                filterValues(filters["washroomType"])?.let { values ->
                    if (str(property, FirebaseConstants.FIELD_WASHROOM_TYPE) !in values) return false
                }
            }
            "industrial" -> {
                filterValues(filters["industrialType"])?.let { values ->
                    if (str(property, FirebaseConstants.FIELD_INDUSTRIAL_TYPE) !in values) return false
                }
                if (!matchesMinMaxStringNumber(property, FirebaseConstants.FIELD_PLOT_AREA, filters["plotAreaMin"], filters["plotAreaMax"])) return false
                if (!matchesMinMaxStringNumber(property, FirebaseConstants.FIELD_BUILT_UP_AREA, filters["builtUpAreaMin"], filters["builtUpAreaMax"])) return false

                filterValues(filters["electricityLoad"])?.let { values ->
                    val propValue = str(property, FirebaseConstants.FIELD_ELECTRICITY_LOAD)
                    val valueOrder = listOf("Up to 50 KW", "50-100 KW", "100-500 KW", "500+ KW")
                    if (values.none { orderedIndexMatch(valueOrder, propValue, it) }) return false
                }
            }
            "land" -> {
                filterValues(filters["landType"])?.let { values ->
                    if (str(property, FirebaseConstants.FIELD_LAND_TYPE) !in values) return false
                }
                if (!matchesMinMaxStringNumber(property, FirebaseConstants.FIELD_PROPERTY_AREA, filters["propertyAreaMin"], filters["propertyAreaMax"])) return false

                filterValues(filters["landStatus"])?.let { values ->
                    if (str(property, FirebaseConstants.FIELD_LAND_STATUS) !in values) return false
                }

                filterValues(filters["roadWidth"])?.let { values ->
                    val propValue = str(property, FirebaseConstants.FIELD_ROAD_WIDTH)
                    val valueOrder = listOf("Less than 20 ft", "20-30 ft", "30-40 ft", "40-60 ft", "60+ ft")
                    if (values.none { orderedIndexMatch(valueOrder, propValue, it) }) return false
                }

                filterValues(filters["landFacing"])?.let { values ->
                    if (str(property, FirebaseConstants.FIELD_LAND_FACING) !in values) return false
                }
            }
        }

        return true
    }

    private fun matchesMinMaxStringNumber(
        property: Map<String, Any?>,
        field: String,
        minValue: Any?,
        maxValue: Any?
    ): Boolean {
        val propertyValue = str(property, field).toDoubleOrNull()
        (minValue as? Number)?.toDouble()?.let { min ->
            if (propertyValue == null || propertyValue < min) return false
        }
        (maxValue as? Number)?.toDouble()?.let { max ->
            if (propertyValue == null || propertyValue > max) return false
        }
        return true
    }

    private fun normalizePossessionStatus(value: String): String {
        return when (normalizeText(value)) {
            "ready to move", "ready possession", "ready_possession" -> "ready_possession"
            "under construction", "under_construction" -> "under_construction"
            else -> value
        }
    }

    private fun mapToProperty(doc: DocumentSnapshot): Property? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        val geo = data[FirebaseConstants.FIELD_GEO] as? Map<*, *>

        val images = (data[FirebaseConstants.FIELD_IMAGES] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val amenities = (data[FirebaseConstants.FIELD_AMENITIES] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val createdAt = data[FirebaseConstants.FIELD_CREATED_AT] as? Timestamp
        val updatedAt = data[FirebaseConstants.FIELD_UPDATED_AT] as? Timestamp

        val mappedPrice = (data[FirebaseConstants.FIELD_PRICE] as? Number)?.toDouble() ?: 0.0
        val mappedRent = (data[FirebaseConstants.FIELD_RENT] as? Number)?.toDouble() ?: mappedPrice

        return Property(
            id = doc.id,
            name = str(data, FirebaseConstants.FIELD_NAME),
            images = images,
            propertyType = str(data, FirebaseConstants.FIELD_PROPERTY_TYPE),
            listingType = str(data, FirebaseConstants.FIELD_LISTING_TYPE),
            price = mappedPrice,
            rent = mappedRent,
            location = str(data, FirebaseConstants.FIELD_LOCATION),
            cityState = str(data, FirebaseConstants.FIELD_CITY_STATE),
            state = str(data, FirebaseConstants.FIELD_STATE),
            stateCode = str(data, FirebaseConstants.FIELD_STATE_CODE),
            city = str(data, FirebaseConstants.FIELD_CITY),
            district = str(data, FirebaseConstants.FIELD_DISTRICT),
            address = str(data, FirebaseConstants.FIELD_ADDRESS),
            nameStreetArea = str(data, FirebaseConstants.FIELD_NAME_STREET_AREA),
            landmark = str(data, FirebaseConstants.FIELD_LANDMARK),
            locationDetails = str(data, FirebaseConstants.FIELD_LOCATION_DETAILS),
            status = str(data, FirebaseConstants.FIELD_STATUS),
            isActive = data[FirebaseConstants.FIELD_IS_ACTIVE] as? Boolean ?: true,
            ownerRole = str(data, FirebaseConstants.FIELD_OWNER_ROLE),
            uniqueId = str(data, FirebaseConstants.FIELD_UNIQUE_ID),
            createdAt = createdAt,
            updatedAt = updatedAt,
            geoLat = normalizeCoordinate(geo?.get("lat")),
            geoLng = normalizeCoordinate(geo?.get("lng")),
            geoFormattedAddress = geo?.get("formattedAddress") as? String ?: "",
            geoDisplayName = geo?.get("displayName") as? String ?: "",

            // Owner
            owner = str(data, FirebaseConstants.FIELD_OWNER),
            ownerName = str(data, FirebaseConstants.FIELD_OWNER_NAME),
            ownerEmail = str(data, FirebaseConstants.FIELD_OWNER_EMAIL),
            ownerMobile = str(data, FirebaseConstants.FIELD_OWNER_MOBILE),
            ownerPhoto = str(data, FirebaseConstants.FIELD_OWNER_PHOTO),

            // Description & Amenities
            description = str(data, FirebaseConstants.FIELD_DESCRIPTION),
            amenities = amenities,

            // Location sub-fields
            buildingName = str(data, FirebaseConstants.FIELD_BUILDING_NAME),
            locality = str(data, FirebaseConstants.FIELD_LOCALITY),
            zoneType = str(data, FirebaseConstants.FIELD_ZONE_TYPE),
            locationHub = str(data, FirebaseConstants.FIELD_LOCATION_HUB),

            // Type & Category
            residentialType = str(data, FirebaseConstants.FIELD_RESIDENTIAL_TYPE),
            bhkConfig = str(data, FirebaseConstants.FIELD_BHK_CONFIG),
            commercialType = str(data, FirebaseConstants.FIELD_COMMERCIAL_TYPE),
            industrialType = str(data, FirebaseConstants.FIELD_INDUSTRIAL_TYPE),
            landType = str(data, FirebaseConstants.FIELD_LAND_TYPE),

            // Status & Availability
            possessionStatus = str(data, FirebaseConstants.FIELD_POSSESSION_STATUS),
            availableFrom = str(data, FirebaseConstants.FIELD_AVAILABLE_FROM),

            // Property & Legal
            propertyCondition = str(data, FirebaseConstants.FIELD_PROPERTY_CONDITION),
            ownership = str(data, FirebaseConstants.FIELD_OWNERSHIP),
            plotArea = str(data, FirebaseConstants.FIELD_PLOT_AREA),
            propertyArea = str(data, FirebaseConstants.FIELD_PROPERTY_AREA),
            propertyAreaUnit = str(data, FirebaseConstants.FIELD_PROPERTY_AREA_UNIT),
            builtUpArea = str(data, FirebaseConstants.FIELD_BUILT_UP_AREA),
            carpetArea = str(data, FirebaseConstants.FIELD_CARPET_AREA),
            totalConstructionArea = str(data, FirebaseConstants.FIELD_TOTAL_CONSTRUCTION_AREA),
            frontage = str(data, FirebaseConstants.FIELD_FRONTAGE),
            roadAccess = str(data, FirebaseConstants.FIELD_ROAD_ACCESS),

            // Residential
            propertyAge = str(data, FirebaseConstants.FIELD_PROPERTY_AGE),
            floorNumber = str(data, FirebaseConstants.FIELD_FLOOR_NUMBER),
            bathrooms = str(data, FirebaseConstants.FIELD_BATHROOMS),
            balconies = str(data, FirebaseConstants.FIELD_BALCONIES),
            furnishing = str(data, FirebaseConstants.FIELD_FURNISHING),
            coveredParking = str(data, FirebaseConstants.FIELD_COVERED_PARKING),
            openParking = str(data, FirebaseConstants.FIELD_OPEN_PARKING),
            parkingCharges = str(data, FirebaseConstants.FIELD_PARKING_CHARGES),
            tenantType = str(data, FirebaseConstants.FIELD_TENANT_TYPE),
            petFriendly = str(data, FirebaseConstants.FIELD_PET_FRIENDLY),
            maintenanceCharges = str(data, FirebaseConstants.FIELD_MAINTENANCE_CHARGES),
            servantRoom = str(data, FirebaseConstants.FIELD_SERVANT_ROOM),
            amenitiesText = str(data, FirebaseConstants.FIELD_AMENITIES_TEXT),
            residentsCount = str(data, FirebaseConstants.FIELD_RESIDENTS_COUNT),

            // Commercial / Industrial shared
            parkingType = str(data, FirebaseConstants.FIELD_PARKING_TYPE),
            washroomType = str(data, FirebaseConstants.FIELD_WASHROOM_TYPE),

            // Industrial
            shedHeight = str(data, FirebaseConstants.FIELD_SHED_HEIGHT),
            shedSideWallHeight = str(data, FirebaseConstants.FIELD_SHED_SIDE_WALL_HEIGHT),
            plotDimensions = str(data, FirebaseConstants.FIELD_PLOT_DIMENSIONS),
            shedBuiltUpArea = str(data, FirebaseConstants.FIELD_SHED_BUILT_UP_AREA),
            builtUpConstructionArea = str(data, FirebaseConstants.FIELD_BUILT_UP_CONSTRUCTION_AREA),
            electricityLoad = str(data, FirebaseConstants.FIELD_ELECTRICITY_LOAD),
            waterAvailable = str(data, FirebaseConstants.FIELD_WATER_AVAILABLE),
            preLeased = str(data, FirebaseConstants.FIELD_PRE_LEASED),
            preRented = str(data, FirebaseConstants.FIELD_PRE_RENTED),

            // Land
            areaAcres = str(data, FirebaseConstants.FIELD_AREA_ACRES),
            plotLength = str(data, FirebaseConstants.FIELD_PLOT_LENGTH),
            plotBreadth = str(data, FirebaseConstants.FIELD_PLOT_BREADTH),
            landFacing = str(data, FirebaseConstants.FIELD_LAND_FACING),
            roadWidth = str(data, FirebaseConstants.FIELD_ROAD_WIDTH),
            landStatus = str(data, FirebaseConstants.FIELD_LAND_STATUS),

            // Floors & Elevation
            yourFloor = str(data, FirebaseConstants.FIELD_YOUR_FLOOR),
            totalFloors = str(data, FirebaseConstants.FIELD_TOTAL_FLOORS),
            staircases = str(data, FirebaseConstants.FIELD_STAIRCASES),
            passengerLift = str(data, FirebaseConstants.FIELD_PASSENGER_LIFT),
            serviceLift = str(data, FirebaseConstants.FIELD_SERVICE_LIFT),

            // Facing
            facing = str(data, FirebaseConstants.FIELD_FACING),
            rearFacing = str(data, FirebaseConstants.FIELD_REAR_FACING),
            roadFacing = str(data, FirebaseConstants.FIELD_ROAD_FACING),

            // Lease & Financials
            priceNegotiable = str(data, FirebaseConstants.FIELD_PRICE_NEGOTIABLE),
            rentNegotiable = str(data, FirebaseConstants.FIELD_RENT_NEGOTIABLE),
            securityDeposit = str(data, FirebaseConstants.FIELD_SECURITY_DEPOSIT),
            rentIncrease = str(data, FirebaseConstants.FIELD_RENT_INCREASE),
            lockInPeriod = str(data, FirebaseConstants.FIELD_LOCK_IN_PERIOD),

            // Charges & Inclusions
            dampUpsIncluded = str(data, FirebaseConstants.FIELD_DAMP_UPS_INCLUDED),
            electricityIncluded = str(data, FirebaseConstants.FIELD_ELECTRICITY_INCLUDED),
            waterChargesIncluded = str(data, FirebaseConstants.FIELD_WATER_CHARGES_INCLUDED),

            // Interested
            interestedCount = (data[FirebaseConstants.FIELD_INTERESTED_COUNT] as? Number)?.toInt() ?: 0,
        )
    }

    /**
     * Non-developers cannot see developer-owned content.
     */
    private fun canAccessDeveloperOwnedContent(ownerRole: String, viewerRole: String): Boolean {
        val isDevOwner = normalizeRole(ownerRole) == FirebaseConstants.ROLE_DEVELOPER
        val isDevViewer = normalizeRole(viewerRole) == FirebaseConstants.ROLE_DEVELOPER
        return !isDevOwner || isDevViewer
    }

    private fun normalizeRole(role: String): String {
        return when (role.trim().lowercase(Locale.US)) {
            FirebaseConstants.ROLE_USER,
            FirebaseConstants.ROLE_BROKER,
            FirebaseConstants.ROLE_ADMIN,
            FirebaseConstants.ROLE_SUPERADMIN,
            FirebaseConstants.ROLE_DEVELOPER -> role.trim().lowercase(Locale.US)
            else -> FirebaseConstants.ROLE_USER
        }
    }
}
