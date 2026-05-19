package com.example.myapplication.ui.screens.addproperty

import android.annotation.SuppressLint
import android.content.Context
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myapplication.ui.common.AppOutlinedButton
import com.example.myapplication.ui.common.AppPrimaryButton
import com.example.myapplication.ui.theme.AccentColor
import com.example.myapplication.ui.theme.BackgroundPrimary
import com.example.myapplication.ui.theme.GoldStart
import com.example.myapplication.ui.theme.PrimaryEnd
import com.example.myapplication.ui.theme.PrimaryStart
import com.example.myapplication.ui.theme.TextSecondary
import com.example.myapplication.ui.theme.TextWhite
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.koin.androidx.compose.koinViewModel
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AddResidentialPropertyScreen(
    onBack: () -> Unit,
    viewModel: AddResidentialPropertyViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AddResidentialPropertyEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                AddResidentialPropertyEvent.PropertySaved -> onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Residential Property") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppOutlinedButton(
                    text = "Save Draft",
                    onClick = { viewModel.saveDraft(context.contentResolver) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isPublishing && !state.isSavingDraft
                )
                AppPrimaryButton(
                    text = "Publish",
                    onClick = { viewModel.publish(context.contentResolver) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isPublishing && !state.isSavingDraft,
                    isLoading = state.isPublishing
                )
            }
        }
    ) { padding ->
        val imageLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetMultipleContents()
        ) { uris ->
            if (!uris.isNullOrEmpty()) viewModel.setImages(uris)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ListingTypeSection(
                    selected = state.listingType,
                    onSelected = viewModel::updateListingType
                )
            }
            item {
                BasicInfoSection(state = state, onValueChange = viewModel::updateField)
            }
            item {
                LocationSection(
                    state = state,
                    onValueChange = viewModel::updateField,
                    onLocationResolved = viewModel::updateLocation
                )
            }
            residentialSections.forEach { section ->
                item(section.title) {
                    ResidentialSectionCard(
                        section = section,
                        state = state,
                        onValueChange = viewModel::updateField,
                        onToggleAmenity = viewModel::toggleAmenity
                    )
                }
            }
            item {
                ImagesSection(
                    imageUris = state.imageUris,
                    onAddImages = { imageLauncher.launch("image/*") },
                    onRemoveImage = viewModel::removeImage
                )
            }
        }
    }
}

@Composable
private fun ListingTypeSection(selected: String, onSelected: (String) -> Unit) {
    SectionCard(title = "Listing Type") {
        CheckboxChoiceField(
            label = "Listing Type",
            selectedValue = selected,
            options = listOf("Rent" to LISTING_TYPE_RENT, "Sale" to LISTING_TYPE_SALE),
            onSelected = onSelected,
            required = true
        )
    }
}

@Composable
private fun BasicInfoSection(
    state: AddResidentialPropertyUiState,
    onValueChange: (String, String) -> Unit
) {
    SectionCard(title = "Basic Information") {
        AppTextField("Building/Society/Apartment Name", state.fieldValues["propertyAddress"].orEmpty(), { onValueChange("propertyAddress", it) }, error = state.fieldErrors["propertyAddress"], required = true)
        Spacer(Modifier.height(12.dp))
        AppTextField("Monthly Rent / Sale Price (₹)", state.fieldValues["propertyPrice"].orEmpty(), { onValueChange("propertyPrice", it) }, isNumber = true, error = state.fieldErrors["propertyPrice"], required = true)
        Spacer(Modifier.height(12.dp))
        AppTextField("Description", state.fieldValues["description"].orEmpty(), { onValueChange("description", it) }, minLines = 4)
    }
}

