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

    private fun mapToProperty(doc: DocumentSnapshot): Property? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null

        val images = (data[FirebaseConstants.FIELD_IMAGES] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val createdAt = data[FirebaseConstants.FIELD_CREATED_AT] as? Timestamp
        val updatedAt = data[FirebaseConstants.FIELD_UPDATED_AT] as? Timestamp

        return Property(
            id = doc.id,
            name = data[FirebaseConstants.FIELD_NAME] as? String ?: "",
            images = images,
            propertyType = data[FirebaseConstants.FIELD_PROPERTY_TYPE] as? String ?: "",
            listingType = data[FirebaseConstants.FIELD_LISTING_TYPE] as? String ?: "",
            price = (data[FirebaseConstants.FIELD_PRICE] as? Number)?.toDouble() ?: 0.0,
            rent = (data[FirebaseConstants.FIELD_RENT] as? Number)?.toDouble() ?: 0.0,
            location = data[FirebaseConstants.FIELD_LOCATION] as? String ?: "",
            cityState = data[FirebaseConstants.FIELD_CITY_STATE] as? String ?: "",
            isActive = data[FirebaseConstants.FIELD_IS_ACTIVE] as? Boolean ?: true,
            ownerRole = data[FirebaseConstants.FIELD_OWNER_ROLE] as? String ?: "",
            uniqueId = data[FirebaseConstants.FIELD_UNIQUE_ID] as? String ?: "",
            createdAt = createdAt,
            updatedAt = updatedAt
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
