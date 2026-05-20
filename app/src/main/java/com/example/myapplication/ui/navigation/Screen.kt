package com.example.myapplication.ui.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Login : Screen("login")
    data object Main : Screen("main")
    data object Home : Screen("home")
    data object Profile : Screen("profile")
    data object MyProperties : Screen("my_properties")
    data object AddResidentialProperty : Screen("add_residential_property")
    data object AddCommercialProperty : Screen("add_commercial_property")
    data object AddIndustrialProperty : Screen("add_industrial_property")
    data object PropertyDetails : Screen("property_details/{propertyId}") {
        fun createRoute(propertyId: String) = "property_details/$propertyId"
    }
    data object Dashboard : Screen("dashboard")
}
