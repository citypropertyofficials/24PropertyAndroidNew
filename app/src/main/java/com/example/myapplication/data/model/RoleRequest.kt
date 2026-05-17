package com.example.myapplication.data.model

import com.google.firebase.Timestamp

data class RoleRequest(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val userMobile: String = "",
    val currentUserRole: String = "user",
    val requestedRole: String = "",
    val status: String = "pending",
    val reason: String = "",
    val createdAt: Timestamp? = null
)