@Composable
private fun LocationSection(
    state: AddResidentialPropertyUiState,
    onValueChange: (String, String) -> Unit,
    onLocationResolved: (SelectedLocation, Map<String, String>) -> Unit
) {
    SectionCard(title = "Location Information") {
        AppTextField("State", state.fieldValues["state"].orEmpty(), { onValueChange("state", it) }, error = state.fieldErrors["state"], required = true)
        Spacer(Modifier.height(12.dp))
        AppTextField("Dist/City", state.fieldValues["city"].orEmpty(), { onValueChange("city", it) }, error = state.fieldErrors["city"], required = true)
        Spacer(Modifier.height(12.dp))
        AppTextField("Name/Street/Area", state.fieldValues["nameStreetArea"].orEmpty(), { onValueChange("nameStreetArea", it) }, error = state.fieldErrors["nameStreetArea"], required = true)
        Spacer(Modifier.height(12.dp))
        AppTextField("Landmark/Popular/Nearby", state.fieldValues["landmark"].orEmpty(), { onValueChange("landmark", it) }, error = state.fieldErrors["landmark"], required = true)
        Spacer(Modifier.height(16.dp))
        GoogleLocationPicker(
            selectedLocation = state.selectedLocation,
            error = state.fieldErrors["mapLocation"],
            mapsApiConfigured = state.mapsApiConfigured,
            onLocationResolved = onLocationResolved
        )
    }
}

