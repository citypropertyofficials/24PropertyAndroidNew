package com.example.myapplication.ui.screens.home

import com.example.myapplication.data.model.LocationPreference
import com.example.myapplication.data.repository.GeoFilter
import com.example.myapplication.data.repository.LocationRestriction
import com.example.myapplication.data.repository.SearchArea

const val HOME_PROPERTY_TYPE_RESIDENTIAL = "residential"
const val HOME_PROPERTY_TYPE_COMMERCIAL = "commercial"
const val HOME_PROPERTY_TYPE_INDUSTRIAL = "industrial"
const val HOME_PROPERTY_TYPE_LAND = "land"
const val HOME_LISTING_TYPE_RENT = "rent"
const val HOME_LISTING_TYPE_SALE = "sale"

data class HomeSearchArea(
    val placeId: String? = null,
    val displayName: String = "",
    val formattedAddress: String = "",
    val lat: Double? = null,
    val lng: Double? = null
) {
    fun toRepositoryModel(): SearchArea = SearchArea(
        placeId = placeId,
        displayName = displayName,
        formattedAddress = formattedAddress,
        lat = lat,
        lng = lng
    )
}

data class HomePriceRange(
    val min: Float,
    val max: Float,
    val step: Float
)

data class HomeFilters(
    val values: Map<String, Any> = emptyMap()
) {
    val count: Int get() = values.size
    fun isEmpty(): Boolean = values.isEmpty()
}

data class HomeUiState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val properties: List<com.example.myapplication.data.model.Property> = emptyList(),
    val hasMore: Boolean = false,
    val selectedPropertyType: String = HOME_PROPERTY_TYPE_RESIDENTIAL,
    val selectedListingType: String = HOME_LISTING_TYPE_RENT,
    val appliedFilters: HomeFilters = HomeFilters(),
    val appliedSearchAreas: List<HomeSearchArea> = emptyList(),
    val locationRadiusKm: Int = 1,
    val locationRestriction: LocationRestriction? = null
)

fun getHomePriceRange(propertyType: String, listingType: String): HomePriceRange {
    return when {
        listingType == HOME_LISTING_TYPE_RENT && propertyType == HOME_PROPERTY_TYPE_RESIDENTIAL ->
            HomePriceRange(0f, 1_000_000f, 1_000f)
        listingType == HOME_LISTING_TYPE_RENT ->
            HomePriceRange(0f, 100_000_000f, 10_000f)
        propertyType == HOME_PROPERTY_TYPE_RESIDENTIAL ->
            HomePriceRange(0f, 100_000_000f, 100_000f)
        else ->
            HomePriceRange(0f, 500_000_000f, 500_000f)
    }
}

fun buildGeoFilter(searchAreas: List<HomeSearchArea>, radiusKm: Int): GeoFilter? {
    if (searchAreas.isEmpty()) return null
    return GeoFilter(
        centers = searchAreas.map { it.toRepositoryModel() },
        radiusKm = radiusKm.toDouble()
    )
}

fun buildLocationRestriction(locationPreference: LocationPreference?): LocationRestriction? {
    if (locationPreference == null) return null
    return LocationRestriction(
        stateCode = locationPreference.stateCode,
        stateName = locationPreference.stateName,
        districtName = locationPreference.districtName
    )
}
