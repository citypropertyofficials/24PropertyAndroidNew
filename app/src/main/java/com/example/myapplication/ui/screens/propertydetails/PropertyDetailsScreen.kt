package com.example.myapplication.ui.screens.propertydetails

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.data.model.Property
import com.example.myapplication.ui.theme.PrimaryStart
import org.koin.androidx.compose.koinViewModel

private fun normalizeAmenityLabel(value: String): String =
    if (value == "Gated Community") "Gated Security" else value

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyDetailsScreen(
    propertyId: String,
    onBack: () -> Unit,
    viewModel: PropertyDetailsViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val interestedIds by viewModel.interestedIds.collectAsState()
    val isInterestedLoading by viewModel.isInterestedLoading.collectAsState()
    val currentImageIndex by viewModel.currentImageIndex.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(propertyId) { viewModel.loadProperty(propertyId) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PropertyDetailsEvent.ShowMessage ->
                    snackbarHostState.showSnackbar(event.message)
                is PropertyDetailsEvent.DialNumber -> {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${event.phoneNumber}"))
                    context.startActivity(intent)
                }
                is PropertyDetailsEvent.OpenWhatsApp -> {
                    val url = "https://wa.me/${event.phoneNumber}?text=${Uri.encode(event.message)}"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            when (val state = uiState) {
                is PropertyDetailsUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is PropertyDetailsUiState.Error -> ErrorContent(state.message, onBack)
                is PropertyDetailsUiState.Loaded -> {
                    val property = state.property
                    PropertyDetailsContent(
                        property = property,
                        isFavorite = favoriteIds.contains(property.id),
                        isInterested = interestedIds.contains(property.id),
                        isInterestedLoading = isInterestedLoading,
                        isOwner = property.owner == viewModel.currentUserId,
                        currentImageIndex = currentImageIndex,
                        onBack = onBack,
                        onPrevImage = { viewModel.prevImage() },
                        onNextImage = { viewModel.nextImage() },
                        onToggleFavorite = { viewModel.toggleFavorite(property.id) },
                        onToggleInterested = { viewModel.toggleInterested(property.id) },
                        onCall = { viewModel.callOwner() },
                        onWhatsApp = { viewModel.whatsappOwner() }
                    )
                }
            }
        }
    }
}