@Composable
private fun ResidentialSectionCard(
    section: ResidentialFormSection,
    state: AddResidentialPropertyUiState,
    onValueChange: (String, String) -> Unit,
    onToggleAmenity: (String, Boolean) -> Unit
) {
    SectionCard(title = section.title) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            section.fields.forEach { field ->
                if (!isFieldVisible(field, state.listingType, state.fieldValues)) return@forEach
                when (field.type) {
                    FormFieldType.TEXT -> AppTextField(field.label, state.fieldValues[field.id].orEmpty(), { onValueChange(field.id, it) }, placeholder = field.placeholder, error = state.fieldErrors[field.id], required = field.required)
                    FormFieldType.NUMBER -> AppTextField(field.label, state.fieldValues[field.id].orEmpty(), { onValueChange(field.id, it) }, placeholder = field.placeholder, isNumber = true, error = state.fieldErrors[field.id], required = field.required)
                    FormFieldType.SELECT -> {
                        if (field.options.size == 2) {
                            CheckboxChoiceField(
                                label = field.label,
                                selectedValue = state.fieldValues[field.id].orEmpty(),
                                options = field.options.map { it to it },
                                onSelected = { onValueChange(field.id, it) },
                                required = field.required,
                                error = state.fieldErrors[field.id]
                            )
                        } else {
                            DropdownField(field.label, state.fieldValues[field.id].orEmpty(), field.options, { onValueChange(field.id, it) }, required = field.required, error = state.fieldErrors[field.id])
                        }
                    }
                    FormFieldType.RADIO -> CheckboxChoiceField(
                        label = field.label,
                        selectedValue = state.fieldValues[field.id].orEmpty(),
                        options = field.options.map { it to it },
                        onSelected = { onValueChange(field.id, it) },
                        required = field.required,
                        error = state.fieldErrors[field.id]
                    )
                    FormFieldType.DATETIME -> DateTimePickerField(
                        label = field.label,
                        value = state.fieldValues[field.id].orEmpty(),
                        onValueChange = { onValueChange(field.id, it) },
                        required = field.required,
                        error = state.fieldErrors[field.id]
                    )
                    FormFieldType.AMENITY -> Unit
                }
            }
            if (section.fields.any { it.type == FormFieldType.AMENITY }) {
                ChipRows(items = section.fields.map { it.label }, columns = 2) { amenity ->
                    AmenityField(
                        label = amenity,
                        selected = state.amenities.contains(amenity)
                    ) { selected ->
                        onToggleAmenity(amenity, selected)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImagesSection(
    imageUris: List<Uri>,
    onAddImages: () -> Unit,
    onRemoveImage: (Uri) -> Unit
) {
    SectionCard(title = "Property Images") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onAddImages) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add Images")
            }
            Text("${imageUris.size}/$MAX_PROPERTY_IMAGES selected", color = TextSecondary)
        }
        if (imageUris.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            ChunkedRows(items = imageUris, columns = 3) { uri ->
                Box {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(92.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable { onRemoveImage(uri) }
                            .padding(4.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> ChunkedRows(
    items: List<T>,
    columns: Int,
    itemContent: @Composable (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { item ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        itemContent(item)
                    }
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ChipRows(
    items: List<String>,
    columns: Int,
    chip: @Composable (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { item ->
                    Box(modifier = Modifier.weight(1f)) {
                        chip(item)
                    }
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = PrimaryEnd)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun AppTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isNumber: Boolean = false,
    error: String? = null,
    required: Boolean = false,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(if (required) "$label *" else label) },
        placeholder = if (placeholder.isBlank()) null else ({ Text(placeholder) }),
        supportingText = if (error != null) ({ Text(error) }) else null,
        isError = error != null,
        minLines = minLines,
        keyboardOptions = if (isNumber) KeyboardOptions.Default.copy(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number) else KeyboardOptions.Default
    )
}

@Composable
private fun DropdownField(
    label: String,
    selectedValue: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    required: Boolean = false,
    error: String? = null
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (required) "$label *" else label, style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectedValue.ifBlank { "Select $label" })
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }
}

@Composable
private fun DateTimePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    required: Boolean = false,
    error: String? = null
) {
    val context = LocalContext.current
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US) }
    val displayFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (required) "$label *" else label, style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
            onClick = {
                showDateTimePicker(
                    context = context,
                    currentValue = value,
                    formatter = formatter,
                    onValueSelected = onValueChange
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (value.isBlank()) "Select $label"
                else runCatching { displayFormatter.format(formatter.parse(value)!!) }.getOrDefault(value)
            )
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }
}

private fun showDateTimePicker(
    context: Context,
    currentValue: String,
    formatter: SimpleDateFormat,
    onValueSelected: (String) -> Unit
) {
    val calendar = Calendar.getInstance()
    runCatching { formatter.parse(currentValue) }.getOrNull()?.let { parsed ->
        calendar.time = parsed
    }

    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val pickedCalendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                set(Calendar.HOUR_OF_DAY, calendar.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, calendar.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    pickedCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    pickedCalendar.set(Calendar.MINUTE, minute)
                    onValueSelected(formatter.format(pickedCalendar.time))
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                false
            ).show()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

@Composable
private fun CheckboxChoiceField(
    label: String,
    selectedValue: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
    required: Boolean = false,
    error: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (required) "$label *" else label, style = MaterialTheme.typography.titleMedium)
        options.forEach { (optionLabel, optionValue) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelected(optionValue) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = selectedValue == optionValue,
                    onCheckedChange = { checked ->
                        if (checked) onSelected(optionValue)
                    }
                )
                Text(
                    text = optionLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }
}

@Composable
private fun AmenityField(label: String, selected: Boolean, onToggle: (Boolean) -> Unit) {
    AssistChip(
        onClick = { onToggle(!selected) },
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp)) }
        } else {
            null
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) PrimaryStart.copy(alpha = 0.14f) else BackgroundPrimary,
            labelColor = if (selected) PrimaryEnd else MaterialTheme.colorScheme.onSurface
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = true,
            borderColor = if (selected) PrimaryStart else MaterialTheme.colorScheme.outline
        )
    )
}

