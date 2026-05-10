package com.example.myapplication.data.repository

import com.example.myapplication.data.model.DashboardStats
import com.example.myapplication.data.model.PropertyRequest
import com.example.myapplication.data.model.RoleRequest
import com.example.myapplication.data.model.User
import com.example.myapplication.utils.FirebaseConstants
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class DashboardRepositoryImpl(
    private val firestore: FirebaseFirestore
) : DashboardRepository {

    override suspend fun getDashboardStats(viewerRole: String): DashboardResult<DashboardStats> {
        return try {
            val usersSnapshot = firestore.collection(FirebaseConstants.COLLECTION_USERS).get().await()
            val allUsers = usersSnapshot.toObjects(User::class.java)

            // Simplification: We assume viewer can see these users based on UI check
            val totalUsers = allUsers.size
            val activeUsers = allUsers.count { !it.blocked }
            val blockedUsers = allUsers.count { it.blocked }

            val roleStats = mutableMapOf<String, Int>()
            allUsers.forEach { user ->
                val role = user.role.ifEmpty { FirebaseConstants.ROLE_USER }
                roleStats[role] = (roleStats[role] ?: 0) + 1
            }

            val requestsSnapshot = firestore.collection(FirebaseConstants.COLLECTION_PROPERTY_REQUESTS).get().await()
            val totalRequests = requestsSnapshot.size()

            DashboardResult.Success(DashboardStats(totalUsers, activeUsers, blockedUsers, roleStats, totalRequests))
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
            }

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
            
            val requests = snapshot.toObjects(PropertyRequest::class.java).mapIndexed { index, req ->
                req.copy(id = snapshot.documents[index].id)
            }
            DashboardResult.Success(requests)
        } catch (e: Exception) {
            DashboardResult.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun loadRoleRequests(roleTypeFilter: String, viewerRole: String): DashboardResult<List<RoleRequest>> {
        return try {
            val snapshot = firestore.collection(FirebaseConstants.COLLECTION_ROLE_REQUESTS)
                .whereEqualTo(FirebaseConstants.FIELD_REQUESTED_ROLE, roleTypeFilter)
                .orderBy(FirebaseConstants.FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .get()
                .await()
            
            val requests = snapshot.toObjects(RoleRequest::class.java).mapIndexed { index, req ->
                req.copy(id = snapshot.documents[index].id)
            }
            DashboardResult.Success(requests)
        } catch (e: Exception) {
            DashboardResult.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun toggleUserBlock(userId: String, actorRole: String): DashboardResult<Boolean> {
        return try {
            val docRef = firestore.collection(FirebaseConstants.COLLECTION_USERS).document(userId)
            val doc = docRef.get().await()
            if (!doc.exists()) throw Exception("User not found")
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
            firestore.collection(FirebaseConstants.COLLECTION_USERS).document(userId).update(
                mapOf(
                    FirebaseConstants.FIELD_ROLE to newRole,
                    FirebaseConstants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
            ).await()
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
            firestore.runTransaction { transaction ->
                val requestRef = firestore.collection(FirebaseConstants.COLLECTION_ROLE_REQUESTS).document(requestId)
                val userRef = firestore.collection(FirebaseConstants.COLLECTION_USERS).document(userId)
                
                transaction.update(requestRef, FirebaseConstants.FIELD_STATUS, FirebaseConstants.STATUS_APPROVED, FirebaseConstants.FIELD_UPDATED_AT, FieldValue.serverTimestamp())
                transaction.update(userRef, FirebaseConstants.FIELD_ROLE, newRole, FirebaseConstants.FIELD_UPDATED_AT, FieldValue.serverTimestamp())
            }.await()
            DashboardResult.Success(true)
        } catch (e: Exception) {
            DashboardResult.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun rejectRoleRequest(requestId: String, actorRole: String): DashboardResult<Boolean> {
        return try {
            firestore.collection(FirebaseConstants.COLLECTION_ROLE_REQUESTS).document(requestId).update(
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
}