@Composable
private fun PropertyDetailsContent(
    property: Property,
    isFavorite: Boolean,
    isInterested: Boolean,
    isInterestedLoading: Boolean,
    isOwner: Boolean,
    currentImageIndex: Int,
    onBack: () -> Unit,
    onPrevImage: () -> Unit,
    onNextImage: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleInterested: () -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit
) {
    val images = property.images.ifEmpty { listOf("") }

    LazyColumn(Modifier.fillMaxSize()) {
        // ── Hero Image ────────────────────────────────────────────
        item {
            Box(Modifier.fillMaxWidth().height(280.dp)) {
                AsyncImage(
                    model = images[currentImageIndex].ifBlank { property.displayImage },
                    contentDescription = property.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Black.copy(0.3f), Color.Transparent, Color.Black.copy(0.55f)))
                    )
                )
                // Back
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(12.dp).align(Alignment.TopStart)
                        .clip(CircleShape).background(Color.Black.copy(0.5f)).size(40.dp)
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }

                // Favorite
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.padding(12.dp).align(Alignment.TopEnd)
                        .clip(CircleShape).background(Color.Black.copy(0.5f)).size(40.dp)
                ) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        "Fav",
                        tint = if (isFavorite) Color(0xFFE53935) else Color.White
                    )
                }

                // Prev / Next arrows
                if (images.size > 1) {
                    if (currentImageIndex > 0) {
                        IconButton(
                            onClick = onPrevImage,
                            modifier = Modifier.align(Alignment.CenterStart).padding(8.dp)
                                .clip(CircleShape).background(Color.Black.copy(0.4f)).size(36.dp)
                        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Prev", tint = Color.White, modifier = Modifier.size(24.dp)) }
                    }
                    if (currentImageIndex < images.size - 1) {
                        IconButton(
                            onClick = onNextImage,
                            modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp)
                                .clip(CircleShape).background(Color.Black.copy(0.4f)).size(36.dp)
                        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next", tint = Color.White, modifier = Modifier.size(24.dp)) }
                    }
                    // Counter
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(12.dp)
                            .clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(0.6f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) { Text("${currentImageIndex + 1}/${images.size}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                }

                // Listing badge
                Box(
                    Modifier.align(Alignment.BottomStart).padding(12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (property.listingType == "rent") Color(0xFF0288D1) else Color(0xFF388E3C))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        if (property.listingType == "rent") "For Rent" else "For Sale",
                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ── Body ──────────────────────────────────────────────────
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {

                // Title & Price Row
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        val isBrokerProperty = property.ownerRole.lowercase() == "broker"
                        Text(property.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
                        if (!isBrokerProperty && property.displayLocation.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Icon(Icons.Filled.LocationOn, null, tint = PrimaryStart, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(property.displayLocation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(property.displayPrice, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PrimaryStart)
                        if (property.uniqueId.isNotBlank())
                            Text("#${property.uniqueId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Type badge
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(PrimaryStart.copy(0.1f)).padding(horizontal = 12.dp, vertical = 5.dp)
                ) { Text(property.propertyTypeDisplay, color = PrimaryStart, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }

                Spacer(Modifier.height(16.dp))

                // Quick chips (only icon-safe icons)
                val chips = mutableListOf<Pair<ImageVector, String>>()
                if (property.bhkConfig.isNotBlank()) chips += Icons.Filled.Home to "${property.bhkConfig} BHK"
                if (property.builtUpArea.isNotBlank()) chips += Icons.Filled.Star to "${property.builtUpArea} sq ft"
                if (property.tenantType.isNotBlank()) chips += Icons.Filled.Person to property.tenantType
                if (property.availableFrom.isNotBlank()) chips += Icons.Filled.Info to property.availableFrom

                if (chips.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(chips) { (icon, label) -> FeatureChip(icon, label) }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Description
                SectionCard {
                    SectionHeader(Icons.Filled.Info, "Description")
                    Text(
                        property.description.ifBlank { "No description available." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
                Spacer(Modifier.height(12.dp))

                // Amenities
                if (property.amenities.isNotEmpty()) {
                    SectionCard {
                        SectionHeader(Icons.Filled.Check, "Amenities")
                        val rows = property.amenities.map(::normalizeAmenityLabel).chunked(2)
                        rows.forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { amenity -> AmenityChip(amenity, Modifier.weight(1f)) }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Contact
                SectionCard {
                    SectionHeader(Icons.Filled.Person, "Contact Owner")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onCall,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryStart),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Phone, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Call Now", fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = onWhatsApp,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Send, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("WhatsApp", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // Interested Button — hidden if owner
                if (!isOwner) {
                    SectionCard {
                        SectionHeader(Icons.Filled.Star, "Interested")
                        InterestedToggleButton(
                            isInterested = isInterested,
                            isLoading = isInterestedLoading,
                            interestedCount = property.interestedCount,
                            onClick = onToggleInterested
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Detail Sections
                DetailSection("Type & Category", Icons.Filled.Home, listOfNotNull(
                    field("Property Type", property.propertyTypeDisplay),
                    field("Residential Type", property.residentialType),
                    field("BHK Config", property.bhkConfig),
                    field("Commercial Type", property.commercialType),
                    field("Industrial Type", property.industrialType),
                    field("Land Type", property.landType)
                ))
                DetailSection("Location & Building", Icons.Filled.LocationOn, listOfNotNull(
                    field(if (property.propertyType == "industrial") "Property Name" else "Building/Society", property.buildingName),
                    field("Locality", property.locality),
                    field("Zone Type", property.zoneType),
                    field("Location Hub", property.locationHub)
                ))
                val showingFields = listOfNotNull(
                    field("Possession Status", property.possessionStatus),
                    field("Available From", property.availableFrom),
                    field("Availability Schedule", property.showingAvailability)
                )
                val splitShowingFields = mutableListOf<Pair<String, String>>()
                val hasSplit = property.showingStartTime.isNotBlank() || property.showingEndTime.isNotBlank()
                if (hasSplit) {
                    if (property.showingStartTime.isNotBlank()) splitShowingFields += field("Start Time", property.showingStartTime)!!
                    if (property.showingEndTime.isNotBlank()) splitShowingFields += field("End Time", property.showingEndTime)!!
                } else if (property.showingDateTime.isNotBlank()) {
                    splitShowingFields += field("Property Showing Date & Time", property.showingDateTime)!!
                }
                DetailSection("Status & Availability", Icons.Filled.Info, showingFields + splitShowingFields)
                DetailSection("Property & Legal", Icons.Filled.Star, listOfNotNull(
                    field("Property Condition", property.propertyCondition),
                    field("Ownership", property.ownership),
                    field("Plot Area", property.plotArea, property.plotAreaUnit.ifBlank { "sq ft" }),
                    field("Built-up Area", property.builtUpArea, property.builtUpAreaUnit.ifBlank { "sq ft" }),
                    field("Carpet Area", property.carpetArea, property.carpetAreaUnit.ifBlank { "sq ft" }),
                    field("Total Construction", property.totalConstructionArea, "sq ft"),
                    field("Frontage", property.frontage, "ft"),
                    field("Road Access", property.roadAccess, "ft")
                ))

                if (property.propertyType == "residential") {
                    DetailSection("Configuration", Icons.Filled.Home, listOfNotNull(
                        field("Property Age", property.propertyAge),
                        field("Floor Number", property.floorNumber),
                        field("Bathrooms", property.bathrooms),
                        field("Balconies", property.balconies),
                        field("Furnishing", property.furnishing)
                    ))
                    DetailSection("Parking", Icons.Filled.Share, listOfNotNull(
                        field("Covered Parking", property.coveredParking),
                        field("Open Parking", property.openParking),
                        field("Parking Charges", property.parkingCharges)
                    ))
                    DetailSection("Tenancy", Icons.Filled.Person, listOfNotNull(
                        field("Preferred Tenant", property.tenantType),
                        field("Pet Friendly", property.petFriendly),
                        field("Maintenance", property.maintenanceCharges)
                    ))
                }

                if (property.propertyType == "industrial") {
                    DetailSection("Industrial / Shed", Icons.Filled.Star, listOfNotNull(
                        field("Shed Height", property.shedHeight, "ft"),
                        field("Side Wall Height", property.shedSideWallHeight, "ft"),
                        field("Plot Dimensions", property.plotDimensions),
                        field("Built-up Area (Shed)", property.shedBuiltUpArea, property.shedBuiltUpAreaUnit.ifBlank { "sq ft" }),
                        field("Built-up Construction Area", property.builtUpConstructionArea, property.builtUpConstructionAreaUnit.ifBlank { "sq ft" }),
                        field("Electricity Load", property.electricityLoad),
                        field("Water Available", property.waterAvailable)
                    ))
                }

                if (property.propertyType == "land") {
                    DetailSection("Land Features", Icons.Filled.LocationOn, listOfNotNull(
                        field("Area (Acres)", property.areaAcres, "acres"),
                        field("Plot Length", property.plotLength, "ft"),
                        field("Plot Width", property.plotBreadth, "ft"),
                        field("Land Facing", property.landFacing),
                        field("Road Width", property.roadWidth, "ft"),
                        field("Land Status", property.landStatus)
                    ))
                }

                DetailSection("Floors & Elevation", Icons.Filled.Home, listOfNotNull(
                    field("Your Floor", property.yourFloor),
                    field("Total Floors", property.totalFloors),
                    field("Passenger Lift", property.passengerLift),
                    field("Service Lift", property.serviceLift)
                ))
                DetailSection("Lease & Financials", Icons.Filled.Star, listOfNotNull(
                    field("Price Negotiable", property.priceNegotiable),
                    field("Rent Negotiable", property.rentNegotiable),
                    field("Security Deposit", property.securityDeposit),
                    field("Rent Increase", property.rentIncrease),
                    field("Lock-in Period", property.lockInPeriod)
                ))
                DetailSection("Charges & Inclusions", Icons.Filled.Info, listOfNotNull(
                    field("DAMP UPS Included", property.dampUpsIncluded),
                    field("Electricity Included", property.electricityIncluded),
                    field("Water Charges", property.waterChargesIncluded)
                ))

                // Stats card
                Spacer(Modifier.height(12.dp))
                SectionCard {
                    SectionHeader(Icons.Filled.Info, "Property Stats")
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("Property ID", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(PrimaryStart).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text("#${property.uniqueId.ifBlank { property.id.take(8).uppercase() }}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("Listing Type", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(if (property.listingType == "rent") Color(0xFF0288D1) else Color(0xFF388E3C))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(if (property.listingType == "rent") "For Rent" else "For Sale", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        content = { Column(Modifier.padding(16.dp), content = content) }
    )
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
        Icon(icon, null, tint = PrimaryStart, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FeatureChip(icon: ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(icon, null, tint = PrimaryStart, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AmenityChip(label: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(end = 6.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(PrimaryStart.copy(0.1f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(Icons.Filled.Check, null, tint = Color(0xFF43A047), modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = PrimaryStart, fontWeight = FontWeight.Medium)
    }
}

private fun field(label: String, value: String, unit: String = ""): Pair<String, String>? {
    if (value.isBlank()) return null
    return label to if (unit.isBlank()) value else "$value $unit"
}

@Composable
private fun DetailSection(title: String, icon: ImageVector, fields: List<Pair<String, String>>) {
    if (fields.isEmpty()) return
    Spacer(Modifier.height(12.dp))
    SectionCard {
        SectionHeader(icon, title)
        fields.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) -> DetailFieldItem(label, value, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DetailFieldItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
            .padding(10.dp)
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ErrorContent(message: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack, shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Go Back")
        }
    }
}

@Composable
private fun InterestedToggleButton(
    isInterested: Boolean,
    isLoading: Boolean,
    interestedCount: Int,
    onClick: () -> Unit
) {
    val bgColor = if (isInterested) Color(0xFFFFA726).copy(alpha = 0.15f) else Color(0xFFF5F5F5)
    val contentColor = if (isInterested) Color(0xFFE65100) else Color(0xFF757575)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(enabled = !isLoading) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.5.dp,
                color = contentColor
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = if (isInterested) Color(0xFFFFA726) else Color(0xFFBDBDBD),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isInterested) {
                stringResource(R.string.interested)
            } else {
                stringResource(R.string.mark_interested)
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isInterested) FontWeight.Bold else FontWeight.Medium,
            color = contentColor
        )
        if (interestedCount > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "($interestedCount)",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
