package com.example.myapplication.data.repository

import com.example.myapplication.data.model.Property
import com.example.myapplication.utils.FirebaseConstants
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface FavoritesRepository {
    suspend fun fetchUserFavoritesPage(
        limitCount: Int,
        cursor: DocumentSnapshot?,
        viewerRole: String
    ): PropertyPageResult

    suspend fun addToFavorites(propertyId: String): Boolean
    suspend fun removeFromFavorites(propertyId: String): Boolean
    suspend fun isPropertyInFavorites(propertyId: String): Boolean
    fun observeFavoriteIds(): Flow<Set<String>>
}

class FavoritesRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : FavoritesRepository {

    override suspend fun fetchUserFavoritesPage(
        limitCount: Int,
        cursor: DocumentSnapshot?,
        viewerRole: String
    ): PropertyPageResult {
        val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")

        // Step 1: Get all favorite IDs ordered by addedAt
        val favoritesSnapshot = firestore
            .collection(FirebaseConstants.COLLECTION_USERS)
            .document(userId)
            .collection(FirebaseConstants.COLLECTION_FAVORITES)
            .orderBy(FirebaseConstants.FIELD_ADDED_AT, Query.Direction.DESCENDING)
            .get()
            .await()

        if (favoritesSnapshot.isEmpty) {
            return PropertyPageResult(items = emptyList(), nextCursor = null, hasMore = false)
        }

        val favoriteIds = favoritesSnapshot.documents.map { it.id }

        // Fetch property details in batches of 10
        val allProperties = mutableListOf<Property>()
        val batches = favoriteIds.chunked(10)

        for (batch in batches) {
            val propertiesSnapshot = firestore
                .collection(FirebaseConstants.COLLECTION_PROPERTIES)
                .whereIn(FieldPath.documentId(), batch)
                .whereEqualTo(FirebaseConstants.FIELD_IS_ACTIVE, true)
                .get()
                .await()

            propertiesSnapshot.documents.forEach { doc ->
                mapToProperty(doc)?.let { allProperties.add(it) }
            }
        }

        // Filter by role
        val filteredProperties = allProperties.filter { property ->
            canAccessDeveloperOwnedContent(property.ownerRole, viewerRole)
        }

        // Sort by the order in favorites
        val sortedVisible = filteredProperties.sortedBy { property ->
            favoriteIds.indexOf(property.id)
        }

        // Paginate the filtered+sorted list
        val startIndex = if (cursor != null) {
            val cursorIndex = sortedVisible.indexOfFirst { it.id == cursor.id }
            if (cursorIndex >= 0) cursorIndex + 1 else 0
        } else 0

        val endIndex = minOf(startIndex + limitCount, sortedVisible.size)
        val pageItems = sortedVisible.subList(startIndex, endIndex)

        val hasMore = endIndex < sortedVisible.size

        // For client-side pagination, we just use the last favorite doc snapshot as cursor, 
        // but here we are fetching all favorites up front. We can return the last property's corresponding favorite doc as cursor.
        val nextCursor = if (hasMore && pageItems.isNotEmpty()) {
            val lastPropertyId = pageItems.last().id
            favoritesSnapshot.documents.find { it.id == lastPropertyId }
        } else null

        return PropertyPageResult(
            items = pageItems,
            nextCursor = nextCursor,
            hasMore = hasMore
        )
    }

    override suspend fun addToFavorites(propertyId: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        try {
            val data = hashMapOf(
                FirebaseConstants.FIELD_ADDED_AT to Timestamp.now()
            )
            firestore
                .collection(FirebaseConstants.COLLECTION_USERS)
                .document(userId)
                .collection(FirebaseConstants.COLLECTION_FAVORITES)
                .document(propertyId)
                .set(data)
                .await()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    override suspend fun removeFromFavorites(propertyId: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        try {
            firestore
                .collection(FirebaseConstants.COLLECTION_USERS)
                .document(userId)
                .collection(FirebaseConstants.COLLECTION_FAVORITES)
                .document(propertyId)
                .delete()
                .await()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    override suspend fun isPropertyInFavorites(propertyId: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        try {
            val doc = firestore
                .collection(FirebaseConstants.COLLECTION_USERS)
                .document(userId)
                .collection(FirebaseConstants.COLLECTION_FAVORITES)
                .document(propertyId)
                .get()
                .await()
            return doc.exists()
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
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

    private fun canAccessDeveloperOwnedContent(ownerRole: String, viewerRole: String): Boolean {
        val isDevOwner = ownerRole == FirebaseConstants.ROLE_DEVELOPER
        val isDevViewer = viewerRole == FirebaseConstants.ROLE_DEVELOPER
        return !isDevOwner || isDevViewer
    }

    override fun observeFavoriteIds(): Flow<Set<String>> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(emptySet())
            close()
            return@callbackFlow
        }
        
        val listener = firestore
            .collection(FirebaseConstants.COLLECTION_USERS)
            .document(userId)
            .collection(FirebaseConstants.COLLECTION_FAVORITES)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val ids = snapshot?.documents?.map { it.id }?.toSet() ?: emptySet()
                trySend(ids)
            }
            
        awaitClose { listener.remove() }
    }
}
