package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.model.InterestedUser
import com.example.myapplication.utils.FirebaseConstants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Interface for the Interested Property feature.
 * All Firestore queries mirror the web's interestedService.js exactly.
 */
interface InterestedRepository {

    /**
     * Toggle interested state for a property.
     * Uses Firestore runTransaction matching web's toggleInterestedProperty().
     * @return true if now interested, false if interest was removed
     */
    suspend fun toggleInterestedProperty(userId: String, propertyId: String): Boolean

    /**
     * Real-time observation of the current user's interested property IDs.
     * Mirrors web's loadInterestedPropertyIds but as a live listener.
     */
    fun observeInterestedPropertyIds(): Flow<Set<String>>

    /**
     * One-shot read of interested property IDs for a user.
     * Matches web's loadInterestedPropertyIds().
     */
    suspend fun loadInterestedPropertyIds(userId: String): Set<String>

    /**
     * Fetch all interested users for a given property (owner view).
     * Matches web's getInterestedUsersForProperty().
     */
    suspend fun getInterestedUsersForProperty(propertyId: String): List<InterestedUser>

    /**
     * Sync interested property snapshots when a property is updated or deleted.
     * Reads fresh property data, then batch-updates all users/{interestedUserId}/interestedProperties/{propertyId} docs.
     * Matches web's syncInterestedPropertySnapshots().
     */
    suspend fun syncInterestedPropertySnapshots(propertyId: String)
}

class InterestedRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : InterestedRepository {

    private companion object {
        const val TAG = "InterestedRepo"
    }

    // ── toggleInterestedProperty ──────────────────────────────────────────────
    // Matches web's toggleInterestedProperty() in interestedService.js exactly.
    // Uses Firestore runTransaction to atomically:
    //   1. Read user doc, property doc, and existing interest doc
    //   2. Validate: user exists, property exists, owner ≠ user, property isActive
    //   3. If interest exists: delete both subcollection docs, decrement interestedCount
    //   4. If not: create both subcollection docs with snapshots, increment interestedCount
    override suspend fun toggleInterestedProperty(userId: String, propertyId: String): Boolean {
        require(userId.isNotBlank()) { "User ID is required" }
        require(propertyId.isNotBlank()) { "Property ID is required" }

        val userRef = firestore.collection(FirebaseConstants.COLLECTION_USERS).document(userId)
        val propertyRef = firestore.collection(FirebaseConstants.COLLECTION_PROPERTIES).document(propertyId)
        val userInterestRef = firestore.collection(FirebaseConstants.COLLECTION_USERS)
            .document(userId)
            .collection(FirebaseConstants.SUBCOLLECTION_INTERESTED_PROPERTIES)
            .document(propertyId)
        val propertyInterestRef = firestore.collection(FirebaseConstants.COLLECTION_PROPERTIES)
            .document(propertyId)
            .collection(FirebaseConstants.SUBCOLLECTION_INTERESTED_USERS)
            .document(userId)

        return firestore.runTransaction { transaction ->
            // Read all three documents atomically (matches web's Promise.all)
            val userSnapshot = transaction.get(userRef)
            val propertySnapshot = transaction.get(propertyRef)
            val userInterestSnapshot = transaction.get(userInterestRef)

            // Validation — matches web exactly
            if (!userSnapshot.exists()) {
                throw Exception("User profile not found")
            }
            if (!propertySnapshot.exists()) {
                throw Exception("Property not found")
            }

            val propertyData = propertySnapshot.data ?: throw Exception("Property data is empty")
            val ownerId = propertyData[FirebaseConstants.FIELD_OWNER] as? String
                ?: propertyData[FirebaseConstants.FIELD_USER_ID] as? String

            if (ownerId.isNullOrBlank()) {
                throw Exception("Property owner information is missing")
            }
            if (ownerId == userId) {
                throw Exception("You cannot mark your own property as interested")
            }
            if (propertyData[FirebaseConstants.FIELD_IS_ACTIVE] == false) {
                throw Exception("This property is no longer available")
            }

            val currentInterestedCount = (propertyData[FirebaseConstants.FIELD_INTERESTED_COUNT] as? Number)?.toInt() ?: 0

            if (userInterestSnapshot.exists()) {
                // ── REMOVE interest ──
                transaction.delete(userInterestRef)
                transaction.delete(propertyInterestRef)
                transaction.update(propertyRef, mapOf(
                    FirebaseConstants.FIELD_INTERESTED_COUNT to maxOf(currentInterestedCount - 1, 0),
                    FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                ))
                false // returning false = interest removed
            } else {
                // ── ADD interest ──
                val userData = userSnapshot.data ?: emptyMap()

                // Property snapshot (matches web's getPropertySnapshot)
                val images = (propertyData[FirebaseConstants.FIELD_IMAGES] as? List<*>)
                    ?.filterIsInstance<String>()
                val primaryImage = if (!images.isNullOrEmpty()) images[0] else null

                val propertyInterestData = hashMapOf<String, Any?>(
                    FirebaseConstants.FIELD_PROPERTY_ID to propertyId,
                    FirebaseConstants.FIELD_PROPERTY_NAME to (propertyData[FirebaseConstants.FIELD_NAME] ?: ""),
                    FirebaseConstants.FIELD_PROPERTY_TYPE to (propertyData[FirebaseConstants.FIELD_PROPERTY_TYPE] ?: ""),
                    FirebaseConstants.FIELD_LISTING_TYPE to (propertyData[FirebaseConstants.FIELD_LISTING_TYPE] ?: ""),
                    FirebaseConstants.FIELD_PRICE to propertyData[FirebaseConstants.FIELD_PRICE],
                    FirebaseConstants.FIELD_PRIMARY_IMAGE to primaryImage,
                    FirebaseConstants.FIELD_CITY to (propertyData[FirebaseConstants.FIELD_CITY] ?: ""),
                    FirebaseConstants.FIELD_STATE_FIELD to (propertyData[FirebaseConstants.FIELD_STATE_FIELD] ?: ""),
                    FirebaseConstants.FIELD_OWNER_ID to ownerId,
                    FirebaseConstants.FIELD_IS_ACTIVE to (propertyData[FirebaseConstants.FIELD_IS_ACTIVE] ?: true),
                    FirebaseConstants.FIELD_STATUS to (propertyData[FirebaseConstants.FIELD_STATUS]),
                    FirebaseConstants.FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                    FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )

                // Interested user snapshot (matches web's getInterestedUserSnapshot)
                val interestedUserData = hashMapOf<String, Any?>(
                    FirebaseConstants.FIELD_USER_ID to userId,
                    FirebaseConstants.FIELD_NAME to (userData[FirebaseConstants.FIELD_NAME] ?: ""),
                    FirebaseConstants.FIELD_EMAIL to (userData[FirebaseConstants.FIELD_EMAIL] ?: ""),
                    FirebaseConstants.FIELD_MOBILE to (userData[FirebaseConstants.FIELD_MOBILE] ?: ""),
                    FirebaseConstants.FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                    FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )

                transaction.set(userInterestRef, propertyInterestData)
                transaction.set(propertyInterestRef, interestedUserData)
                transaction.update(propertyRef, mapOf(
                    FirebaseConstants.FIELD_INTERESTED_COUNT to currentInterestedCount + 1,
                    FirebaseConstants.FIELD_LAST_INTERESTED_AT to FieldValue.serverTimestamp(),
                    FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                ))
                true // returning true = interest added
            }
        }.await()
    }

