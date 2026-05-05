package com.example.myapplication.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myapplication.data.model.Property
import com.example.myapplication.ui.theme.PrimaryStart

@Composable
fun PropertyCard(
    property: Property,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = property.displayImage,
                    contentDescription = property.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentScale = ContentScale.Crop
                )

                // Property Type Badge
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopStart)
                ) {
                    PropertyTypeBadge(propertyType = property.propertyType)
                }

                // Listing Type Badge
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.BottomStart)
                ) {
                    ListingTypeBadge(listingType = property.listingType)
                }
            }

            // Content
            Column(modifier = Modifier.padding(16.dp)) {
                // Property Name
                Text(
                    text = property.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Price
                Text(
                    text = property.displayPrice,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryStart
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Location
                val locationText = buildString {
                    if (property.location.isNotBlank()) append(property.location)
                    if (property.cityState.isNotBlank()) {
                        if (isNotEmpty()) append(", ")
                        append(property.cityState)
                    }
                }
                if (locationText.isNotBlank()) {
                    Text(
                        text = locationText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PropertyTypeBadge(propertyType: String) {
    val (label, color) = when (propertyType.lowercase()) {
        "residential" -> "Residential" to Color(0xFF4CAF50)
        "commercial" -> "Commercial" to Color(0xFF2196F3)
        "industrial" -> "Industrial" to Color(0xFFFF9800)
        "agricultural" -> "Agricultural" to Color(0xFF8BC34A)
        else -> propertyType.replaceFirstChar { it.uppercase() } to Color(0xFF607D8B)
    }

    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.9f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun ListingTypeBadge(listingType: String) {
    val (label, color) = if (listingType == "rent") {
        "For Rent" to Color(0xFF03A9F4)
    } else {
        "For Sale" to Color(0xFF4CAF50)
    }

    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.9f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
    )
}
