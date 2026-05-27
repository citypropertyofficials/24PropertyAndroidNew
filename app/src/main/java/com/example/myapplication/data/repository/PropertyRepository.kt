package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.model.Property
import com.example.myapplication.utils.FirebaseConstants
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

interface PropertyRepository {
    suspend fun fetchPropertiesPage(
        limitCount: Int,
        cursor: DocumentSnapshot?,
        viewerRole: String
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
}

data class PropertyPageResult(
    val items: List<Property>,
    val nextCursor: DocumentSnapshot?,
    val hasMore: Boolean
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
        viewerRole: String
    ): PropertyPageResult {
        // Step 1: Query active properties (matches web's loadAllProperties)
        val snapshot = firestore
            .collection(FirebaseConstants.COLLECTION_PROPERTIES)
            .whereEqualTo(FirebaseConstants.FIELD_IS_ACTIVE, true)
            .get()
            .await()

        val allDocs = snapshot.documents

        // Step 2: Map ALL docs and filter by role (BEFORE paginating — matches web behavior)
        val visibleDocs = allDocs.mapNotNull { doc ->
            mapToProperty(doc)
        }.filter { property ->
            canAccessDeveloperOwnedContent(property.ownerRole, viewerRole)
        }

        // Step 3: Sort by createdAt desc client-side (newest first)
        val sortedVisible = visibleDocs.sortedByDescending { it.createdAt?.seconds ?: 0L }

        // Step 4: Paginate the filtered+sorted list
        val startIndex = if (cursor != null) {
            val cursorIndex = sortedVisible.indexOfFirst { it.id == cursor.id }
            if (cursorIndex >= 0) cursorIndex + 1 else 0
        } else 0

        val endIndex = minOf(startIndex + limitCount, sortedVisible.size)
        val pageItems = sortedVisible.subList(startIndex, endIndex)

        val hasMore = endIndex < sortedVisible.size

        // We can't use Property as cursor since it's not a DocumentSnapshot.
        // For client-side pagination, use the last visible item's ID for simple offset.
        val nextCursor = if (hasMore && pageItems.isNotEmpty()) {
            allDocs.find { it.id == pageItems.last().id }
        } else null

        Log.d(
            TAG,
            "pageResult: pageItems=${pageItems.size} hasMore=$hasMore nextCursor=${nextCursor?.id}"
        )

        return PropertyPageResult(
            items = pageItems,
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

    private fun str(data: Map<String, Any?>, key: String): String =
        data[key] as? String ?: ""

    private fun mapToProperty(doc: DocumentSnapshot): Property? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null

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
            status = str(data, FirebaseConstants.FIELD_STATUS),
            isActive = data[FirebaseConstants.FIELD_IS_ACTIVE] as? Boolean ?: true,
            ownerRole = str(data, FirebaseConstants.FIELD_OWNER_ROLE),
            uniqueId = str(data, FirebaseConstants.FIELD_UNIQUE_ID),
            createdAt = createdAt,
            updatedAt = updatedAt,

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
        val isDevOwner = ownerRole == FirebaseConstants.ROLE_DEVELOPER
        val isDevViewer = viewerRole == FirebaseConstants.ROLE_DEVELOPER
        return !isDevOwner || isDevViewer
    }
}
