package com.example.myapplication.ui.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Login : Screen("login")
    data object Main : Screen("main")
    data object Home : Screen("home")
    data object Profile : Screen("profile")
}
