package com.example.myapplication.data.model

import com.google.firebase.Timestamp

data class PropertyRequest(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val userMobile: String = "",
    val userRole: String = "user",
    val propertyType: String = "",
    val area: String = "",
    val minBudget: Long = 0,
    val maxBudget: Long = 0,
    val status: String = "pending",
    val createdAt: Timestamp? = null
)
