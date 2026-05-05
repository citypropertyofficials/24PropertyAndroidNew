package com.example.myapplication.data.repository

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

class AuthRepositoryImpl(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
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

            // Superadmin override
            val email = auth.currentUser?.email
            if (superAdminEmails.contains(email)) {
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

                if (shouldBeSuperAdmin && currentRole != FirebaseConstants.ROLE_SUPERADMIN) {
                    userDocRef.update(
                        mapOf(
                            FirebaseConstants.FIELD_ROLE to FirebaseConstants.ROLE_SUPERADMIN
                        )
                    ).await()
                    // Remove maxPropertiesAllowed for unlimited roles
                    userDocRef.update(
                        mapOf(FirebaseConstants.FIELD_MAX_PROPERTIES_ALLOWED to com.google.firebase.firestore.FieldValue.delete())
                    ).await()
                }

                val user = User(
                    uid = firebaseUser.uid,
                    name = data[FirebaseConstants.FIELD_NAME] as? String ?: "",
                    email = data[FirebaseConstants.FIELD_EMAIL] as? String ?: "",
                    photoUrl = data[FirebaseConstants.FIELD_PHOTO_URL] as? String ?: "",
                    mobile = data[FirebaseConstants.FIELD_MOBILE] as? String ?: "",
                    role = if (shouldBeSuperAdmin) FirebaseConstants.ROLE_SUPERADMIN else currentRole,
                    maxPropertiesAllowed = (data[FirebaseConstants.FIELD_MAX_PROPERTIES_ALLOWED] as? Long)?.toInt()
                        ?: if (currentRole == FirebaseConstants.ROLE_USER) FirebaseConstants.PROPERTY_LIMIT_USER else null
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

            val locations = (data[FirebaseConstants.FIELD_PREFERRED_LOCATIONS] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

            val user = User(
                uid = userId,
                name = data[FirebaseConstants.FIELD_NAME] as? String ?: "",
                email = data[FirebaseConstants.FIELD_EMAIL] as? String ?: "",
                photoUrl = data[FirebaseConstants.FIELD_PHOTO_URL] as? String ?: "",
                mobile = data[FirebaseConstants.FIELD_MOBILE] as? String ?: "",
                role = data[FirebaseConstants.FIELD_ROLE] as? String ?: FirebaseConstants.ROLE_USER,
                maxPropertiesAllowed = (data[FirebaseConstants.FIELD_MAX_PROPERTIES_ALLOWED] as? Long)?.toInt(),
                preferredLocations = locations
            )
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Error("Failed to fetch user data: ${e.localizedMessage ?: "Unknown error"}")
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
            val requestData = hashMapOf(
                FirebaseConstants.FIELD_USER_ID to userId,
                FirebaseConstants.FIELD_REQUESTED_ROLE to requestedRole,
                FirebaseConstants.FIELD_STATUS to FirebaseConstants.STATUS_PENDING,
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

    override fun observeAuthState(): Flow<Boolean> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser != null)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }
}
