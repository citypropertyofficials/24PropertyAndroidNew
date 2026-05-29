package com.example.myapplication.ui.screens.addproperty

import android.content.Context
import android.net.Uri
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myapplication.ui.theme.BorderColor
import com.example.myapplication.utils.IndianLocations
import com.example.myapplication.ui.theme.PrimaryEnd
import com.example.myapplication.ui.theme.PrimaryStart
import com.example.myapplication.ui.theme.TextPrimary
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier.shadow(3.dp, RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(PrimaryStart, PrimaryEnd)
                        ),
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    )
            )
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isNumber: Boolean = false,
    error: String? = null,
    required: Boolean = false,
    minLines: Int = 1,
    modifier: Modifier = Modifier
) {
    val displayLabel = if (required) "$label *" else label
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = {
            Text(
                displayLabel,
                color = if (error != null) MaterialTheme.colorScheme.error else TextSecondary
            )
        },
        placeholder = if (placeholder.isBlank()) null else ({ Text(placeholder, color = TextSecondary.copy(alpha = 0.6f)) }),
        supportingText = if (error != null) ({ Text(error, color = MaterialTheme.colorScheme.error) }) else null,
        isError = error != null,
        minLines = minLines,
        singleLine = minLines == 1,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryStart,
            focusedLabelColor = PrimaryStart,
            unfocusedBorderColor = BorderColor,
            errorBorderColor = MaterialTheme.colorScheme.error
        ),
        keyboardOptions = if (isNumber) {
            androidx.compose.foundation.text.KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
        } else {
            androidx.compose.foundation.text.KeyboardOptions.Default
        }
    )
}

@Composable
fun DropdownField(
    label: String,
    selectedValue: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    required: Boolean = false,
    error: String? = null
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = if (required) "$label *" else label,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Box {
            OutlinedTextField(
                value = selectedValue.ifBlank { "Select $label" },
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
                readOnly = true,
                enabled = false,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledBorderColor = if (error != null) MaterialTheme.colorScheme.error else BorderColor,
                    disabledTextColor = if (selectedValue.isBlank()) TextSecondary else TextPrimary,
                    disabledContainerColor = Color.White
                ),
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = if (error != null) MaterialTheme.colorScheme.error else TextSecondary
                    )
                },
                isError = error != null
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .matchParentSize()
                    .clickable { expanded = true }
            )
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White),
            shape = RoundedCornerShape(12.dp)
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
}

@Composable
fun DateTimePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    required: Boolean = false,
    error: String? = null
) {
    val context = LocalContext.current
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US) }
    val displayFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = if (required) "$label *" else label,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Box {
            val displayValue = if (value.isBlank()) {
                "Select $label"
            } else {
                runCatching { displayFormatter.format(formatter.parse(value)!!) }.getOrDefault(value)
            }
            OutlinedTextField(
                value = displayValue,
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showDateTimePicker(context, value, formatter, onValueChange)
                    },
                readOnly = true,
                enabled = false,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledBorderColor = if (error != null) MaterialTheme.colorScheme.error else BorderColor,
                    disabledTextColor = if (value.isBlank()) TextSecondary else TextPrimary,
                    disabledContainerColor = Color.White
                ),
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = if (error != null) MaterialTheme.colorScheme.error else TextSecondary
                    )
                },
                isError = error != null
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .matchParentSize()
                    .clickable {
                        showDateTimePicker(context, value, formatter, onValueChange)
                    }
            )
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }
}

