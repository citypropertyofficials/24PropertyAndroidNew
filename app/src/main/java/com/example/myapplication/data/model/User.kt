package com.example.myapplication.data.model

import com.google.firebase.Timestamp

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val mobile: String = "",
    val role: String = "user",
    val maxPropertiesAllowed: Int? = null,
    val preferredLocations: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val isProfileComplete: Boolean
        get() = name.isNotBlank() && mobile.isNotBlank()
}
