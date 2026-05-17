package com.example.myapplication.data.model

import com.google.firebase.Timestamp

/**
 * Property data model matching web app's property fields.
 * Contains both list-view fields and full detail fields.
 */
data class Property(
    // ── Core / List fields ────────────────────────────────────────
    val id: String = "",
    val name: String = "",
    val images: List<String> = emptyList(),
    val propertyType: String = "",   // residential | commercial | industrial | land
    val listingType: String = "",    // sale | rent
    val price: Double = 0.0,
    val rent: Double = 0.0,
    val location: String = "",
    val cityState: String = "",
    val status: String = "",
    val isActive: Boolean = true,
    val ownerRole: String = "",
    val uniqueId: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,

    // ── Owner / Contact ───────────────────────────────────────────
    val owner: String = "",          // owner UID – used to fetch mobile number

    // ── Description & Amenities ───────────────────────────────────
    val description: String = "",
    val amenities: List<String> = emptyList(),

    // ── Location sub-fields ───────────────────────────────────────
    val buildingName: String = "",
    val locality: String = "",
    val zoneType: String = "",
    val locationHub: String = "",

    // ── Type & Category ───────────────────────────────────────────
    val residentialType: String = "",
    val commercialType: String = "",
    val industrialType: String = "",
    val landType: String = "",

    // ── Status & Availability ─────────────────────────────────────
    val possessionStatus: String = "",
    val availableFrom: String = "",

    // ── Property & Legal ─────────────────────────────────────────
    val propertyCondition: String = "",
    val ownership: String = "",
    val plotArea: String = "",
    val builtUpArea: String = "",
    val carpetArea: String = "",
    val totalConstructionArea: String = "",
    val frontage: String = "",
    val roadAccess: String = "",

    // ── Residential-specific ──────────────────────────────────────
    val bhkConfig: String = "",
    val propertyAge: String = "",
    val floorNumber: String = "",
    val bathrooms: String = "",
    val balconies: String = "",
    val furnishing: String = "",
    val coveredParking: String = "",
    val openParking: String = "",
    val parkingCharges: String = "",
    val tenantType: String = "",
    val petFriendly: String = "",
    val maintenanceCharges: String = "",
    val servantRoom: String = "",
    val amenitiesText: String = "",
    val residentsCount: String = "",

    // ── Commercial-specific ───────────────────────────────────────
    val parkingType: String = "",
    val washroomType: String = "",

    // ── Industrial-specific ───────────────────────────────────────
    val shedHeight: String = "",
    val shedSideWallHeight: String = "",
    val plotDimensions: String = "",
    val shedBuiltUpArea: String = "",
    val builtUpConstructionArea: String = "",
    val electricityLoad: String = "",
    val waterAvailable: String = "",
    val preLeased: String = "",
    val preRented: String = "",

    // ── Land-specific ─────────────────────────────────────────────
    val areaAcres: String = "",
    val plotLength: String = "",
    val plotBreadth: String = "",
    val landFacing: String = "",
    val roadWidth: String = "",
    val landStatus: String = "",

    // ── Floors & Elevation ────────────────────────────────────────
    val yourFloor: String = "",
    val totalFloors: String = "",
    val staircases: String = "",
    val passengerLift: String = "",
    val serviceLift: String = "",

    // ── Facing & Facilities ───────────────────────────────────────
    val facing: String = "",
    val rearFacing: String = "",
    val roadFacing: String = "",

    // ── Lease & Financials ────────────────────────────────────────
    val rentNegotiable: String = "",
    val securityDeposit: String = "",
    val rentIncrease: String = "",
    val lockInPeriod: String = "",

    // ── Charges & Inclusions ─────────────────────────────────────
    val dampUpsIncluded: String = "",
    val electricityIncluded: String = "",
    val waterChargesIncluded: String = "",
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

    /** Location text matching web's getLocationText() */
    val displayLocation: String
        get() = buildString {
            if (location.isNotBlank()) append(location)
            if (cityState.isNotBlank()) {
                if (isNotEmpty()) append(", ")
                append(cityState)
            }
        }

    /** Human-readable property type label matching web's getPropertyTypeDisplay() */
    val propertyTypeDisplay: String
        get() = when (propertyType.lowercase()) {
            "residential" -> "Residential"
            "commercial"  -> "Commercial"
            "industrial"  -> "Industrial"
            "land"        -> "Land"
            else          -> propertyType.replaceFirstChar { it.uppercase() }
        }
}