fun showDateTimePicker(
    context: Context,
    currentValue: String,
    formatter: SimpleDateFormat,
    onValueSelected: (String) -> Unit
) {
    val calendar = Calendar.getInstance()
    runCatching { formatter.parse(currentValue) }.getOrNull()?.let { parsed ->
        calendar.time = parsed
    }

    android.app.DatePickerDialog(
        context,
        { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
            val pickedCalendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                set(Calendar.HOUR_OF_DAY, calendar.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, calendar.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            android.app.TimePickerDialog(
                context,
                { _: TimePicker, hourOfDay: Int, minute: Int ->
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
fun CheckboxChoiceField(
    label: String,
    selectedValue: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
    required: Boolean = false,
    error: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = if (required) "$label *" else label,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        ChipFlowRow(horizontalGap = 8.dp, verticalGap = 8.dp) {
            options.forEach { (optionLabel, optionValue) ->
                val isSelected = selectedValue == optionValue
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) PrimaryStart else Color.White,
                    label = "choice_bg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else TextPrimary,
                    label = "choice_text"
                )
                val borderColor by animateColorAsState(
                    targetValue = if (isSelected) PrimaryStart else BorderColor,
                    label = "choice_border"
                )
                val elevation by animateDpAsState(
                    targetValue = if (isSelected) 4.dp else 0.dp,
                    label = "choice_elevation"
                )

                Box(
                    modifier = Modifier
                        .shadow(elevation, RoundedCornerShape(999.dp))
                        .border(
                            width = 1.5.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(999.dp)
                        )
                        .background(bgColor, RoundedCornerShape(999.dp))
                        .clickable { onSelected(optionValue) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = optionLabel,
                        color = textColor,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }
}

@Composable
fun AmenityField(label: String, selected: Boolean, onToggle: (Boolean) -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) PrimaryStart else Color.White,
        label = "amenity_bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) Color.White else TextPrimary,
        label = "amenity_text"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) PrimaryStart else BorderColor,
        label = "amenity_border"
    )
    val elevation by animateDpAsState(
        targetValue = if (selected) 4.dp else 0.dp,
        label = "amenity_elevation"
    )

    Box(
        modifier = Modifier
            .shadow(elevation, RoundedCornerShape(999.dp))
            .border(
                width = 1.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(999.dp)
            )
            .background(bgColor, RoundedCornerShape(999.dp))
            .clickable { onToggle(!selected) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                color = textColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun ChipFlowRow(
    modifier: Modifier = Modifier,
    horizontalGap: androidx.compose.ui.unit.Dp = 8.dp,
    verticalGap: androidx.compose.ui.unit.Dp = 8.dp,
    content: @Composable () -> Unit
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val hGapPx = horizontalGap.roundToPx()
        val vGapPx = verticalGap.roundToPx()

        val placeables = subcompose(0, content).map { it.measure(constraints.copy(minWidth = 0)) }

        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0

        placeables.forEach { placeable ->
            if (currentRow.isEmpty()) {
                currentRow.add(placeable)
                currentRowWidth = placeable.width
            } else if (currentRowWidth + hGapPx + placeable.width <= constraints.maxWidth) {
                currentRow.add(placeable)
                currentRowWidth += hGapPx + placeable.width
            } else {
                rows.add(currentRow.toList())
                currentRow = mutableListOf(placeable)
                currentRowWidth = placeable.width
            }
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow.toList())
        }

        val height = rows.sumOf { row -> row.maxOfOrNull { it.height } ?: 0 } +
            (rows.size - 1).coerceAtLeast(0) * vGapPx

        layout(constraints.maxWidth, height) {
            var y = 0
            rows.forEach { row ->
                val rowHeight = row.maxOfOrNull { it.height } ?: 0
                var x = 0
                row.forEach { placeable ->
                    placeable.placeRelative(x, y + (rowHeight - placeable.height) / 2)
                    x += placeable.width + hGapPx
                }
                y += rowHeight + vGapPx
            }
        }
    }
}

@Composable
fun <T> ChunkedRows(
    items: List<T>,
    columns: Int,
    horizontalSpacing: androidx.compose.ui.unit.Dp = 10.dp,
    verticalSpacing: androidx.compose.ui.unit.Dp = 10.dp,
    itemContent: @Composable (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing)
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
fun ImagesSection(
    existingImageUrls: List<String>,
    imageUris: List<Uri>,
    onAddImages: () -> Unit,
    onRemoveExistingImage: (String) -> Unit,
    onRemoveImage: (Uri) -> Unit
) {
    SectionCard(title = "Property Images") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .border(
                            width = 1.5.dp,
                            color = BorderColor,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .clickable(onClick = onAddImages)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = PrimaryStart
                        )
                        Text(
                            "Add Images",
                            color = PrimaryStart,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    "${existingImageUrls.size + imageUris.size}/$MAX_PROPERTY_IMAGES selected",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }

            if (existingImageUrls.isNotEmpty()) {
                Text(
                    "Existing Images",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                ChunkedRows(items = existingImageUrls, columns = 3) { imageUrl ->
                    Box {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable { onRemoveExistingImage(imageUrl) }
                                .padding(5.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            if (imageUris.isNotEmpty()) {
                Text(
                    "New Images",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                ChunkedRows(items = imageUris, columns = 3) { uri ->
                    Box {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable { onRemoveImage(uri) }
                                .padding(5.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ListingTypeSection(selected: String, onSelected: (String) -> Unit) {
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
fun LocationSection(
    fieldValues: Map<String, String>,
    fieldErrors: Map<String, String>,
    onValueChange: (String, String) -> Unit,
    selectedLocation: SelectedLocation?,
    mapsApiConfigured: Boolean,
    onLocationResolved: (SelectedLocation, Map<String, String>) -> Unit
) {
    val currentStateName = fieldValues["state"].orEmpty()
    val districts = remember(currentStateName) {
        IndianLocations.getDistrictsForState(currentStateName)
    }

    // If current city is not in the districts list for the selected state, clear it
    val currentCity = fieldValues["city"].orEmpty()
    LaunchedEffect(districts, currentCity) {
        if (currentCity.isNotBlank() && districts.isNotEmpty() && districts.none { it.equals(currentCity, ignoreCase = true) }) {
            onValueChange("city", "")
        }
    }

    SectionCard(title = "Location Information") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DropdownField(
                label = "State",
                selectedValue = currentStateName,
                options = IndianLocations.allStateNames,
                onSelected = {
                    onValueChange("state", it)
                    onValueChange("city", "")
                },
                required = true,
                error = fieldErrors["state"]
            )
            DropdownField(
                label = "Dist/City",
                selectedValue = currentCity,
                options = if (districts.isEmpty()) listOf("Select State First") else districts,
                onSelected = { onValueChange("city", it) },
                required = true,
                error = fieldErrors["city"]
            )
            AppTextField(
                "Name/Street/Area",
                fieldValues["nameStreetArea"].orEmpty(),
                { onValueChange("nameStreetArea", it) },
                error = fieldErrors["nameStreetArea"],
                required = true
            )
            AppTextField(
                "Landmark/Popular/Nearby",
                fieldValues["landmark"].orEmpty(),
                { onValueChange("landmark", it) },
                error = fieldErrors["landmark"],
                required = true
            )
            Spacer(Modifier.height(4.dp))
            GoogleLocationPicker(
                selectedLocation = selectedLocation,
                error = fieldErrors["mapLocation"],
                mapsApiConfigured = mapsApiConfigured,
                onLocationResolved = onLocationResolved
            )
        }
    }
}

@Composable
fun PropertySectionCard(
    section: PropertyFormSection,
    listingType: String,
    fieldValues: Map<String, String>,
    fieldErrors: Map<String, String>,
    amenities: Set<String>,
    onValueChange: (String, String) -> Unit,
    onToggleAmenity: (String, Boolean) -> Unit
) {
    SectionCard(title = section.title) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            section.fields.forEach { field ->
                if (!isFieldVisible(field, listingType, fieldValues)) return@forEach
                when (field.type) {
                    FormFieldType.TEXT -> AppTextField(
                        field.label,
                        fieldValues[field.id].orEmpty(),
                        { onValueChange(field.id, it) },
                        placeholder = field.placeholder,
                        error = fieldErrors[field.id],
                        required = field.required
                    )
                    FormFieldType.NUMBER -> AppTextField(
                        field.label,
                        fieldValues[field.id].orEmpty(),
                        { onValueChange(field.id, it) },
                        placeholder = field.placeholder,
                        isNumber = true,
                        error = fieldErrors[field.id],
                        required = field.required
                    )
                    FormFieldType.SELECT -> {
                        if (field.options.size == 2) {
                            CheckboxChoiceField(
                                label = field.label,
                                selectedValue = fieldValues[field.id].orEmpty(),
                                options = field.options.map { it to it },
                                onSelected = { onValueChange(field.id, it) },
                                required = field.required,
                                error = fieldErrors[field.id]
                            )
                        } else {
                            DropdownField(
                                field.label,
                                fieldValues[field.id].orEmpty(),
                                field.options,
                                { onValueChange(field.id, it) },
                                required = field.required,
                                error = fieldErrors[field.id]
                            )
                        }
                    }
                    FormFieldType.RADIO -> CheckboxChoiceField(
                        label = field.label,
                        selectedValue = fieldValues[field.id].orEmpty(),
                        options = field.options.map { it to it },
                        onSelected = { onValueChange(field.id, it) },
                        required = field.required,
                        error = fieldErrors[field.id]
                    )
                    FormFieldType.DATETIME -> DateTimePickerField(
                        label = field.label,
                        value = fieldValues[field.id].orEmpty(),
                        onValueChange = { onValueChange(field.id, it) },
                        required = field.required,
                        error = fieldErrors[field.id]
                    )
                    FormFieldType.AMENITY -> Unit
                }
            }
            if (section.fields.any { it.type == FormFieldType.AMENITY }) {
                ChipFlowRow(horizontalGap = 8.dp, verticalGap = 8.dp) {
                    section.fields.map { it.label }.forEach { amenity ->
                        AmenityField(
                            label = amenity,
                            selected = amenities.contains(amenity)
                        ) { selected ->
                            onToggleAmenity(amenity, selected)
                        }
                    }
                }
            }
        }
    }
}

@android.annotation.SuppressLint("MissingPermission")
@Composable
fun GoogleLocationPicker(
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
            placesError = "Google Places suggestions are unavailable."
        }
        isSearching = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Map Search",
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        if (!mapsApiConfigured) {
            Text(
                "Add MAPS_API_KEY to local.properties to enable map search.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp
            )
            return
        }
        if (!playServicesReady) {
            Text(
                "Google Play services unavailable. Map search disabled.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp
            )
            return
        }
        if (!mapInitReady) {
            Text(
                "Google Maps SDK failed to initialize. Check API key.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp
            )
            return
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                placesError = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search location on map") },
            placeholder = { Text("Type area, building, landmark, or locality") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
            trailingIcon = if (isSearching) {
                {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryStart
                    )
                }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryStart,
                focusedLabelColor = PrimaryStart,
                unfocusedBorderColor = BorderColor
            )
        )

        if (predictions.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    predictions.take(5).forEachIndexed { index, prediction ->
                        PredictionRow(
                            prediction = prediction,
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        val request = FetchPlaceRequest.builder(
                                            prediction.placeId,
                                            listOf(Place.Field.LAT_LNG, Place.Field.ADDRESS)
                                        ).setSessionToken(sessionToken).build()
                                        val place = placesClient.fetchPlace(request).await().place
                                        val latLng = place?.latLng
                                        if (latLng != null) {
                                            val resolved = SelectedLocation(
                                                lat = latLng.latitude,
                                                lng = latLng.longitude,
                                                placeId = prediction.placeId,
                                                formattedAddress = place.address
                                            )
                                            onLocationResolved(
                                                resolved,
                                                buildPlaceFieldSuggestions(place)
                                            )
                                            searchQuery = place.address.orEmpty()
                                            predictions = emptyList()
                                            cameraPositionState.move(
                                                CameraUpdateFactory.newLatLngZoom(latLng, 16f)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                        if (index < predictions.size - 1) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .padding(horizontal = 14.dp)
                                    .background(BorderColor)
                            )
                        }
                    }
                }
            }
        }
        if (placesError != null) {
            Text(placesError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        if (selectedLocation != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(),
                    uiSettings = MapUiSettings(zoomControlsEnabled = false)
                ) {
                    Marker(
                        state = MarkerState(
                            position = LatLng(selectedLocation.lat, selectedLocation.lng)
                        ),
                        title = selectedLocation.formattedAddress
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .border(1.5.dp, BorderColor, RoundedCornerShape(16.dp))
                    .background(Color.White, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        "Search and select a location to view map",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
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

@Composable
private fun PredictionRow(
    prediction: AutocompletePrediction,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            prediction.getPrimaryText(null).toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
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
