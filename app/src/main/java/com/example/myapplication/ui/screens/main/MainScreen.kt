package com.example.myapplication.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.ui.screens.addproperty.propertyTypeOptions
import com.example.myapplication.ui.screens.auctions.AuctionsScreen
import com.example.myapplication.ui.screens.favorites.FavoritesScreen
import com.example.myapplication.ui.screens.home.HomeScreen
import com.example.myapplication.ui.screens.more.MoreScreen
import com.example.myapplication.ui.theme.GoldStart
import com.example.myapplication.ui.theme.PrimaryEnd
import com.example.myapplication.ui.theme.PrimaryStart
import com.example.myapplication.ui.theme.TextSecondary
import com.example.myapplication.ui.theme.TextWhite
import kotlinx.coroutines.launch

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
    onNavigateToDashboard: () -> Unit,
    onNavigateToMyProperties: () -> Unit,
    onNavigateToAddResidentialProperty: () -> Unit,
    onNavigateToAddCommercialProperty: () -> Unit,
    onNavigateToAddIndustrialProperty: () -> Unit
) {
    val navItems = listOf(
        BottomNavItem.Home,
        BottomNavItem.Favorites,
        BottomNavItem.Auctions,
        BottomNavItem.More
    )
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var showAddSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Add Property", style = MaterialTheme.typography.headlineSmall, color = PrimaryEnd)
                Spacer(Modifier.height(8.dp))
                Text("Choose property category. Residential and Commercial are live.", color = TextSecondary)
                Spacer(Modifier.height(16.dp))
                propertyTypeOptions.forEach { option ->
                    val isActive = option.id == "residential" || option.id == "commercial" || option.id == "industrial"
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clickable {
                                showAddSheet = false
                                when (option.id) {
                                    "residential" -> onNavigateToAddResidentialProperty()
                                    "commercial" -> onNavigateToAddCommercialProperty()
                                    "industrial" -> onNavigateToAddIndustrialProperty()
                                    else -> {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("${option.title} will be wired next.")
                                        }
                                    }
                                }
                            },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) PrimaryStart.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(option.title, fontWeight = FontWeight.Bold, color = PrimaryEnd)
                            Spacer(Modifier.height(6.dp))
                            Text(option.description, color = TextSecondary)
                            if (!isActive) {
                                Spacer(Modifier.height(10.dp))
                                Text("Coming next", color = GoldStart, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selectedIndex == 0) {
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    containerColor = PrimaryStart,
                    contentColor = TextWhite
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_property)
                    )
                }
            }
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
                    onNavigateToMyProperties = onNavigateToMyProperties,
                    onLogout = onLogout
                )
            }
        }
    }
}
