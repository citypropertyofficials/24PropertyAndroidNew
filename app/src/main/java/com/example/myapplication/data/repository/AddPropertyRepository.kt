package com.example.myapplication.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.example.myapplication.utils.FirebaseConstants
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

interface AddPropertyRepository {
    suspend fun getUserPropertyCount(userId: String): Int
    suspend fun getProperty(propertyId: String): Map<String, Any?>?
    suspend fun createProperty(propertyData: Map<String, Any?>): String
    suspend fun updateProperty(propertyId: String, propertyData: Map<String, Any?>)
    suspend fun uploadPropertyImages(
        propertyId: String,
        imageUris: List<Uri>,
        contentResolver: ContentResolver
    ): List<String>
    suspend fun deletePropertyImages(imageUrls: List<String>)
}

class AddPropertyRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) : AddPropertyRepository {

    override suspend fun getUserPropertyCount(userId: String): Int {
        val snapshot = firestore.collection(FirebaseConstants.COLLECTION_PROPERTIES)
            .whereEqualTo(FirebaseConstants.FIELD_OWNER, userId)
            .whereEqualTo(FirebaseConstants.FIELD_IS_ACTIVE, true)
            .get()
            .await()
        return snapshot.size()
    }

    override suspend fun getProperty(propertyId: String): Map<String, Any?>? {
        val snapshot = firestore.collection(FirebaseConstants.COLLECTION_PROPERTIES)
            .document(propertyId)
            .get()
            .await()
        return snapshot.data
    }

    /**
     * Creates a new property document using a Firestore transaction to atomically assign
     * a sequential uniqueId from the counters/propertyId document.
     * Matches the web's addProperty() transaction logic exactly:
     *   - Reads counters/propertyId for the current ID
     *   - Computes the next ID (A000001, A000002 ... A999999, B000000 ...)
     *   - Sets property data + uniqueId + isActive + createdAt + updatedAt atomically
     *   - Updates the counter document
     */
    override suspend fun createProperty(propertyData: Map<String, Any?>): String {
        val counterRef = firestore
            .collection(FirebaseConstants.COLLECTION_COUNTERS)
            .document(FirebaseConstants.COUNTER_DOC_PROPERTY_ID)

        val newPropertyRef = firestore.collection(FirebaseConstants.COLLECTION_PROPERTIES).document()

        firestore.runTransaction { transaction ->
            val counterDoc = transaction.get(counterRef)

            val nextId = if (!counterDoc.exists()) {
                "A000000"
            } else {
                val currentId = counterDoc.getString(FirebaseConstants.COUNTER_FIELD_CURRENT_ID) ?: "A000000"
                getNextPropertyId(currentId)
            }

            val filtered = propertyData.filterValues { it != null }.toMutableMap<String, Any?>()
            filtered[FirebaseConstants.FIELD_UNIQUE_ID] = nextId
            filtered[FirebaseConstants.FIELD_IS_ACTIVE] = true
            filtered[FirebaseConstants.FIELD_CREATED_AT] = FieldValue.serverTimestamp()
            filtered[FirebaseConstants.FIELD_UPDATED_AT] = FieldValue.serverTimestamp()

            transaction.set(newPropertyRef, filtered)
            transaction.set(counterRef, mapOf(FirebaseConstants.COUNTER_FIELD_CURRENT_ID to nextId))
        }.await()

        return newPropertyRef.id
    }

    /**
     * Generates the next sequential property ID.
     * Examples: A000000 -> A000001, A999999 -> B000000
     * Matches the web's getNextId() function exactly.
     */
    private fun getNextPropertyId(currentId: String): String {
        if (currentId.length < 2) return "A000000"
        val letter = currentId[0]
        val number = currentId.substring(1).toIntOrNull() ?: 0
        return if (number < 999999) {
            "$letter${(number + 1).toString().padStart(6, '0')}"
        } else {
            val nextLetter = (letter.code + 1).toChar()
            "${nextLetter}000000"
        }
    }

