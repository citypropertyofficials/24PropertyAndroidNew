package com.example.myapplication.ui.screens.home

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.common.AppFullScreenLoading
import com.example.myapplication.ui.common.PropertyCard
import com.example.myapplication.ui.theme.BackgroundPrimary
import com.example.myapplication.ui.theme.BorderColor
import com.example.myapplication.ui.theme.PrimaryEnd
import com.example.myapplication.ui.theme.PrimaryStart
import com.example.myapplication.ui.theme.TextSecondary
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.koin.androidx.compose.koinViewModel
import java.text.NumberFormat
import java.util.Locale

private data class FilterDefinition(
    val id: String,
    val label: String,
    val type: FilterControlType,
    val options: List<String> = emptyList(),
    val minKey: String = "",
    val maxKey: String = "",
    val range: HomePriceRange? = null
)

private enum class FilterControlType {
    OPTIONS,
    RANGE,
    RANGE_PAIR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPropertyDetails: (String) -> Unit = {},
    viewModel: HomeViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val interestedIds by viewModel.interestedIds.collectAsState()
    val interestedLoadingMap by viewModel.interestedLoadingMap.collectAsState()
    val currentUserId = viewModel.currentUserId
    val listState = rememberLazyListState()
    var showFilterDialog by rememberSaveable { mutableStateOf(false) }
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundPrimary)) {
        if (uiState.isInitialLoading) {
            AppFullScreenLoading()
        } else {
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        HomeSearchShell(
                            context = context,
                            state = uiState,
                            onListingTypeChange = viewModel::updateListingType,
                            onPropertyTypeChange = viewModel::updatePropertyType,
                            onAreasChanged = { areas ->
                                viewModel.applyFilters(
                                    filters = uiState.appliedFilters.values,
                                    searchAreas = areas,
                                    locationRadiusKm = uiState.locationRadiusKm
                                )
                            },
                            onOpenFilter = { showFilterDialog = true },
                            onClearFilters = viewModel::clearFilters
                        )
                    }

                    uiState.errorMessage?.let { error ->
                        item {
                            ErrorPropertyContent(message = error, onRetry = viewModel::retry)
                        }
                    }

                    if (uiState.properties.isEmpty() && uiState.errorMessage == null) {
                        item {
                            EmptyPropertyContent(onRetry = viewModel::retry)
                        }
                    } else {
                        items(uiState.properties, key = { it.id }) { property ->
                            PropertyCard(
                                property = property,
                                isFavorite = favoriteIds.contains(property.id),
                                onFavoriteClick = { viewModel.toggleFavorite(property.id) },
                                isInterested = interestedIds.contains(property.id),
                                isInterestedLoading = interestedLoadingMap[property.id] == true,
                                onInterestedClick = { viewModel.toggleInterested(property.id) },
                                isOwner = currentUserId != null && property.owner == currentUserId,
                                onClick = { onNavigateToPropertyDetails(property.id) }
                            )
                        }
                    }

                    if (uiState.isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    if (uiState.hasMore && !uiState.isLoadingMore) {
                        item {
                            LaunchedEffect(uiState.properties.size) {
                                viewModel.loadMore()
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showFilterDialog) {
        HomeFilterDialog(
            context = context,
            sheetState = filterSheetState,
            selectedPropertyType = uiState.selectedPropertyType,
            listingType = uiState.selectedListingType,
            initialFilters = uiState.appliedFilters.values,
            initialSearchAreas = uiState.appliedSearchAreas,
            initialLocationRadius = uiState.locationRadiusKm,
            onDismiss = { showFilterDialog = false },
            onApply = { filters, searchAreas, radiusKm ->
                showFilterDialog = false
                viewModel.applyFilters(filters, searchAreas, radiusKm)
            }
        )
    }
}

@Composable
private fun HomeSearchShell(
    context: Context,
    state: HomeUiState,
    onListingTypeChange: (String) -> Unit,
    onPropertyTypeChange: (String) -> Unit,
    onAreasChanged: (List<HomeSearchArea>) -> Unit,
    onOpenFilter: () -> Unit,
    onClearFilters: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFF4F8FF))
                ),
                shape = RoundedCornerShape(28.dp)
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Find the right property",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = PrimaryEnd
        )
        Text(
            text = "Search by area and refine listings with the same overall flow as web.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ListingTypeChip(
                label = "For Rent",
                selected = state.selectedListingType == HOME_LISTING_TYPE_RENT,
                onClick = { onListingTypeChange(HOME_LISTING_TYPE_RENT) },
                modifier = Modifier.weight(1f)
            )
            ListingTypeChip(
                label = "For Sale",
                selected = state.selectedListingType == HOME_LISTING_TYPE_SALE,
                onClick = { onListingTypeChange(HOME_LISTING_TYPE_SALE) },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                HOME_PROPERTY_TYPE_RESIDENTIAL to "Residential",
                HOME_PROPERTY_TYPE_COMMERCIAL to "Commercial",
                HOME_PROPERTY_TYPE_INDUSTRIAL to "Industrial",
                HOME_PROPERTY_TYPE_LAND to "Land"
            ).forEach { (type, label) ->
                PropertyTypePill(
                    label = label,
                    selected = state.selectedPropertyType == type,
                    onClick = { onPropertyTypeChange(type) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        LocationSearchField(
            context = context,
            selectedAreas = state.appliedSearchAreas,
            onSelectedAreasChange = onAreasChanged,
            onOpenFilter = onOpenFilter
        )

        if (!state.appliedFilters.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text("${state.appliedFilters.count} Filters Applied") }
                )
                TextButton(onClick = onClearFilters) {
                    Text("Clear")
                }
            }
        }
    }
}

