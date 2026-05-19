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
    suspend fun createProperty(propertyData: Map<String, Any?>): String
    suspend fun updateProperty(propertyId: String, propertyData: Map<String, Any?>)
    suspend fun uploadPropertyImages(
        propertyId: String,
        imageUris: List<Uri>,
        contentResolver: ContentResolver
    ): List<String>
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

    override suspend fun createProperty(propertyData: Map<String, Any?>): String {
        val docRef = firestore.collection(FirebaseConstants.COLLECTION_PROPERTIES).document()
        val filtered = propertyData.filterValues { it != null }
        docRef.set(filtered).await()
        return docRef.id
    }

    override suspend fun updateProperty(propertyId: String, propertyData: Map<String, Any?>) {
        val filtered = propertyData.toMutableMap()
        filtered[FirebaseConstants.FIELD_UPDATED_AT] = FieldValue.serverTimestamp()
        firestore.collection(FirebaseConstants.COLLECTION_PROPERTIES)
            .document(propertyId)
            .update(filtered)
            .await()
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
}
