package com.example.myapplication.data.repository

import com.example.myapplication.data.model.LocationPreference
import com.example.myapplication.data.model.User
import com.example.myapplication.utils.FirebaseConstants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import android.util.Log

class AuthRepositoryImpl(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val interestedRepository: InterestedRepository
) : AuthRepository {

    private val superAdminEmails = listOf(
        "mtvhustlevideos@gmail.com",
        "someshbamayya@gmail.com"
    )

    override fun isUserSignedIn(): Boolean = auth.currentUser != null

    override fun getCurrentUserId(): String? = auth.currentUser?.uid

    override fun getCurrentUserEmail(): String? = auth.currentUser?.email

    override suspend fun getCurrentUserRole(): String? {
        return try {
            val userId = auth.currentUser?.uid ?: return null
            val docSnap = firestore.collection(FirebaseConstants.COLLECTION_USERS)
                .document(userId)
                .get()
                .await()
            val data = docSnap.data ?: return FirebaseConstants.ROLE_USER
            val role = data[FirebaseConstants.FIELD_ROLE] as? String ?: FirebaseConstants.ROLE_USER

            val email = auth.currentUser?.email
            if (
                superAdminEmails.contains(email) &&
                !hasRequiredRole(role, FirebaseConstants.ROLE_SUPERADMIN)
            ) {
                return FirebaseConstants.ROLE_SUPERADMIN
            }
            role
        } catch (e: Exception) {
            FirebaseConstants.ROLE_USER
        }
    }

    override suspend fun signInWithGoogle(idToken: String): AuthResult<User> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user
                ?: return AuthResult.Error("No user returned from authentication")

            val userDocRef = firestore.collection(FirebaseConstants.COLLECTION_USERS)
                .document(firebaseUser.uid)

            val docSnap = userDocRef.get().await()

            val userRole = if (superAdminEmails.contains(firebaseUser.email)) {
                FirebaseConstants.ROLE_SUPERADMIN
            } else {
                FirebaseConstants.ROLE_USER
            }

            val maxProperties = when (userRole) {
                FirebaseConstants.ROLE_USER -> FirebaseConstants.PROPERTY_LIMIT_USER
                else -> null
            }

            if (!docSnap.exists()) {
                val newUser = User(
                    uid = firebaseUser.uid,
                    name = firebaseUser.displayName ?: "",
                    email = firebaseUser.email ?: "",
                    photoUrl = firebaseUser.photoUrl?.toString() ?: "",
                    mobile = "",
                    role = userRole,
                    maxPropertiesAllowed = maxProperties
                )

                val userMap = mutableMapOf<String, Any>(
                    FirebaseConstants.FIELD_NAME to newUser.name,
                    FirebaseConstants.FIELD_EMAIL to newUser.email,
                    FirebaseConstants.FIELD_PHOTO_URL to newUser.photoUrl,
                    FirebaseConstants.FIELD_MOBILE to newUser.mobile,
                    FirebaseConstants.FIELD_ROLE to newUser.role
                )
                maxProperties?.let { userMap[FirebaseConstants.FIELD_MAX_PROPERTIES_ALLOWED] = it }

                userDocRef.set(userMap).await()
                AuthResult.Success(newUser)
            } else {
                val data = docSnap.data ?: return AuthResult.Error("User data is empty")

                val shouldBeSuperAdmin = superAdminEmails.contains(firebaseUser.email)
                val currentRole = data[FirebaseConstants.FIELD_ROLE] as? String ?: FirebaseConstants.ROLE_USER
                val finalRole = if (
                    shouldBeSuperAdmin &&
                    !hasRequiredRole(currentRole, FirebaseConstants.ROLE_SUPERADMIN)
                ) {
                    FirebaseConstants.ROLE_SUPERADMIN
                } else {
                    currentRole
                }

                if (finalRole != currentRole) {
                    userDocRef.update(
                        mapOf(
                            FirebaseConstants.FIELD_ROLE to finalRole
                        )
                    ).await()
                    // Remove maxPropertiesAllowed for unlimited roles
                    userDocRef.update(
                        mapOf(FirebaseConstants.FIELD_MAX_PROPERTIES_ALLOWED to com.google.firebase.firestore.FieldValue.delete())
                    ).await()
                }

                // Sync property limit
                syncUserPropertyLimit(firebaseUser.uid, finalRole)

                val user = User(
                    uid = firebaseUser.uid,
                    name = data[FirebaseConstants.FIELD_NAME] as? String ?: "",
                    email = data[FirebaseConstants.FIELD_EMAIL] as? String ?: "",
                    photoUrl = data[FirebaseConstants.FIELD_PHOTO_URL] as? String ?: "",
                    mobile = data[FirebaseConstants.FIELD_MOBILE] as? String ?: "",
                    role = finalRole,
                    maxPropertiesAllowed = (data[FirebaseConstants.FIELD_MAX_PROPERTIES_ALLOWED] as? Long)?.toInt()
                        ?: if (finalRole == FirebaseConstants.ROLE_USER) FirebaseConstants.PROPERTY_LIMIT_USER else null
                )
                AuthResult.Success(user)
            }
        } catch (e: Exception) {
            AuthResult.Error("Authentication failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    override suspend fun signOut(): AuthResult<Unit> {
        return try {
            auth.signOut()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Error("Sign out failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    override suspend fun getUserData(userId: String): AuthResult<User> {
        return try {
            val docSnap = firestore.collection(FirebaseConstants.COLLECTION_USERS)
                .document(userId)
                .get()
                .await()

            if (!docSnap.exists()) {
                return AuthResult.Error("User not found")
            }

            val data = docSnap.data ?: return AuthResult.Error("User data is empty")
            val role = data[FirebaseConstants.FIELD_ROLE] as? String ?: FirebaseConstants.ROLE_USER
            val expectedLimit = if (role == FirebaseConstants.ROLE_USER) FirebaseConstants.PROPERTY_LIMIT_USER else null

            // Sync user property limit
            syncUserPropertyLimit(userId, role)

            val locations = (data[FirebaseConstants.FIELD_PREFERRED_LOCATIONS] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val locationPreferenceData = data[FirebaseConstants.FIELD_LOCATION_PREFERENCE] as? Map<*, *>
            val locationPreference = locationPreferenceData?.let {
                LocationPreference(
                    stateCode = it["stateCode"] as? String ?: "",
                    stateName = it["stateName"] as? String ?: "",
                    districtName = it["districtName"] as? String ?: ""
                )
            }

            val user = User(
                uid = userId,
                name = data[FirebaseConstants.FIELD_NAME] as? String ?: "",
                email = data[FirebaseConstants.FIELD_EMAIL] as? String ?: "",
                photoUrl = data[FirebaseConstants.FIELD_PHOTO_URL] as? String ?: "",
                mobile = data[FirebaseConstants.FIELD_MOBILE] as? String ?: "",
                role = role,
                maxPropertiesAllowed = (data[FirebaseConstants.FIELD_MAX_PROPERTIES_ALLOWED] as? Long)?.toInt() ?: expectedLimit,
                preferredLocations = locations,
                locationPreference = locationPreference
            )
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Error("Failed to fetch user data: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun hasRequiredRole(userRole: String?, requiredRole: String): Boolean {
        return roleLevel(userRole) >= roleLevel(requiredRole)
    }

    private fun roleLevel(role: String?): Int {
        return when (role?.trim()?.lowercase()) {
            FirebaseConstants.ROLE_USER -> 0
            FirebaseConstants.ROLE_BROKER -> 1
            FirebaseConstants.ROLE_ADMIN -> 2
            FirebaseConstants.ROLE_SUPERADMIN -> 3
            FirebaseConstants.ROLE_DEVELOPER -> 4
            else -> 0
        }
    }

    override suspend fun updateUserProfile(
        userId: String,
        profileData: Map<String, Any>
    ): AuthResult<Unit> {
        return try {
            val userRef = firestore.collection(FirebaseConstants.COLLECTION_USERS)
                .document(userId)
            val updateMap = profileData.toMutableMap()
            updateMap[FirebaseConstants.FIELD_UPDATED_AT] = FieldValue.serverTimestamp()
            userRef.set(updateMap, com.google.firebase.firestore.SetOptions.merge()).await()

            // Cascade updates to other denormalized snapshots
            val name = profileData[FirebaseConstants.FIELD_NAME] as? String
            val email = profileData[FirebaseConstants.FIELD_EMAIL] as? String
            val mobile = profileData[FirebaseConstants.FIELD_MOBILE] as? String
            syncUserProfileSnapshots(userId, name, email, mobile)

            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Error("Failed to update profile: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    override suspend fun submitRoleRequest(
        userId: String,
        requestedRole: String
    ): AuthResult<String> {
        return try {
            val userDoc = firestore.collection(FirebaseConstants.COLLECTION_USERS)
                .document(userId)
                .get()
                .await()

            val userName = userDoc.getString(FirebaseConstants.FIELD_NAME) ?: "Unknown User"
            val userEmail = userDoc.getString(FirebaseConstants.FIELD_EMAIL) ?: "unknown@example.com"
            val userMobile = userDoc.getString(FirebaseConstants.FIELD_MOBILE) ?: ""
            val userRole = userDoc.getString(FirebaseConstants.FIELD_ROLE) ?: "user"

            val requestData = hashMapOf(
                FirebaseConstants.FIELD_USER_ID to userId,
                FirebaseConstants.FIELD_REQUESTED_ROLE to requestedRole,
                FirebaseConstants.FIELD_STATUS to FirebaseConstants.STATUS_PENDING,
                "userName" to userName,
                "userEmail" to userEmail,
                "userMobile" to userMobile,
                FirebaseConstants.FIELD_USER_ROLE to userRole,
                FirebaseConstants.FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
            )
            val docRef = firestore.collection(FirebaseConstants.COLLECTION_ROLE_REQUESTS)
                .add(requestData)
                .await()
            AuthResult.Success(docRef.id)
        } catch (e: Exception) {
            AuthResult.Error("Failed to submit role request: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    override suspend fun fetchPendingRoleRequests(userId: String): AuthResult<List<String>> {
        return try {
            val q = firestore.collection(FirebaseConstants.COLLECTION_ROLE_REQUESTS)
                .whereEqualTo(FirebaseConstants.FIELD_USER_ID, userId)
                .whereEqualTo(FirebaseConstants.FIELD_STATUS, FirebaseConstants.STATUS_PENDING)
                .get()
                .await()
            val roles = q.documents.mapNotNull { it.getString(FirebaseConstants.FIELD_REQUESTED_ROLE) }
            AuthResult.Success(roles)
        } catch (e: Exception) {
            AuthResult.Error("Failed to fetch role requests: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    override suspend fun checkExistingRoleRequest(
        userId: String,
        requestedRole: String
    ): AuthResult<Boolean> {
        return try {
            val q = firestore.collection(FirebaseConstants.COLLECTION_ROLE_REQUESTS)
                .whereEqualTo(FirebaseConstants.FIELD_USER_ID, userId)
                .whereEqualTo(FirebaseConstants.FIELD_REQUESTED_ROLE, requestedRole)
                .whereEqualTo(FirebaseConstants.FIELD_STATUS, FirebaseConstants.STATUS_PENDING)
                .get()
                .await()
            AuthResult.Success(!q.isEmpty)
        } catch (e: Exception) {
            AuthResult.Error("Failed to check role request: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private suspend fun syncUserProfileSnapshots(
        userId: String,
        name: String?,
        email: String?,
        mobile: String?
    ) {
        try {
            // 1. Sync Role Requests
            val roleSnapshot = firestore.collection(FirebaseConstants.COLLECTION_ROLE_REQUESTS)
                .whereEqualTo(FirebaseConstants.FIELD_USER_ID, userId)
                .whereEqualTo(FirebaseConstants.FIELD_STATUS, FirebaseConstants.STATUS_PENDING)
                .get()
                .await()

            if (!roleSnapshot.isEmpty) {
                val batch = firestore.batch()
                roleSnapshot.documents.forEach { doc ->
                    val updateMap = mutableMapOf<String, Any>(
                        FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                    )
                    name?.let { updateMap["userName"] = it }
                    email?.let { updateMap["userEmail"] = it }
                    mobile?.let { updateMap["userMobile"] = it }
                    batch.update(doc.reference, updateMap)
                }
                batch.commit().await()
            }

            // 2. Sync Property Requests
            val propRequestsSnapshot = firestore.collection(FirebaseConstants.COLLECTION_PROPERTY_REQUESTS)
                .whereEqualTo(FirebaseConstants.FIELD_USER_ID, userId)
                .whereEqualTo(FirebaseConstants.FIELD_STATUS, FirebaseConstants.STATUS_PENDING)
                .get()
                .await()

            if (!propRequestsSnapshot.isEmpty) {
                val batch = firestore.batch()
                propRequestsSnapshot.documents.forEach { doc ->
                    val updateMap = mutableMapOf<String, Any>(
                        FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                    )
                    name?.let { updateMap["userName"] = it }
                    email?.let { updateMap["userEmail"] = it }
                    mobile?.let { updateMap["userMobile"] = it }
                    batch.update(doc.reference, updateMap)
                }
                batch.commit().await()
            }

            // 3. Sync Owned Properties
            val propertiesSnapshot = firestore.collection(FirebaseConstants.COLLECTION_PROPERTIES)
                .whereEqualTo(FirebaseConstants.FIELD_OWNER, userId)
                .get()
                .await()

            if (!propertiesSnapshot.isEmpty) {
                val batch = firestore.batch()
                propertiesSnapshot.documents.forEach { doc ->
                    val updateMap = mutableMapOf<String, Any>(
                        FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                    )
                    name?.let { updateMap[FirebaseConstants.FIELD_OWNER_NAME] = it }
                    email?.let { updateMap[FirebaseConstants.FIELD_OWNER_EMAIL] = it }
                    mobile?.let { updateMap[FirebaseConstants.FIELD_OWNER_MOBILE] = it }
                    batch.update(doc.reference, updateMap)
                }
                batch.commit().await()
            }

            // 3b. Cascade: sync interested property snapshots for each owned property
            // Matches web's myPropertiesService.js syncPropertiesOwnerProfileSnapshots L368-376
            if (!propertiesSnapshot.isEmpty) {
                for (doc in propertiesSnapshot.documents) {
                    try {
                        interestedRepository.syncInterestedPropertySnapshots(doc.id)
                    } catch (e: Exception) {
                        Log.e("AuthRepo", "Error cascading interested sync for property ${doc.id}", e)
                    }
                }
            }

            // 4. Sync Interested Users snapshots (collection group query group sync)
            val interestedUsersSnapshot = firestore.collectionGroup("interestedUsers")
                .whereEqualTo("userId", userId)
                .get()
                .await()

            if (!interestedUsersSnapshot.isEmpty) {
                val batch = firestore.batch()
                interestedUsersSnapshot.documents.forEach { doc ->
                    val updateMap = mutableMapOf<String, Any>(
                        FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                    )
                    name?.let { updateMap["name"] = it }
                    email?.let { updateMap["email"] = it }
                    mobile?.let { updateMap["mobile"] = it }
                    batch.update(doc.reference, updateMap)
                }
                batch.commit().await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun observeAuthState(): Flow<Boolean> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser != null)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun deleteUserAccount(userId: String): AuthResult<Unit> {
        return try {
            // 1. Delete interested users/properties relation where this user is interested
            removeInterestedUserData(userId)

            // 2. Delete all Properties owned by user
            val propertiesSnapshot = firestore.collection(FirebaseConstants.COLLECTION_PROPERTIES)
                .whereEqualTo(FirebaseConstants.FIELD_OWNER, userId)
                .get()
                .await()

            // Remove property interested data and delete documents
            if (!propertiesSnapshot.isEmpty) {
                propertiesSnapshot.documents.forEach { doc ->
                    removePropertyInterestedData(doc.id)
                    doc.reference.delete().await()
                }
            }

            // 3. Delete all Auctions created by user
            val auctionsSnapshot = firestore.collection(FirebaseConstants.COLLECTION_AUCTIONS)
                .whereEqualTo(FirebaseConstants.FIELD_OWNER_ID, userId)
                .get()
                .await()
            val auctionsBatch = firestore.batch()
            auctionsSnapshot.documents.forEach { doc ->
                auctionsBatch.delete(doc.reference)
            }
            if (!auctionsSnapshot.isEmpty) {
                auctionsBatch.commit().await()
            }

            // 4. Delete pending/existing Role Requests
            val requestsSnapshot = firestore.collection(FirebaseConstants.COLLECTION_ROLE_REQUESTS)
                .whereEqualTo(FirebaseConstants.FIELD_USER_ID, userId)
                .get()
                .await()
            val requestsBatch = firestore.batch()
            requestsSnapshot.documents.forEach { doc ->
                requestsBatch.delete(doc.reference)
            }
            if (!requestsSnapshot.isEmpty) {
                requestsBatch.commit().await()
            }

            // 5. Delete Profile Image from Storage
            try {
                val imageRef = com.google.firebase.storage.FirebaseStorage.getInstance()
                    .getReference("profile-images/$userId.jpg")
                imageRef.delete().await()
            } catch (e: Exception) {
                // Ignore storage/object-not-found errors
            }

            // 6. Delete User Profile Document
            firestore.collection(FirebaseConstants.COLLECTION_USERS)
                .document(userId)
                .delete()
                .await()

            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Error("Failed to delete account: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private suspend fun removePropertyInterestedData(propertyId: String) {
        try {
            val interestedUsersSnapshot = firestore.collection(FirebaseConstants.COLLECTION_PROPERTIES)
                .document(propertyId)
                .collection("interestedUsers")
                .get()
                .await()

            if (!interestedUsersSnapshot.isEmpty) {
                val documents = interestedUsersSnapshot.documents
                val chunks = documents.chunked(400)
                for (chunk in chunks) {
                    val batch = firestore.batch()
                    chunk.forEach { doc ->
                        val interestedUserId = doc.id
                        batch.delete(doc.reference)
                        batch.delete(
                            firestore.collection(FirebaseConstants.COLLECTION_USERS)
                                .document(interestedUserId)
                                .collection("interestedProperties")
                                .document(propertyId)
                        )
                    }
                    batch.commit().await()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun removeInterestedUserData(userId: String) {
        try {
            val userInterestedSnapshot = firestore.collection(FirebaseConstants.COLLECTION_USERS)
                .document(userId)
                .collection("interestedProperties")
                .get()
                .await()

            if (!userInterestedSnapshot.isEmpty) {
                val documents = userInterestedSnapshot.documents
                val chunks = documents.chunked(400)
                for (chunk in chunks) {
                    // Fetch all parent properties to adjust interestedCount
                    val propertyRefs = chunk.map {
                        firestore.collection(FirebaseConstants.COLLECTION_PROPERTIES).document(it.id)
                    }
                    val propertySnapshots = propertyRefs.map { it.get().await() }

                    val batch = firestore.batch()
                    chunk.forEachIndexed { index, doc ->
                        val propertyId = doc.id
                        val propertySnapshot = propertySnapshots[index]
                        val currentInterestedCount = if (propertySnapshot.exists()) {
                            (propertySnapshot.getLong("interestedCount") ?: 0L).toInt()
                        } else {
                            0
                        }

                        batch.delete(doc.reference)
                        batch.delete(
                            firestore.collection(FirebaseConstants.COLLECTION_PROPERTIES)
                                .document(propertyId)
                                .collection("interestedUsers")
                                .document(userId)
                        )

                        if (propertySnapshot.exists()) {
                            batch.set(
                                firestore.collection(FirebaseConstants.COLLECTION_PROPERTIES)
                                    .document(propertyId),
                                mapOf(
                                    "interestedCount" to maxOf(currentInterestedCount - 1, 0),
                                    FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                                ),
                                com.google.firebase.firestore.SetOptions.merge()
                            )
                        }
                    }
                    batch.commit().await()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun syncUserPropertyLimit(userId: String, role: String) {
        try {
            val expectedLimit = if (role == FirebaseConstants.ROLE_USER) FirebaseConstants.PROPERTY_LIMIT_USER else null
            val userDocRef = firestore.collection(FirebaseConstants.COLLECTION_USERS).document(userId)
            val docSnap = userDocRef.get().await()
            if (!docSnap.exists()) return

            val hasField = docSnap.contains(FirebaseConstants.FIELD_MAX_PROPERTIES_ALLOWED)
            val currentLimit = if (hasField) {
                (docSnap.get(FirebaseConstants.FIELD_MAX_PROPERTIES_ALLOWED) as? Long)?.toInt()
            } else {
                null
            }

            val needsUpdate = if (role == FirebaseConstants.ROLE_USER) {
                !hasField || currentLimit != expectedLimit
            } else {
                hasField && currentLimit != null
            }

            if (needsUpdate) {
                val updateMap = mutableMapOf<String, Any?>(
                    FirebaseConstants.FIELD_MAX_PROPERTIES_ALLOWED to expectedLimit,
                    FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
                userDocRef.update(updateMap).await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
