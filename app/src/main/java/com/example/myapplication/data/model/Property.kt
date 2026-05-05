package com.example.myapplication.data.model

import com.google.firebase.Timestamp

/**
 * Property data model matching web app's property fields.
 */
data class Property(
    val id: String = "",
    val name: String = "",
    val images: List<String> = emptyList(),
    val propertyType: String = "",
    val listingType: String = "",
    val price: Double = 0.0,
    val rent: Double = 0.0,
    val location: String = "",
    val cityState: String = "",
    val isActive: Boolean = true,
    val ownerRole: String = "",
    val uniqueId: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val displayPrice: String
        get() = if (listingType == "rent") {
            "₹${String.format("%,.0f", rent)}/mo"
        } else {
            "₹${String.format("%,.0f", price)}"
        }

    val displayImage: String
        get() = images.firstOrNull { it.isNotBlank() }
            ?: "android.resource://com.example.myapplication/drawable/property_placeholder"
}
