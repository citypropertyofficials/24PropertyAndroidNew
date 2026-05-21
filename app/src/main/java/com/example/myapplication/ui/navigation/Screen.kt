package com.example.myapplication.ui.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Login : Screen("login")
    data object Main : Screen("main")
    data object Home : Screen("home")
    data object Profile : Screen("profile")
    data object MyProperties : Screen("my_properties")
    data object AddResidentialProperty : Screen("add_residential_property?propertyId={propertyId}") {
        fun createRoute(propertyId: String? = null) = if (propertyId.isNullOrBlank()) {
            "add_residential_property"
        } else {
            "add_residential_property?propertyId=$propertyId"
        }
    }
    data object AddCommercialProperty : Screen("add_commercial_property?propertyId={propertyId}") {
        fun createRoute(propertyId: String? = null) = if (propertyId.isNullOrBlank()) {
            "add_commercial_property"
        } else {
            "add_commercial_property?propertyId=$propertyId"
        }
    }
    data object AddIndustrialProperty : Screen("add_industrial_property?propertyId={propertyId}") {
        fun createRoute(propertyId: String? = null) = if (propertyId.isNullOrBlank()) {
            "add_industrial_property"
        } else {
            "add_industrial_property?propertyId=$propertyId"
        }
    }
    data object AddLandProperty : Screen("add_land_property?propertyId={propertyId}") {
        fun createRoute(propertyId: String? = null) = if (propertyId.isNullOrBlank()) {
            "add_land_property"
        } else {
            "add_land_property?propertyId=$propertyId"
        }
    }
    data object PropertyDetails : Screen("property_details/{propertyId}") {
        fun createRoute(propertyId: String) = "property_details/$propertyId"
    }
    data object Dashboard : Screen("dashboard")
}