    override suspend fun updateProperty(propertyId: String, propertyData: Map<String, Any?>) {
        val filtered = propertyData.toMutableMap()
        filtered[FirebaseConstants.FIELD_UPDATED_AT] = FieldValue.serverTimestamp()
        firestore.collection(FirebaseConstants.COLLECTION_PROPERTIES)
            .document(propertyId)
            .update(filtered)
            .await()
        
        syncInterestedPropertySnapshots(propertyId)
    }

    private suspend fun syncInterestedPropertySnapshots(propertyId: String) {
        try {
            val doc = firestore.collection(FirebaseConstants.COLLECTION_PROPERTIES)
                .document(propertyId)
                .get()
                .await()

            if (!doc.exists()) return
            val currentData = doc.data ?: return

            val interestedUsersSnapshot = firestore.collection(FirebaseConstants.COLLECTION_PROPERTIES)
                .document(propertyId)
                .collection("interestedUsers")
                .get()
                .await()

            if (!interestedUsersSnapshot.isEmpty) {
                val name = currentData[FirebaseConstants.FIELD_NAME] as? String ?: ""
                val propertyType = currentData[FirebaseConstants.FIELD_PROPERTY_TYPE] as? String ?: ""
                val listingType = currentData[FirebaseConstants.FIELD_LISTING_TYPE] as? String ?: ""
                val price = (currentData[FirebaseConstants.FIELD_PRICE] as? Number)?.toDouble() ?: 0.0
                val rent = (currentData[FirebaseConstants.FIELD_RENT] as? Number)?.toDouble() ?: price

                val images = (currentData[FirebaseConstants.FIELD_IMAGES] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val primaryImage = images.firstOrNull { it.isNotBlank() }

                val cityState = currentData[FirebaseConstants.FIELD_CITY_STATE] as? String ?: ""
                val city = cityState.substringBefore(",").trim()
                val state = cityState.substringAfter(",", "").trim()

                val owner = currentData[FirebaseConstants.FIELD_OWNER] as? String ?: ""
                val isActive = currentData[FirebaseConstants.FIELD_IS_ACTIVE] as? Boolean ?: true
                val status = currentData[FirebaseConstants.FIELD_STATUS] as? String ?: ""

                val propertySnapshotData = hashMapOf(
                    "propertyName" to name,
                    "propertyType" to propertyType,
                    "listingType" to listingType,
                    "price" to if (listingType == "rent") rent else price,
                    "primaryImage" to primaryImage,
                    "city" to city,
                    "state" to state,
                    "ownerId" to owner,
                    "isActive" to isActive,
                    "status" to status,
                    FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )

                val batch = firestore.batch()
                interestedUsersSnapshot.documents.forEach { userDoc ->
                    val interestedUserId = userDoc.id
                    val userInterestRef = firestore.collection(FirebaseConstants.COLLECTION_USERS)
                        .document(interestedUserId)
                        .collection("interestedProperties")
                        .document(propertyId)

                    batch.set(userInterestRef, propertySnapshotData, com.google.firebase.firestore.SetOptions.merge())
                }
                batch.commit().await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun uploadPropertyImages(
        propertyId: String,
        imageUris: List<Uri>,
        contentResolver: ContentResolver
    ): List<String> {
        return imageUris.mapIndexed { index, uri ->
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Unable to read selected image")
            val path = "${FirebaseConstants.STORAGE_PROPERTY_IMAGES}/$propertyId/${System.currentTimeMillis()}_${index}_${UUID.randomUUID()}.jpg"
            val ref = storage.reference.child(path)
            ref.putBytes(bytes).await()
            ref.downloadUrl.await().toString()
        }
    }

    override suspend fun deletePropertyImages(imageUrls: List<String>) {
        imageUrls.forEach { imageUrl ->
            runCatching {
                storage.getReferenceFromUrl(imageUrl).delete().await()
            }
        }
    }
}
