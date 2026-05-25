package com.example.myapplication.data.repository

import com.example.myapplication.data.model.DashboardStats
import com.example.myapplication.data.model.PropertyRequest
import com.example.myapplication.data.model.RoleRequest
import com.example.myapplication.data.model.User
import com.example.myapplication.utils.FirebaseConstants
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

class DashboardRepositoryImpl(
    private val firestore: FirebaseFirestore
) : DashboardRepository {

    override suspend fun getDashboardStats(viewerRole: String): DashboardResult<DashboardStats> {
        return try {
            val usersSnapshot = firestore.collection(FirebaseConstants.COLLECTION_USERS).get().await()
            val allUsers = usersSnapshot.toObjects(User::class.java).mapIndexed { index, u ->
                u.copy(uid = usersSnapshot.documents[index].id)
            }

            // Filter users visible to current viewer
            val visibleUsers = allUsers.filter { canViewRoleRequestUser(it.role, viewerRole) }
            val totalUsers = visibleUsers.size
            val activeUsers = visibleUsers.count { !it.blocked }
            val blockedUsers = visibleUsers.count { it.blocked }

            val roleStats = mutableMapOf<String, Int>()
            visibleUsers.forEach { user ->
                val role = user.role.ifEmpty { FirebaseConstants.ROLE_USER }
                roleStats[role] = (roleStats[role] ?: 0) + 1
            }

            // Fetch property requests and filter out developer requests if viewer is not developer
            val requestsSnapshot = firestore.collection(FirebaseConstants.COLLECTION_PROPERTY_REQUESTS).get().await()
            
            // Map raw property request data
            val rawRequests = requestsSnapshot.toObjects(PropertyRequest::class.java).mapIndexed { index, req ->
                req.copy(id = requestsSnapshot.documents[index].id)
            }

            // Resolve legacy requests user IDs
            val legacyUserIds = rawRequests.mapNotNull { req ->
                if (req.userId.isNotBlank() && req.userRole.isBlank()) req.userId else null
            }.distinct()

            val userMap = mutableMapOf<String, com.google.firebase.firestore.DocumentSnapshot>()
            if (legacyUserIds.isNotEmpty()) {
                coroutineScope {
                    val deferreds = legacyUserIds.map { userId ->
                        async {
                            try {
                                userId to firestore.collection(FirebaseConstants.COLLECTION_USERS)
                                    .document(userId)
                                    .get()
                                    .await()
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                    awaitAll(*deferreds.toTypedArray()).filterNotNull().forEach { pair ->
                        val id = pair.first
                        val doc = pair.second
                        if (doc.exists()) {
                            userMap[id] = doc
                        }
                    }
                }
            }

            // Count only requests visible to viewer
            val visibleRequestsCount = rawRequests.count { req ->
                val role = if (req.userRole.isNotBlank()) req.userRole else {
                    userMap[req.userId]?.getString(FirebaseConstants.FIELD_ROLE) ?: FirebaseConstants.ROLE_USER
                }
                canViewRoleRequestUser(role, viewerRole)
            }

            DashboardResult.Success(DashboardStats(totalUsers, activeUsers, blockedUsers, roleStats, visibleRequestsCount))
        } catch (e: Exception) {
            DashboardResult.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun loadUsers(filter: String, viewerRole: String): DashboardResult<List<User>> {
        return try {
            val query = firestore.collection(FirebaseConstants.COLLECTION_USERS)
                .orderBy(FirebaseConstants.FIELD_CREATED_AT, Query.Direction.DESCENDING)
            
            val snapshot = query.get().await()
            var users = snapshot.toObjects(User::class.java).mapIndexed { index, user -> 
                user.copy(uid = snapshot.documents[index].id)
            }.filter { canViewRoleRequestUser(it.role, viewerRole) }

            if (filter == "active") {
                users = users.filter { !it.blocked }
            } else if (filter == "blocked") {
                users = users.filter { it.blocked }
            }

            DashboardResult.Success(users)
        } catch (e: Exception) {
            DashboardResult.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun loadPropertyRequests(viewerRole: String): DashboardResult<List<PropertyRequest>> {
        return try {
            val snapshot = firestore.collection(FirebaseConstants.COLLECTION_PROPERTY_REQUESTS)
                .orderBy(FirebaseConstants.FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .get()
                .await()
            
            val rawRequests = snapshot.toObjects(PropertyRequest::class.java).mapIndexed { index, req ->
                req.copy(id = snapshot.documents[index].id)
            }

            val legacyUserIds = rawRequests.mapNotNull { req ->
                if (req.userId.isNotBlank() && req.userName.isBlank()) req.userId else null
            }.distinct()

            val userMap = mutableMapOf<String, com.google.firebase.firestore.DocumentSnapshot>()
            if (legacyUserIds.isNotEmpty()) {
                coroutineScope {
                    val deferreds = legacyUserIds.map { userId ->
                        async {
                            try {
                                userId to firestore.collection(FirebaseConstants.COLLECTION_USERS)
                                    .document(userId)
                                    .get()
                                    .await()
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                    awaitAll(*deferreds.toTypedArray()).filterNotNull().forEach { pair ->
                        val id = pair.first
                        val doc = pair.second
                        if (doc.exists()) {
                            userMap[id] = doc
                        }
                    }
                }
            }

            val processedRequests = mutableListOf<PropertyRequest>()
            rawRequests.forEach { req ->
                val legacyUserDoc = userMap[req.userId]
                val role = if (req.userRole.isNotBlank()) req.userRole else {
                    legacyUserDoc?.getString(FirebaseConstants.FIELD_ROLE) ?: FirebaseConstants.ROLE_USER
                }

                if (canViewRoleRequestUser(role, viewerRole)) {
                    val name = if (req.userName.isNotBlank()) req.userName else {
                        legacyUserDoc?.getString(FirebaseConstants.FIELD_NAME)
                            ?: legacyUserDoc?.getString(FirebaseConstants.FIELD_EMAIL)
                            ?: "Anonymous"
                    }
                    val email = if (req.userEmail.isNotBlank()) req.userEmail else {
                        legacyUserDoc?.getString(FirebaseConstants.FIELD_EMAIL) ?: "N/A"
                    }
                    val mobile = if (req.userMobile.isNotBlank()) req.userMobile else {
                        legacyUserDoc?.getString(FirebaseConstants.FIELD_MOBILE) ?: "Not available"
                    }

                    processedRequests.add(
                        req.copy(
                            userName = name,
                            userEmail = email,
                            userMobile = mobile,
                            userRole = role
                        )
                    )
                }
            }

            DashboardResult.Success(processedRequests)
        } catch (e: Exception) {
            DashboardResult.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun loadRoleRequests(roleTypeFilter: String, viewerRole: String): DashboardResult<List<RoleRequest>> {
        return try {
            val snapshot = firestore.collection(FirebaseConstants.COLLECTION_ROLE_REQUESTS)
                .whereEqualTo(FirebaseConstants.FIELD_STATUS, FirebaseConstants.STATUS_PENDING)
                .get()
                .await()

            val rawRequests = snapshot.documents.map { document ->
                val userId = document.getString(FirebaseConstants.FIELD_USER_ID).orEmpty()
                val requestedRole = document.getString(FirebaseConstants.FIELD_REQUESTED_ROLE).orEmpty()
                val status = document.getString(FirebaseConstants.FIELD_STATUS) ?: FirebaseConstants.STATUS_PENDING
                val reason = document.getString("reason").orEmpty()
                val createdAt = document.getTimestamp(FirebaseConstants.FIELD_CREATED_AT)
                val userName = document.getString("userName").orEmpty()
                val userEmail = document.getString("userEmail").orEmpty()
                val userMobile = document.getString("userMobile").orEmpty()
                val userRole = document.getString(FirebaseConstants.FIELD_USER_ROLE).orEmpty()

                RoleRequest(
                    id = document.id,
                    userId = userId,
                    requestedRole = requestedRole,
                    status = status,
                    reason = reason,
                    createdAt = createdAt,
                    userName = userName,
                    userEmail = userEmail,
                    userMobile = userMobile,
                    currentUserRole = userRole
                )
            }

            // Find legacy requests where userName is missing
            val legacyUserIds = rawRequests.mapNotNull { req ->
                if (req.userId.isNotBlank() && req.userName.isBlank()) req.userId else null
            }.distinct()

            val userMap = mutableMapOf<String, com.google.firebase.firestore.DocumentSnapshot>()
            if (legacyUserIds.isNotEmpty()) {
                coroutineScope {
                    val deferreds = legacyUserIds.map { userId ->
                        async {
                            try {
                                userId to firestore.collection(FirebaseConstants.COLLECTION_USERS)
                                    .document(userId)
                                    .get()
                                    .await()
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                    awaitAll(*deferreds.toTypedArray()).filterNotNull().forEach { pair ->
                        val id = pair.first
                        val doc = pair.second
                        if (doc.exists()) {
                            userMap[id] = doc
                        }
                    }
                }
            }

            val processedRequests = mutableListOf<RoleRequest>()
            rawRequests.forEach { req ->
                val legacyUserDoc = userMap[req.userId]
                val role = if (req.currentUserRole.isNotBlank()) req.currentUserRole else {
                    legacyUserDoc?.getString(FirebaseConstants.FIELD_ROLE) ?: FirebaseConstants.ROLE_USER
                }

                if (canViewRoleRequestUser(role, viewerRole)) {
                    val name = if (req.userName.isNotBlank()) req.userName else {
                        legacyUserDoc?.getString(FirebaseConstants.FIELD_NAME)
                            ?: legacyUserDoc?.getString(FirebaseConstants.FIELD_EMAIL)
                            ?: "Anonymous"
                    }
                    val email = if (req.userEmail.isNotBlank()) req.userEmail else {
                        legacyUserDoc?.getString(FirebaseConstants.FIELD_EMAIL) ?: "N/A"
                    }
                    val mobile = if (req.userMobile.isNotBlank()) req.userMobile else {
                        legacyUserDoc?.getString(FirebaseConstants.FIELD_MOBILE) ?: ""
                    }

                    processedRequests.add(
                        req.copy(
                            userName = name,
                            userEmail = email,
                            userMobile = mobile,
                            currentUserRole = role
                        )
                    )
                }
            }

            val filteredSortedRequests = processedRequests
                .filter { it.requestedRole == roleTypeFilter }
                .sortedByDescending { it.createdAt?.seconds ?: 0L }

            DashboardResult.Success(filteredSortedRequests)
        } catch (e: Exception) {
            DashboardResult.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun toggleUserBlock(userId: String, actorRole: String): DashboardResult<Boolean> {
        return try {
            val docRef = firestore.collection(FirebaseConstants.COLLECTION_USERS).document(userId)
            val doc = docRef.get().await()
            if (!doc.exists()) throw Exception("User not found")
            
            val role = doc.getString(FirebaseConstants.FIELD_ROLE).orEmpty()
            if (role == FirebaseConstants.ROLE_DEVELOPER && actorRole != FirebaseConstants.ROLE_DEVELOPER) {
                throw Exception("Only developers can block or unblock developer accounts")
            }

            val currentBlocked = doc.getBoolean(FirebaseConstants.FIELD_BLOCKED) ?: false
            docRef.update(
                mapOf(
                    FirebaseConstants.FIELD_BLOCKED to !currentBlocked,
                    FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
            ).await()
            DashboardResult.Success(!currentBlocked)
        } catch (e: Exception) {
            DashboardResult.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun updateUserRole(
        userId: String,
        newRole: String,
        actorUserId: String,
        actorRole: String
    ): DashboardResult<Boolean> {
        return try {
            val userDocRef = firestore.collection(FirebaseConstants.COLLECTION_USERS).document(userId)
            val userDoc = userDocRef.get().await()
            if (!userDoc.exists()) throw Exception("User not found")

            val currentRole = userDoc.getString(FirebaseConstants.FIELD_ROLE).orEmpty().ifBlank { FirebaseConstants.ROLE_USER }
            val isSelf = actorUserId == userId

            if (!canManageUserRole(actorRole, currentRole, newRole, isSelf)) {
                throw Exception("You do not have permission to assign this role")
            }

            val maxProperties: Any = if (newRole == FirebaseConstants.ROLE_USER) {
                FirebaseConstants.PROPERTY_LIMIT_USER
            } else {
                FieldValue.delete()
            }

            userDocRef.update(
                mapOf(
                    FirebaseConstants.FIELD_ROLE to newRole,
                    FirebaseConstants.FIELD_MAX_PROPERTIES_ALLOWED to maxProperties,
                    FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
            ).await()

            syncOwnedContentRole(userId, newRole)

            DashboardResult.Success(true)
        } catch (e: Exception) {
            DashboardResult.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun approveRoleRequest(
        requestId: String,
        userId: String,
        newRole: String,
        actorUserId: String,
        actorRole: String
    ): DashboardResult<Boolean> {
        return try {
            val userDoc = firestore.collection(FirebaseConstants.COLLECTION_USERS).document(userId).get().await()
            val requesterRole = userDoc.getString(FirebaseConstants.FIELD_ROLE).orEmpty().ifBlank { FirebaseConstants.ROLE_USER }

            if ((newRole == FirebaseConstants.ROLE_DEVELOPER || requesterRole == FirebaseConstants.ROLE_DEVELOPER) && actorRole != FirebaseConstants.ROLE_DEVELOPER) {
                throw Exception("Only developers can process developer role changes")
            }

            val maxProperties: Any = if (newRole == FirebaseConstants.ROLE_USER) {
                FirebaseConstants.PROPERTY_LIMIT_USER
            } else {
                FieldValue.delete()
            }

            firestore.runTransaction { transaction ->
                val requestRef = firestore.collection(FirebaseConstants.COLLECTION_ROLE_REQUESTS).document(requestId)
                val userRef = firestore.collection(FirebaseConstants.COLLECTION_USERS).document(userId)
                
                transaction.update(requestRef, FirebaseConstants.FIELD_STATUS, FirebaseConstants.STATUS_APPROVED, FirebaseConstants.FIELD_UPDATED_AT, FieldValue.serverTimestamp())
                transaction.update(userRef, mapOf(
                    FirebaseConstants.FIELD_ROLE to newRole,
                    FirebaseConstants.FIELD_MAX_PROPERTIES_ALLOWED to maxProperties,
                    FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                ))
            }.await()

            syncOwnedContentRole(userId, newRole)

            DashboardResult.Success(true)
        } catch (e: Exception) {
            DashboardResult.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun rejectRoleRequest(requestId: String, actorRole: String): DashboardResult<Boolean> {
        return try {
            val requestDocRef = firestore.collection(FirebaseConstants.COLLECTION_ROLE_REQUESTS).document(requestId)
            val requestDoc = requestDocRef.get().await()
            if (!requestDoc.exists()) throw Exception("Request not found")

            val requestedRole = requestDoc.getString(FirebaseConstants.FIELD_REQUESTED_ROLE).orEmpty()
            val userId = requestDoc.getString(FirebaseConstants.FIELD_USER_ID).orEmpty()

            val requesterDoc = firestore.collection(FirebaseConstants.COLLECTION_USERS).document(userId).get().await()
            val requesterRole = requesterDoc.getString(FirebaseConstants.FIELD_ROLE).orEmpty().ifBlank { FirebaseConstants.ROLE_USER }

            if ((requestedRole == FirebaseConstants.ROLE_DEVELOPER || requesterRole == FirebaseConstants.ROLE_DEVELOPER) && actorRole != FirebaseConstants.ROLE_DEVELOPER) {
                throw Exception("Only developers can process developer role changes")
            }

            requestDocRef.update(
                mapOf(
                    FirebaseConstants.FIELD_STATUS to FirebaseConstants.STATUS_REJECTED,
                    FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
            ).await()
            DashboardResult.Success(true)
        } catch (e: Exception) {
            DashboardResult.Error(e.message ?: "Unknown error")
        }
    }

    private suspend fun syncOwnedContentRole(userId: String, newRole: String) {
        try {
            // Sync properties
            val propertiesSnapshot = firestore.collection(FirebaseConstants.COLLECTION_PROPERTIES)
                .whereEqualTo(FirebaseConstants.FIELD_OWNER, userId)
                .get()
                .await()

            if (!propertiesSnapshot.isEmpty) {
                val propertiesBatch = firestore.batch()
                var propertiesUpdatedCount = 0
                propertiesSnapshot.documents.forEach { doc ->
                    val currentOwnerRole = doc.getString(FirebaseConstants.FIELD_OWNER_ROLE)
                    if (currentOwnerRole != newRole) {
                        propertiesBatch.update(
                            doc.reference,
                            mapOf(
                                FirebaseConstants.FIELD_OWNER_ROLE to newRole,
                                FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                            )
                        )
                        propertiesUpdatedCount++
                    }
                }
                if (propertiesUpdatedCount > 0) {
                    propertiesBatch.commit().await()
                }
            }

            // Sync auctions
            val auctionsSnapshot = firestore.collection(FirebaseConstants.COLLECTION_AUCTIONS)
                .whereEqualTo(FirebaseConstants.FIELD_OWNER_ID, userId)
                .get()
                .await()

            if (!auctionsSnapshot.isEmpty) {
                val auctionsBatch = firestore.batch()
                var auctionsUpdatedCount = 0
                auctionsSnapshot.documents.forEach { doc ->
                    val currentOwnerRole = doc.getString(FirebaseConstants.FIELD_OWNER_ROLE)
                    if (currentOwnerRole != newRole) {
                        auctionsBatch.update(
                            doc.reference,
                            mapOf(
                                FirebaseConstants.FIELD_OWNER_ROLE to newRole,
                                FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                            )
                        )
                        auctionsUpdatedCount++
                    }
                }
                if (auctionsUpdatedCount > 0) {
                    auctionsBatch.commit().await()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getRoleLevel(role: String): Int {
        val hierarchy = listOf(
            FirebaseConstants.ROLE_USER,
            FirebaseConstants.ROLE_BROKER,
            FirebaseConstants.ROLE_ADMIN,
            FirebaseConstants.ROLE_SUPERADMIN,
            FirebaseConstants.ROLE_DEVELOPER
        )
        val normalized = if (hierarchy.contains(role)) role else FirebaseConstants.ROLE_USER
        return hierarchy.indexOf(normalized)
    }

    private fun hasRequiredRole(userRole: String, requiredRole: String): Boolean {
        return getRoleLevel(userRole) >= getRoleLevel(requiredRole)
    }

    private fun canAssignRole(actorRole: String, targetRole: String): Boolean {
        if (targetRole == FirebaseConstants.ROLE_DEVELOPER) {
            return actorRole == FirebaseConstants.ROLE_DEVELOPER
        }
        return hasRequiredRole(actorRole, FirebaseConstants.ROLE_SUPERADMIN)
    }

    private fun canManageUserRole(
        actorRole: String,
        targetCurrentRole: String,
        targetNextRole: String,
        isSelf: Boolean
    ): Boolean {
        if (isSelf) return false
        if (targetCurrentRole == FirebaseConstants.ROLE_DEVELOPER && actorRole != FirebaseConstants.ROLE_DEVELOPER) {
            return false
        }
        if (targetNextRole == FirebaseConstants.ROLE_DEVELOPER && actorRole != FirebaseConstants.ROLE_DEVELOPER) {
            return false
        }
        return canAssignRole(actorRole, targetNextRole)
    }

    private fun canViewRoleRequestUser(userRole: String, viewerRole: String): Boolean {
        val normalizedUserRole = userRole.ifBlank { FirebaseConstants.ROLE_USER }
        val isDeveloperUser = normalizedUserRole == FirebaseConstants.ROLE_DEVELOPER
        val isDeveloperViewer = viewerRole == FirebaseConstants.ROLE_DEVELOPER
        return !isDeveloperUser || isDeveloperViewer
    }
}
