package com.example.myapplication.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.example.myapplication.utils.FirebaseConstants
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.functions.FirebaseFunctions
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
    private val storage: FirebaseStorage,
    private val functions: FirebaseFunctions
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
     * Creates a new property document by calling the respective Firebase Callable Cloud Function.
     */
    override suspend fun createProperty(propertyData: Map<String, Any?>): String {
        val propertyType = propertyData[FirebaseConstants.FIELD_PROPERTY_TYPE] as? String
            ?: error("Property type is required")

        val functionName = when (propertyType) {
            "residential" -> "addResidentialProperty"
            "commercial" -> "addCommercialProperty"
            "industrial" -> "addIndustrialProperty"
            "land" -> "addLandProperty"
            else -> error("Unsupported property type: $propertyType")
        }

        val result = functions.getHttpsCallable(functionName)
            .call(propertyData)
            .await()

        val resultData = result.data as? Map<*, *>
            ?: throw Exception("Invalid response from server")

        return resultData["propertyId"] as? String
            ?: throw Exception("Property ID not returned from server")
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
