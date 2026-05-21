package com.example.myapplication.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.myapplication.ui.screens.addproperty.AddResidentialPropertyScreen
import com.example.myapplication.ui.screens.addproperty.AddCommercialPropertyScreen
import com.example.myapplication.ui.screens.addproperty.AddIndustrialPropertyScreen
import com.example.myapplication.ui.screens.addproperty.AddLandPropertyScreen
import com.example.myapplication.ui.screens.login.LoginScreen
import com.example.myapplication.ui.screens.main.MainScreen
import com.example.myapplication.ui.screens.myproperties.MyPropertiesScreen
import com.example.myapplication.ui.screens.profile.ProfileScreen
import com.example.myapplication.ui.screens.propertydetails.PropertyDetailsScreen
import com.example.myapplication.ui.screens.splash.SplashScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Splash.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(route = Screen.Splash.route) {
            SplashScreen(
                onNavigateToMain = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(route = Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = { needsProfileCompletion ->
                    if (needsProfileCompletion) {
                        navController.navigate(Screen.Profile.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.Main.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(route = Screen.Profile.route) {
            ProfileScreen(
                onNavigateToHome = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Profile.route) { inclusive = true }
                    }
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Profile.route) { inclusive = true }
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(route = Screen.Main.route) {
            MainScreen(
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                },
                onNavigateToPropertyDetails = { propertyId ->
                    navController.navigate(Screen.PropertyDetails.createRoute(propertyId))
                },
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard.route)
                },
                onNavigateToMyProperties = {
                    navController.navigate(Screen.MyProperties.route)
                },
                onNavigateToAddResidentialProperty = {
                    navController.navigate(Screen.AddResidentialProperty.createRoute())
                },
                onNavigateToAddCommercialProperty = {
                    navController.navigate(Screen.AddCommercialProperty.createRoute())
                },
                onNavigateToAddIndustrialProperty = {
                    navController.navigate(Screen.AddIndustrialProperty.createRoute())
                },
                onNavigateToAddLandProperty = {
                    navController.navigate(Screen.AddLandProperty.createRoute())
                }
            )
        }

        composable(route = Screen.MyProperties.route) {
            MyPropertiesScreen(
                onBack = { navController.popBackStack() },
                onNavigateToPropertyDetails = { propertyId ->
                    navController.navigate(Screen.PropertyDetails.createRoute(propertyId))
                },
                onEditProperty = { property ->
                    val route = when (property.propertyType.lowercase()) {
                        "commercial" -> Screen.AddCommercialProperty.createRoute(property.id)
                        "industrial" -> Screen.AddIndustrialProperty.createRoute(property.id)
                        "land" -> Screen.AddLandProperty.createRoute(property.id)
                        else -> Screen.AddResidentialProperty.createRoute(property.id)
                    }
                    navController.navigate(route)
                }
            )
        }

        composable(route = Screen.Dashboard.route) {
            com.example.myapplication.ui.screens.dashboard.DashboardScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AddResidentialProperty.route,
            arguments = listOf(
                navArgument("propertyId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            AddResidentialPropertyScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AddCommercialProperty.route,
            arguments = listOf(
                navArgument("propertyId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            AddCommercialPropertyScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AddIndustrialProperty.route,
            arguments = listOf(
                navArgument("propertyId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            AddIndustrialPropertyScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AddLandProperty.route,
            arguments = listOf(
                navArgument("propertyId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            AddLandPropertyScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.PropertyDetails.route,
            arguments = listOf(
                navArgument("propertyId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val propertyId = backStackEntry.arguments?.getString("propertyId") ?: ""
            PropertyDetailsScreen(
                propertyId = propertyId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
