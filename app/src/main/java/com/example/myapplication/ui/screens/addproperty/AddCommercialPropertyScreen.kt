package com.example.myapplication.ui.screens.addproperty

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.common.AppFullScreenLoading
import com.example.myapplication.ui.common.AppOutlinedButton
import com.example.myapplication.ui.common.AppPrimaryButton
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCommercialPropertyScreen(
    onBack: () -> Unit,
    viewModel: AddCommercialPropertyViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AddCommercialPropertyEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                AddCommercialPropertyEvent.PropertySaved -> onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditMode) "Edit Commercial Property" else "Add Commercial Property") },
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
                    enabled = !state.isPublishing && !state.isSavingDraft && !state.isInitialLoading
                )
                AppPrimaryButton(
                    text = if (state.isEditMode) "Save Changes" else "Publish",
                    onClick = { viewModel.publish(context.contentResolver) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isPublishing && !state.isSavingDraft && !state.isInitialLoading,
                    isLoading = state.isPublishing
                )
            }
        }
    ) { padding ->
        if (state.isInitialLoading) {
            AppFullScreenLoading()
            return@Scaffold
        }

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
                    fieldValues = state.fieldValues,
                    fieldErrors = state.fieldErrors,
                    onValueChange = viewModel::updateField,
                    selectedLocation = state.selectedLocation,
                    mapsApiConfigured = state.mapsApiConfigured,
                    onLocationResolved = viewModel::updateLocation
                )
            }
            commercialSections.forEach { section ->
                item(section.title) {
                    PropertySectionCard(
                        section = section,
                        listingType = state.listingType,
                        fieldValues = state.fieldValues,
                        fieldErrors = state.fieldErrors,
                        amenities = state.amenities,
                        onValueChange = viewModel::updateField,
                        onToggleAmenity = viewModel::toggleAmenity
                    )
                }
            }
            item {
                ImagesSection(
                    existingImageUrls = state.existingImageUrls,
                    imageUris = state.imageUris,
                    onAddImages = { imageLauncher.launch("image/*") },
                    onRemoveExistingImage = viewModel::removeExistingImage,
                    onRemoveImage = viewModel::removeImage
                )
            }
        }
    }
}

@Composable
private fun BasicInfoSection(
    state: AddCommercialPropertyUiState,
    onValueChange: (String, String) -> Unit
) {
    SectionCard(title = "Basic Information") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(
                label = if (state.listingType == LISTING_TYPE_RENT) "Monthly Rent (₹)" else "Sale Price (₹)",
                value = state.fieldValues["propertyPrice"].orEmpty(),
                onValueChange = { onValueChange("propertyPrice", it) },
                isNumber = true,
                error = state.fieldErrors["propertyPrice"],
                required = true,
                modifier = Modifier.weight(1f)
            )
            CheckboxChoiceField(
                label = if (state.listingType == LISTING_TYPE_RENT) "Rent Negotiable" else "Price Negotiable",
                selectedValue = state.fieldValues[if (state.listingType == LISTING_TYPE_RENT) "rentNegotiable" else "priceNegotiable"].orEmpty().ifBlank { "No" },
                options = listOf("Yes" to "Yes", "No" to "No"),
                onSelected = {
                    onValueChange(if (state.listingType == LISTING_TYPE_RENT) "rentNegotiable" else "priceNegotiable", it)
                },
                modifier = Modifier.weight(1f)
            )
        }
        AppTextField(
            label = "Description",
            value = state.fieldValues["description"].orEmpty(),
            onValueChange = { onValueChange("description", it) },
            minLines = 4
        )
    }
}