@SuppressLint("MissingPermission")
@Composable
private fun GoogleLocationPicker(
    selectedLocation: SelectedLocation?,
    error: String?,
    mapsApiConfigured: Boolean,
    onLocationResolved: (SelectedLocation, Map<String, String>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val placesClient = remember(context) { Places.createClient(context) }
    val sessionToken = remember { AutocompleteSessionToken.newInstance() }
    val defaultLatLng = LatLng(selectedLocation?.lat ?: 19.0760, selectedLocation?.lng ?: 72.8777)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, if (selectedLocation == null) 5.5f else 16f)
    }
    var placesError by remember { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf(selectedLocation?.formattedAddress.orEmpty()) }
    var predictions by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val playServicesStatus = remember(context) {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
    }
    val playServicesReady = playServicesStatus == ConnectionResult.SUCCESS
    val mapInitReady = remember(context, playServicesReady) {
        if (!playServicesReady) {
            false
        } else {
            runCatching {
                MapsInitializer.initialize(context, MapsInitializer.Renderer.LATEST, null)
            }.isSuccess
        }
    }

    LaunchedEffect(defaultLatLng) {
        if (mapInitReady) {
            runCatching {
                cameraPositionState.move(
                    CameraUpdateFactory.newLatLngZoom(
                        defaultLatLng,
                        if (selectedLocation == null) 5.5f else 16f
                    )
                )
            }
        }
    }

    LaunchedEffect(selectedLocation?.formattedAddress) {
        if (!selectedLocation?.formattedAddress.isNullOrBlank()) {
            searchQuery = selectedLocation?.formattedAddress.orEmpty()
        }
    }

    LaunchedEffect(searchQuery, playServicesReady, mapInitReady) {
        if (!playServicesReady || !mapInitReady) return@LaunchedEffect
        val query = searchQuery.trim()
        if (query.length < 2) {
            predictions = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(250)
        if (query != searchQuery.trim()) return@LaunchedEffect
        runCatching {
            val request = FindAutocompletePredictionsRequest.builder()
                .setSessionToken(sessionToken)
                .setQuery(query)
                .setCountries(listOf("IN"))
                .build()
            placesClient.findAutocompletePredictions(request).await().autocompletePredictions
        }.onSuccess {
            predictions = it
            placesError = null
        }.onFailure {
            predictions = emptyList()
            placesError = "Google Places suggestions are unavailable. Check the API key restriction for package ${context.packageName}."
        }
        isSearching = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Map Search", style = MaterialTheme.typography.titleMedium)
        if (!mapsApiConfigured) {
            Text("Add `MAPS_API_KEY` to `local.properties` to enable Google Places and Maps in Android.", color = MaterialTheme.colorScheme.error)
            return
        }
        if (!playServicesReady) {
            Text("Google Play services is unavailable on this device/emulator, so map search is disabled.", color = MaterialTheme.colorScheme.error)
            return
        }
        if (!mapInitReady) {
            Text("Google Maps SDK failed to initialize. Check the Android-restricted API key for package `${context.packageName}` and its SHA-1/SHA-256.", color = MaterialTheme.colorScheme.error)
            return
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    placesError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search location on map") },
                placeholder = { Text("Type area, building, landmark, or locality") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (isSearching) {
                    {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    }
                } else null,
                singleLine = true
            )
            if (predictions.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        predictions.take(5).forEach { prediction ->
                            PredictionRow(
                                prediction = prediction,
                                onClick = {
                                    scope.launch {
                                        selectPrediction(
                                            placesClient = placesClient,
                                            prediction = prediction,
                                            sessionToken = sessionToken,
                                            onSelected = { location, suggestions ->
                                                predictions = emptyList()
                                                searchQuery = location.formattedAddress ?: prediction.getFullText(null).toString()
                                                placesError = null
                                                onLocationResolved(location, suggestions)
                                            },
                                            onError = {
                                                placesError = it
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selectedLocation != null) {
                AssistChip(
                    onClick = {},
                    label = { Text("Lat ${"%.5f".format(selectedLocation.lat)}, Lng ${"%.5f".format(selectedLocation.lng)}") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) }
                )
            }
        }

        Text("Map Location *", style = MaterialTheme.typography.titleMedium)
        Card(shape = RoundedCornerShape(20.dp)) {
            GoogleMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
                properties = MapProperties(isMyLocationEnabled = false),
                onMapClick = { latLng ->
                    scope.launch {
                        val suggestions = reverseGeocode(context, latLng)
                        val location = SelectedLocation(
                            lat = latLng.latitude,
                            lng = latLng.longitude,
                            formattedAddress = listOf(
                                suggestions["nameStreetArea"].orEmpty(),
                                suggestions["landmark"].orEmpty(),
                                suggestions["city"].orEmpty(),
                                suggestions["state"].orEmpty()
                            ).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null }
                        )
                        onLocationResolved(location, suggestions)
                    }
                }
            ) {
                selectedLocation?.let {
                    Marker(
                        state = MarkerState(position = LatLng(it.lat, it.lng)),
                        title = "Property location"
                    )
                }
            }
        }
        Text(
            text = selectedLocation?.formattedAddress ?: "Search a place or tap the map to set the exact property point.",
            color = if (selectedLocation == null) TextSecondary else MaterialTheme.colorScheme.onSurface
        )
        if (placesError != null) {
            Text(placesError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }
}

@Composable
private fun PredictionRow(
    prediction: AutocompletePrediction,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            prediction.getPrimaryText(null).toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        val secondary = prediction.getSecondaryText(null).toString()
        if (secondary.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                secondary,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

private suspend fun selectPrediction(
    placesClient: PlacesClient,
    prediction: AutocompletePrediction,
    sessionToken: AutocompleteSessionToken,
    onSelected: (SelectedLocation, Map<String, String>) -> Unit,
    onError: (String) -> Unit
) {
    runCatching {
        val request = FetchPlaceRequest.builder(
            prediction.placeId,
            listOf(
                Place.Field.ID,
                Place.Field.ADDRESS,
                Place.Field.NAME,
                Place.Field.LAT_LNG,
                Place.Field.ADDRESS_COMPONENTS
            )
        ).setSessionToken(sessionToken).build()
        placesClient.fetchPlace(request).await().place
    }.onSuccess { place ->
        val latLng = place.latLng
        if (latLng == null) {
            onError("Selected location did not return coordinates.")
            return@onSuccess
        }
        val location = SelectedLocation(
            lat = latLng.latitude,
            lng = latLng.longitude,
            placeId = place.id,
            formattedAddress = place.address
        )
        onSelected(location, buildPlaceFieldSuggestions(place))
    }.onFailure {
        onError("Unable to load that location. Check Places API access for this app.")
    }
}

private fun buildPlaceFieldSuggestions(place: Place): Map<String, String> {
    val components = place.addressComponents?.asList().orEmpty()
    fun find(vararg types: String): String {
        return components.firstOrNull { component -> types.any { it in component.types } }?.name.orEmpty()
    }
    val city = find("locality").ifBlank { find("administrative_area_level_2") }
    val street = listOf(find("route"), find("sublocality_level_1"), find("sublocality")).firstOrNull { it.isNotBlank() }.orEmpty()
    return buildMap {
        put("state", find("administrative_area_level_1"))
        put("city", city)
        put("nameStreetArea", street)
        put("landmark", place.name.orEmpty())
    }
}

private fun reverseGeocode(context: Context, latLng: LatLng): Map<String, String> {
    return try {
        val geocoder = Geocoder(context, Locale("en", "IN"))
        @Suppress("DEPRECATION")
        val address = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)?.firstOrNull()
        address?.toFieldMap().orEmpty()
    } catch (_: IOException) {
        emptyMap()
    }
}

private fun Address.toFieldMap(): Map<String, String> {
    return buildMap {
        put("state", adminArea.orEmpty())
        put("city", locality ?: subAdminArea.orEmpty())
        put("nameStreetArea", thoroughfare ?: subLocality.orEmpty())
        put("landmark", featureName.orEmpty())
    }
}
