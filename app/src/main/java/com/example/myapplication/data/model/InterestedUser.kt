package com.example.myapplication.data.model

/**
 * Data model for an interested user snapshot.
 * Stored at: properties/{propertyId}/interestedUsers/{userId}
 * Matches web's getInterestedUserSnapshot() shape.
 */
data class InterestedUser(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val mobile: String = ""
)