@Composable
private fun ListingTypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        colors = if (selected) {
            ButtonDefaults.buttonColors(
                containerColor = PrimaryStart,
                contentColor = Color.White
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = PrimaryEnd
            )
        },
        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (selected) 5.dp else 0.dp)
    ) {
        Text(label)
    }
}

@Composable
private fun PropertyTypePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = if (selected) PrimaryEnd else Color.White,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else PrimaryEnd,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LocationSearchField(
    context: Context,
    selectedAreas: List<HomeSearchArea>,
    onSelectedAreasChange: (List<HomeSearchArea>) -> Unit,
    onOpenFilter: () -> Unit
) {
    val placesClient = rememberPlacesClient(context)
    val sessionToken = remember { AutocompleteSessionToken.newInstance() }
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var predictions by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.length < 2 || placesClient == null) {
            predictions = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        isSearching = true
        predictions = runCatching {
            val request = FindAutocompletePredictionsRequest.builder()
                .setSessionToken(sessionToken)
                .setCountries(listOf("IN"))
                .setQuery(query)
                .build()
            placesClient.findAutocompletePredictions(request).await().autocompletePredictions
        }.getOrDefault(emptyList())
        isSearching = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(22.dp)),
            label = { Text("Search Location") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                    }
                    Text(
                        text = "Filter",
                        color = PrimaryStart,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onOpenFilter)
                    )
                }
            }
        )

        if (predictions.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .padding(vertical = 6.dp)
            ) {
                predictions.take(5).forEach { prediction ->
                    Text(
                        text = prediction.getFullText(null).toString(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    fetchSearchArea(placesClient, sessionToken, prediction.placeId)?.let { area ->
                                        val nextAreas = (selectedAreas + area).distinctBy {
                                            it.placeId ?: "${it.displayName}-${it.formattedAddress}"
                                        }.take(3)
                                        onSelectedAreasChange(nextAreas)
                                    }
                                    query = ""
                                    predictions = emptyList()
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }

        if (selectedAreas.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                selectedAreas.chunked(2).forEach { rowAreas ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowAreas.forEach { area ->
                            AssistChip(
                                onClick = {},
                                label = { Text(area.displayName.ifBlank { area.formattedAddress }) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.clickable {
                                            onSelectedAreasChange(selectedAreas.filterNot { it == area })
                                        }
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(2 - rowAreas.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeFilterDialog(
    context: Context,
    sheetState: SheetState,
    selectedPropertyType: String,
    listingType: String,
    initialFilters: Map<String, Any>,
    initialSearchAreas: List<HomeSearchArea>,
    initialLocationRadius: Int,
    onDismiss: () -> Unit,
    onApply: (Map<String, Any>, List<HomeSearchArea>, Int) -> Unit
) {
    val priceRange = remember(selectedPropertyType, listingType) {
        getHomePriceRange(selectedPropertyType, listingType)
    }
    var draftFilters by remember(initialFilters, priceRange) {
        mutableStateOf(initialFilters.toMutableMap().apply {
            if (!containsKey("priceMin")) this["priceMin"] = priceRange.min.toInt()
            if (!containsKey("priceMax")) this["priceMax"] = priceRange.max.toInt()
        })
    }
    var searchAreas by remember(initialSearchAreas) { mutableStateOf(initialSearchAreas) }
    var radiusKm by remember(initialLocationRadius) { mutableStateOf(initialLocationRadius) }

    val filters = remember(selectedPropertyType, priceRange) {
        getFilterDefinitions(selectedPropertyType, priceRange)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFFFDFEFF)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Filter Properties",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = PrimaryEnd
            )
            Text(
                text = "Fine-tune your results with the same web-style filters.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                item {
                    LocationSearchField(
                        context = context,
                        selectedAreas = searchAreas,
                        onSelectedAreasChange = { searchAreas = it },
                        onOpenFilter = {}
                    )
                }
                if (searchAreas.isNotEmpty()) {
                    item {
                        Column {
                            Text("Search Radius", fontWeight = FontWeight.SemiBold)
                            Text("$radiusKm km radius", color = PrimaryStart)
                            androidx.compose.material3.Slider(
                                value = radiusKm.toFloat(),
                                onValueChange = { radiusKm = it.toInt() },
                                valueRange = 1f..20f,
                                steps = 18
                            )
                        }
                    }
                }
                items(filters, key = { it.id }) { filter ->
                    when (filter.type) {
                        FilterControlType.OPTIONS -> {
                            DropdownFilterField(
                                label = filter.label,
                                options = filter.options,
                                selectedValue = draftFilters[filter.id] as? String ?: "Any",
                                onValueSelected = { draftFilters = draftFilters.toMutableMap().apply { put(filter.id, it) } }
                            )
                        }
                        FilterControlType.RANGE -> {
                            RangeFilterField(
                                label = filter.label,
                                min = filter.range!!.min,
                                max = filter.range.max,
                                step = filter.range.step,
                                start = (draftFilters["priceMin"] as? Int ?: filter.range.min.toInt()).toFloat(),
                                end = (draftFilters["priceMax"] as? Int ?: filter.range.max.toInt()).toFloat(),
                                onRangeChange = { minValue, maxValue ->
                                    draftFilters = draftFilters.toMutableMap().apply {
                                        put("priceMin", minValue.toInt())
                                        put("priceMax", maxValue.toInt())
                                    }
                                }
                            )
                        }
                        FilterControlType.RANGE_PAIR -> {
                            RangeFilterField(
                                label = filter.label,
                                min = filter.range!!.min,
                                max = filter.range.max,
                                step = filter.range.step,
                                start = (draftFilters[filter.minKey] as? Int ?: filter.range.min.toInt()).toFloat(),
                                end = (draftFilters[filter.maxKey] as? Int ?: filter.range.max.toInt()).toFloat(),
                                suffix = " sq ft",
                                onRangeChange = { minValue, maxValue ->
                                    draftFilters = draftFilters.toMutableMap().apply {
                                        put(filter.minKey, minValue.toInt())
                                        put(filter.maxKey, maxValue.toInt())
                                    }
                                }
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        draftFilters = mutableMapOf(
                            "priceMin" to priceRange.min.toInt(),
                            "priceMax" to priceRange.max.toInt()
                        )
                        searchAreas = emptyList()
                        radiusKm = 1
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Clear")
                }
                Button(
                    onClick = {
                        val cleaned = buildMap<String, Any> {
                            draftFilters.forEach { (key, value) ->
                                when (value) {
                                    is String -> if (value.isNotBlank() && value != "Any") put(key, value)
                                    is Int -> put(key, value)
                                    is Float -> put(key, value.toInt())
                                }
                            }
                        }
                        onApply(cleaned, searchAreas, radiusKm)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryStart,
                        contentColor = Color.White
                    )
                ) {
                    Text("Apply Filters")
                }
            }
        }
    }
}

@Composable
private fun DropdownFilterField(
    label: String,
    options: List<String>,
    selectedValue: String,
    onValueSelected: (String) -> Unit
) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedValue)
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun RangeFilterField(
    label: String,
    min: Float,
    max: Float,
    step: Float,
    start: Float,
    end: Float,
    suffix: String = "",
    onRangeChange: (Float, Float) -> Unit
) {
    val formatter = remember { NumberFormat.getNumberInstance(Locale.ENGLISH) }
    var values by remember(start, end) { mutableStateOf(start..end) }

    LaunchedEffect(start, end) {
        values = start..end
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Text(
            "${formatter.format(values.start.toInt())}$suffix - ${formatter.format(values.endInclusive.toInt())}$suffix",
            color = PrimaryStart
        )
        RangeSlider(
            value = values,
            onValueChange = {
                values = snapRange(it, step, min, max)
                onRangeChange(values.start, values.endInclusive)
            },
            valueRange = min..max
        )
    }
}

private fun snapRange(range: ClosedFloatingPointRange<Float>, step: Float, min: Float, max: Float): ClosedFloatingPointRange<Float> {
    fun snap(value: Float): Float {
        val snapped = ((value - min) / step).toInt() * step + min
        return snapped.coerceIn(min, max)
    }
    val start = snap(range.start)
    val end = snap(range.endInclusive)
    return if (start <= end) start..end else end..start
}

private fun getFilterDefinitions(selectedPropertyType: String, priceRange: HomePriceRange): List<FilterDefinition> {
    val builtUpRange = HomePriceRange(0f, 20_000f, 100f)
    val plotAreaRange = HomePriceRange(0f, 100_000f, 500f)
    return when (selectedPropertyType) {
        HOME_PROPERTY_TYPE_RESIDENTIAL -> listOf(
            FilterDefinition("residentialType", "Residential Type", FilterControlType.OPTIONS, listOf("Any", "Flat", "Individual House", "Villa", "Bungalow", "Farmhouse", "Apartment", "PG/Hostel")),
            FilterDefinition("bhkType", "BHK Type", FilterControlType.OPTIONS, listOf("Any", "1 BHK", "1.5 BHK", "2 BHK", "2.5 BHK", "3 BHK", "3.5 BHK", "4 BHK", "4+ BHK")),
            FilterDefinition("possessionStatus", "Property Status", FilterControlType.OPTIONS, listOf("Any", "Ready Possession", "Under Construction")),
            FilterDefinition("hasParking", "Parking", FilterControlType.OPTIONS, listOf("Any", "Yes", "No")),
            FilterDefinition("price", "Price Range", FilterControlType.RANGE, range = priceRange),
            FilterDefinition("builtUpArea", "Built-up Area Range", FilterControlType.RANGE_PAIR, minKey = "builtUpAreaMin", maxKey = "builtUpAreaMax", range = builtUpRange),
            FilterDefinition("furnishing", "Furnishing", FilterControlType.OPTIONS, listOf("Any", "Fully Furnished", "Semi-Furnished", "Unfurnished")),
            FilterDefinition("facing", "Facing", FilterControlType.OPTIONS, listOf("Any", "North", "East", "West", "South", "North-East", "North-West", "South-East", "South-West"))
        )
        HOME_PROPERTY_TYPE_COMMERCIAL -> listOf(
            FilterDefinition("price", "Price Range", FilterControlType.RANGE, range = priceRange),
            FilterDefinition("commercialType", "Commercial Type", FilterControlType.OPTIONS, listOf("Any", "Office Space", "Retail Shop", "Showroom", "Restaurant", "Cafe", "Other")),
            FilterDefinition("builtUpArea", "Built-up Area Range", FilterControlType.RANGE_PAIR, minKey = "builtUpAreaMin", maxKey = "builtUpAreaMax", range = builtUpRange),
            FilterDefinition("possessionStatus", "Possession Status", FilterControlType.OPTIONS, listOf("Any", "Ready to Move", "Under Construction")),
            FilterDefinition("parkingType", "Parking Type", FilterControlType.OPTIONS, listOf("Any", "Private", "Public")),
            FilterDefinition("washroomType", "Washroom Type", FilterControlType.OPTIONS, listOf("Any", "Private", "Public"))
        )
        HOME_PROPERTY_TYPE_INDUSTRIAL -> listOf(
            FilterDefinition("price", "Price Range", FilterControlType.RANGE, range = priceRange),
            FilterDefinition("industrialType", "Industrial Type", FilterControlType.OPTIONS, listOf("Any", "Warehouse", "Plot", "Industrial Shed")),
            FilterDefinition("plotArea", "Plot Area Range", FilterControlType.RANGE_PAIR, minKey = "plotAreaMin", maxKey = "plotAreaMax", range = plotAreaRange),
            FilterDefinition("builtUpArea", "Built-up Area Range", FilterControlType.RANGE_PAIR, minKey = "builtUpAreaMin", maxKey = "builtUpAreaMax", range = builtUpRange),
            FilterDefinition("electricityLoad", "Min Electricity Load", FilterControlType.OPTIONS, listOf("Any", "Up to 50 KW", "50-100 KW", "100-500 KW", "500+ KW"))
        )
        else -> listOf(
            FilterDefinition("price", "Price Range", FilterControlType.RANGE, range = priceRange),
            FilterDefinition("landType", "Land Type", FilterControlType.OPTIONS, listOf("Any", "Residential Plot", "Commercial Plot", "Industrial Plot", "Agricultural Land", "Farm Land", "NA Plot")),
            FilterDefinition("propertyArea", "Area Range", FilterControlType.RANGE_PAIR, minKey = "propertyAreaMin", maxKey = "propertyAreaMax", range = plotAreaRange),
            FilterDefinition("landStatus", "Land Status", FilterControlType.OPTIONS, listOf("Any", "Clear Title", "Litigation", "Under Development", "Ready for Construction")),
            FilterDefinition("roadWidth", "Min Road Width", FilterControlType.OPTIONS, listOf("Any", "Less than 20 ft", "20-30 ft", "30-40 ft", "40-60 ft", "60+ ft")),
            FilterDefinition("landFacing", "Facing", FilterControlType.OPTIONS, listOf("Any", "North", "South", "East", "West", "North-East", "North-West", "South-East", "South-West"))
        )
    }
}

@Composable
private fun rememberPlacesClient(context: Context): PlacesClient? {
    return remember(context) {
        runCatching {
            if (!Places.isInitialized()) null else Places.createClient(context)
        }.getOrNull()
    }
}

private suspend fun fetchSearchArea(
    placesClient: PlacesClient?,
    sessionToken: AutocompleteSessionToken,
    placeId: String
): HomeSearchArea? {
    if (placesClient == null) return null
    val request = FetchPlaceRequest.builder(
        placeId,
        listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.LAT_LNG
        )
    ).setSessionToken(sessionToken).build()
    val place = placesClient.fetchPlace(request).await().place
    return HomeSearchArea(
        placeId = place.id,
        displayName = place.name.orEmpty(),
        formattedAddress = place.address.orEmpty(),
        lat = place.latLng?.latitude,
        lng = place.latLng?.longitude
    )
}

@Composable
private fun EmptyPropertyContent(
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No Properties Found",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "There are no active properties available right now.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry, shape = RoundedCornerShape(12.dp)) {
            Text("Retry")
        }
    }
}

@Composable
private fun ErrorPropertyContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Failed to load properties",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        Button(onClick = onRetry, shape = RoundedCornerShape(12.dp)) {
            Text("Retry")
        }
    }
}
