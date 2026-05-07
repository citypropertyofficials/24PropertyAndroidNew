package com.example.myapplication.data.repository

import com.example.myapplication.data.model.Property
import com.example.myapplication.utils.FirebaseConstants
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

interface PropertyRepository {
    suspend fun fetchPropertiesPage(
        limitCount: Int,
        cursor: DocumentSnapshot?,
        viewerRole: String
    ): PropertyPageResult

    /** Fetch a single property by ID for the details screen. Returns null if not found. */
    suspend fun fetchPropertyById(propertyId: String): Property?

    /** Fetch the mobile number of the property owner. Returns null if not found. */
    suspend fun getOwnerMobileNumber(ownerUid: String): String?
}

data class PropertyPageResult(
    val items: List<Property>,
    val nextCursor: DocumentSnapshot?,
    val hasMore: Boolean
)

class PropertyRepositoryImpl(
    private val firestore: FirebaseFirestore
) : PropertyRepository {

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

        return PropertyPageResult(
            items = pageItems,
            nextCursor = nextCursor,
            hasMore = hasMore
        )
    }

    override suspend fun fetchPropertyById(propertyId: String): Property? {
        return try {
            val doc = firestore
                .collection(FirebaseConstants.COLLECTION_PROPERTIES)
                .document(propertyId)
                .get()
                .await()
            if (doc.exists()) mapToProperty(doc) else null
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

    private fun str(data: Map<String, Any?>, key: String): String =
        data[key] as? String ?: ""

    private fun mapToProperty(doc: DocumentSnapshot): Property? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null

        val images = (data[FirebaseConstants.FIELD_IMAGES] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val amenities = (data["amenities"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val createdAt = data[FirebaseConstants.FIELD_CREATED_AT] as? Timestamp
        val updatedAt = data[FirebaseConstants.FIELD_UPDATED_AT] as? Timestamp

        return Property(
            id = doc.id,
            name = str(data, FirebaseConstants.FIELD_NAME),
            images = images,
            propertyType = str(data, FirebaseConstants.FIELD_PROPERTY_TYPE),
            listingType = str(data, FirebaseConstants.FIELD_LISTING_TYPE),
            price = (data[FirebaseConstants.FIELD_PRICE] as? Number)?.toDouble() ?: 0.0,
            rent = (data[FirebaseConstants.FIELD_RENT] as? Number)?.toDouble() ?: 0.0,
            location = str(data, FirebaseConstants.FIELD_LOCATION),
            cityState = str(data, FirebaseConstants.FIELD_CITY_STATE),
            isActive = data[FirebaseConstants.FIELD_IS_ACTIVE] as? Boolean ?: true,
            ownerRole = str(data, FirebaseConstants.FIELD_OWNER_ROLE),
            uniqueId = str(data, FirebaseConstants.FIELD_UNIQUE_ID),
            createdAt = createdAt,
            updatedAt = updatedAt,

            // Owner
            owner = str(data, FirebaseConstants.FIELD_OWNER),

            // Description & Amenities
            description = str(data, "description"),
            amenities = amenities,

            // Location sub-fields
            buildingName = str(data, "buildingName"),
            locality = str(data, "locality"),
            zoneType = str(data, "zoneType"),
            locationHub = str(data, "locationHub"),

            // Type & Category
            residentialType = str(data, "residentialType"),
            bhkConfig = str(data, "bhkConfig"),
            commercialType = str(data, "commercialType"),
            industrialType = str(data, "industrialType"),
            landType = str(data, "landType"),

            // Status & Availability
            possessionStatus = str(data, "possessionStatus"),
            availableFrom = str(data, "availableFrom"),

            // Property & Legal
            propertyCondition = str(data, "propertyCondition"),
            ownership = str(data, "ownership"),
            plotArea = str(data, "plotArea"),
            builtUpArea = str(data, "builtUpArea"),
            carpetArea = str(data, "carpetArea"),
            totalConstructionArea = str(data, "totalConstructionArea"),
            frontage = str(data, "frontage"),
            roadAccess = str(data, "roadAccess"),

            // Residential
            propertyAge = str(data, "propertyAge"),
            floorNumber = str(data, "floorNumber"),
            bathrooms = str(data, "bathrooms"),
            balconies = str(data, "balconies"),
            furnishing = str(data, "furnishing"),
            coveredParking = str(data, "coveredParking"),
            openParking = str(data, "openParking"),
            parkingCharges = str(data, "parkingCharges"),
            tenantType = str(data, "tenantType"),
            petFriendly = str(data, "petFriendly"),
            maintenanceCharges = str(data, "maintenanceCharges"),
            servantRoom = str(data, "servantRoom"),
            amenitiesText = str(data, "amenitiesText"),
            residentsCount = str(data, "residentsCount"),

            // Commercial / Industrial shared
            parkingType = str(data, "parkingType"),
            washroomType = str(data, "washroomType"),

            // Industrial
            shedHeight = str(data, "shedHeight"),
            shedSideWallHeight = str(data, "shedSideWallHeight"),
            plotDimensions = str(data, "plotDimensions"),
            shedBuiltUpArea = str(data, "shedBuiltUpArea"),
            builtUpConstructionArea = str(data, "builtUpConstructionArea"),
            electricityLoad = str(data, "electricityLoad"),
            waterAvailable = str(data, "waterAvailable"),
            preLeased = str(data, "preLeased"),
            preRented = str(data, "preRented"),

            // Land
            areaAcres = str(data, "areaAcres"),
            plotLength = str(data, "plotLength"),
            plotBreadth = str(data, "plotBreadth"),
            landFacing = str(data, "landFacing"),
            roadWidth = str(data, "roadWidth"),
            landStatus = str(data, "landStatus"),

            // Floors & Elevation
            yourFloor = str(data, "yourFloor"),
            totalFloors = str(data, "totalFloors"),
            staircases = str(data, "staircases"),
            passengerLift = str(data, "passengerLift"),
            serviceLift = str(data, "serviceLift"),

            // Facing
            facing = str(data, "facing"),
            rearFacing = str(data, "rearFacing"),
            roadFacing = str(data, "roadFacing"),

            // Lease & Financials
            rentNegotiable = str(data, "rentNegotiable"),
            securityDeposit = str(data, "securityDeposit"),
            rentIncrease = str(data, "rentIncrease"),
            lockInPeriod = str(data, "lockInPeriod"),

            // Charges & Inclusions
            dampUpsIncluded = str(data, "dampUpsIncluded"),
            electricityIncluded = str(data, "electricityIncluded"),
            waterChargesIncluded = str(data, "waterChargesIncluded"),
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