    // ── observeInterestedPropertyIds ──────────────────────────────────────────
    // Real-time listener on users/{uid}/interestedProperties.
    // Same pattern as FavoritesRepositoryImpl.observeFavoriteIds().
    override fun observeInterestedPropertyIds(): Flow<Set<String>> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(emptySet())
            close()
            return@callbackFlow
        }

        val listener = firestore
            .collection(FirebaseConstants.COLLECTION_USERS)
            .document(userId)
            .collection(FirebaseConstants.SUBCOLLECTION_INTERESTED_PROPERTIES)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing interested IDs", error)
                    return@addSnapshotListener
                }
                val ids = snapshot?.documents?.map { it.id }?.toSet() ?: emptySet()
                trySend(ids)
            }

        awaitClose { listener.remove() }
    }

    // ── loadInterestedPropertyIds ─────────────────────────────────────────────
    // One-shot read matching web's loadInterestedPropertyIds().
    override suspend fun loadInterestedPropertyIds(userId: String): Set<String> {
        if (userId.isBlank()) return emptySet()

        val snapshot = firestore
            .collection(FirebaseConstants.COLLECTION_USERS)
            .document(userId)
            .collection(FirebaseConstants.SUBCOLLECTION_INTERESTED_PROPERTIES)
            .get()
            .await()

        return snapshot.documents.map { it.id }.toSet()
    }

    // ── getInterestedUsersForProperty ─────────────────────────────────────────
    // Matches web's getInterestedUsersForProperty(): query ordered by createdAt desc.
    override suspend fun getInterestedUsersForProperty(propertyId: String): List<InterestedUser> {
        if (propertyId.isBlank()) return emptyList()

        val snapshot = firestore
            .collection(FirebaseConstants.COLLECTION_PROPERTIES)
            .document(propertyId)
            .collection(FirebaseConstants.SUBCOLLECTION_INTERESTED_USERS)
            .orderBy(FirebaseConstants.FIELD_CREATED_AT, Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.map { doc ->
            InterestedUser(
                id = doc.id,
                userId = doc.getString(FirebaseConstants.FIELD_USER_ID) ?: "",
                name = doc.getString(FirebaseConstants.FIELD_NAME) ?: "",
                email = doc.getString(FirebaseConstants.FIELD_EMAIL) ?: "",
                mobile = doc.getString(FirebaseConstants.FIELD_MOBILE) ?: ""
            )
        }
    }

    // ── syncInterestedPropertySnapshots ───────────────────────────────────────
    // Matches web's syncInterestedPropertySnapshots().
    // Reads fresh property data, then batch-updates all mirror docs in
    // users/{interestedUserId}/interestedProperties/{propertyId}.
    override suspend fun syncInterestedPropertySnapshots(propertyId: String) {
        if (propertyId.isBlank()) return

        try {
            // Read fresh property data
            val propertyDoc = firestore
                .collection(FirebaseConstants.COLLECTION_PROPERTIES)
                .document(propertyId)
                .get()
                .await()

            if (!propertyDoc.exists()) return

            val propertyData = propertyDoc.data ?: return

            // Get all interested users for this property
            val interestedUsersSnapshot = firestore
                .collection(FirebaseConstants.COLLECTION_PROPERTIES)
                .document(propertyId)
                .collection(FirebaseConstants.SUBCOLLECTION_INTERESTED_USERS)
                .get()
                .await()

            if (interestedUsersSnapshot.isEmpty) return

            // Build property snapshot (matches web's getPropertySnapshot)
            val images = (propertyData[FirebaseConstants.FIELD_IMAGES] as? List<*>)
                ?.filterIsInstance<String>()
            val primaryImage = if (!images.isNullOrEmpty()) images[0] else null

            val propertySnapshotData = hashMapOf<String, Any?>(
                FirebaseConstants.FIELD_PROPERTY_ID to propertyId,
                FirebaseConstants.FIELD_PROPERTY_NAME to (propertyData[FirebaseConstants.FIELD_NAME] ?: ""),
                FirebaseConstants.FIELD_PROPERTY_TYPE to (propertyData[FirebaseConstants.FIELD_PROPERTY_TYPE] ?: ""),
                FirebaseConstants.FIELD_LISTING_TYPE to (propertyData[FirebaseConstants.FIELD_LISTING_TYPE] ?: ""),
                FirebaseConstants.FIELD_PRICE to propertyData[FirebaseConstants.FIELD_PRICE],
                FirebaseConstants.FIELD_PRIMARY_IMAGE to primaryImage,
                FirebaseConstants.FIELD_CITY to (propertyData[FirebaseConstants.FIELD_CITY] ?: ""),
                FirebaseConstants.FIELD_STATE_FIELD to (propertyData[FirebaseConstants.FIELD_STATE_FIELD] ?: ""),
                FirebaseConstants.FIELD_OWNER_ID to (propertyData[FirebaseConstants.FIELD_OWNER]
                    ?: propertyData[FirebaseConstants.FIELD_USER_ID] ?: ""),
                FirebaseConstants.FIELD_IS_ACTIVE to (propertyData[FirebaseConstants.FIELD_IS_ACTIVE] ?: true),
                FirebaseConstants.FIELD_STATUS to propertyData[FirebaseConstants.FIELD_STATUS],
                FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
            )

            // Batch update in chunks of 400 (matches web's chunkDocuments)
            val chunks = interestedUsersSnapshot.documents.chunked(400)
            for (chunk in chunks) {
                val batch = firestore.batch()
                chunk.forEach { doc ->
                    val interestedUserId = doc.id
                    val userInterestRef = firestore
                        .collection(FirebaseConstants.COLLECTION_USERS)
                        .document(interestedUserId)
                        .collection(FirebaseConstants.SUBCOLLECTION_INTERESTED_PROPERTIES)
                        .document(propertyId)
                    batch.set(userInterestRef, propertySnapshotData, SetOptions.merge())
                }
                batch.commit().await()
            }

            Log.d(TAG, "Synced interested property snapshots for property $propertyId (${interestedUsersSnapshot.size()} users)")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing interested property snapshots for $propertyId", e)
        }
    }
}
