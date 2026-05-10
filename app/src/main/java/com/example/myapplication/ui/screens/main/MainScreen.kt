package com.example.myapplication.ui.screens.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.ui.screens.auctions.AuctionsScreen
import com.example.myapplication.ui.screens.favorites.FavoritesScreen
import com.example.myapplication.ui.screens.home.HomeScreen
import com.example.myapplication.ui.screens.more.MoreScreen

sealed class BottomNavItem(
    val labelResId: Int,
    val icon: ImageVector
) {
    data object Home : BottomNavItem(R.string.tab_home, Icons.Default.Home)
    data object Favorites : BottomNavItem(R.string.tab_favorites, Icons.Default.Favorite)
    data object Auctions : BottomNavItem(R.string.tab_auctions, Icons.Default.List)
    data object More : BottomNavItem(R.string.tab_more, Icons.Default.Menu)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToProfile: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToPropertyDetails: (String) -> Unit,
    onNavigateToDashboard: () -> Unit
) {
    val navItems = listOf(
        BottomNavItem.Home,
        BottomNavItem.Favorites,
        BottomNavItem.Auctions,
        BottomNavItem.More
    )
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = stringResource(item.labelResId)
                            )
                        },
                        label = { Text(stringResource(item.labelResId)) },
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedIndex) {
                0 -> HomeScreen(onNavigateToPropertyDetails = onNavigateToPropertyDetails)
                1 -> FavoritesScreen(onNavigateToPropertyDetails = onNavigateToPropertyDetails)
                2 -> AuctionsScreen()
                3 -> MoreScreen(
                    onNavigateToProfile = onNavigateToProfile,
                    onNavigateToDashboard = onNavigateToDashboard,
                    onLogout = onLogout
                )
            }
        }
    }
}
