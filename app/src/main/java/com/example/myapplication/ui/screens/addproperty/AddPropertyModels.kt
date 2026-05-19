package com.example.myapplication.ui.screens.addproperty

import android.net.Uri
import com.example.myapplication.BuildConfig

const val RESIDENTIAL_PROPERTY_TYPE = "residential"
const val LISTING_TYPE_RENT = "rent"
const val LISTING_TYPE_SALE = "sale"
const val MAX_PROPERTY_IMAGES = 8

enum class FormFieldType {
    TEXT,
    NUMBER,
    SELECT,
    RADIO,
    DATETIME,
    AMENITY
}

data class FieldVisibilityRule(
    val fieldId: String,
    val values: Set<String>
)

data class ResidentialFieldDefinition(
    val id: String,
    val label: String,
    val type: FormFieldType,
    val options: List<String> = emptyList(),
    val placeholder: String = "",
    val required: Boolean = false,
    val listingTypes: Set<String> = emptySet(),
    val visibleWhen: FieldVisibilityRule? = null,
    val defaultValue: String = ""
)

data class ResidentialFormSection(
    val title: String,
    val fields: List<ResidentialFieldDefinition>
)

data class PropertyTypeOption(
    val id: String,
    val title: String,
    val description: String
)

data class SelectedLocation(
    val lat: Double,
    val lng: Double,
    val placeId: String? = null,
    val formattedAddress: String? = null
)

data class AddResidentialPropertyUiState(
    val listingType: String = LISTING_TYPE_RENT,
    val fieldValues: Map<String, String> = defaultResidentialFieldValues(),
    val amenities: Set<String> = emptySet(),
    val imageUris: List<Uri> = emptyList(),
    val selectedLocation: SelectedLocation? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val isSavingDraft: Boolean = false,
    val isPublishing: Boolean = false,
    val mapsApiConfigured: Boolean = BuildConfig.MAPS_API_KEY.isNotBlank()
)

sealed class AddResidentialPropertyEvent {
    data class ShowMessage(val message: String) : AddResidentialPropertyEvent()
    data object PropertySaved : AddResidentialPropertyEvent()
}

val propertyTypeOptions = listOf(
    PropertyTypeOption("residential", "Residential Property", "Houses, flats, villas, duplexes, and hostels."),
    PropertyTypeOption("commercial", "Commercial Property", "Shops, offices, showrooms, and workspaces."),
    PropertyTypeOption("industrial", "Industrial Property", "Sheds, factories, warehouses, and units."),
    PropertyTypeOption("land", "Land Property", "Plots, agriculture land, NA land, and development parcels.")
)

private val villaTypes = setOf("Individual House", "Villa", "Bungalow", "Farmhouse")

val residentialAmenities = listOf(
    "Swimming Pool", "Gym", "Garden", "Security", "Elevator", "Balcony", "Air Conditioning",
    "Heating", "Laundry", "Storage", "Internet", "Furnished", "Parking", "Power Backup",
    "Water Supply", "Waste Disposal", "Landscaping", "CCTV", "Gated Community", "Intercom",
    "Visitor Parking", "Gas Pipeline", "Club", "Playground", "House Keeping", "Fire Safety",
    "Children Play Area", "Servant Room", "Park"
)

val residentialSections = listOf(
    ResidentialFormSection(
        title = "Type & Category",
        fields = listOf(
            ResidentialFieldDefinition("residentialType", "Residential Type", FormFieldType.SELECT, listOf("Flat", "Individual House", "Villa", "Bungalow", "Farmhouse", "Studio", "Apartment", "Duplex", "Penthouse", "PG/Hostel"), defaultValue = "Flat"),
            ResidentialFieldDefinition("ownership", "Ownership", FormFieldType.SELECT, listOf("Self owned", "On lease"), listingTypes = setOf(LISTING_TYPE_SALE), defaultValue = "Self owned")
        )
    ),
    ResidentialFormSection(
        title = "Configuration & Area",
        fields = listOf(
            ResidentialFieldDefinition("bhkConfig", "BHK Configuration", FormFieldType.SELECT, listOf("1 BHK", "1.5 BHK", "2 BHK", "2.5 BHK", "3 BHK", "3.5 BHK", "4 BHK", "5 BHK+", "4+ BHK"), required = true, defaultValue = "2 BHK"),
            ResidentialFieldDefinition("possessionStatus", "Property Status", FormFieldType.SELECT, listOf("Ready Possession", "Under Construction"), required = true),
            ResidentialFieldDefinition("builtUpArea", "Built Up Area (sq ft)", FormFieldType.NUMBER, placeholder = "e.g., 1200", required = true),
            ResidentialFieldDefinition("builtUpAreaUnit", "Built Up Area Unit", FormFieldType.SELECT, listOf("sq ft", "sq mtr"), defaultValue = "sq ft"),
            ResidentialFieldDefinition("carpetArea", "Carpet Area (sq ft)", FormFieldType.NUMBER, placeholder = "e.g., 1000"),
            ResidentialFieldDefinition("carpetAreaUnit", "Carpet Area Unit", FormFieldType.SELECT, listOf("sq ft", "sq mtr"), defaultValue = "sq ft"),
            ResidentialFieldDefinition("propertyAge", "Age of Property", FormFieldType.SELECT, listOf("Less than 1 year", "1-3 years", "3-5 years", "5-10 years", "More than 10 years"), defaultValue = "Less than 1 year"),
            ResidentialFieldDefinition("totalFloors", "Total Floors in Building", FormFieldType.SELECT, listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "10-20", "20+"), defaultValue = "5"),
            ResidentialFieldDefinition("floorNumber", "Floor Number", FormFieldType.SELECT, listOf("Ground Floor", "1st Floor", "2nd Floor", "3rd Floor", "4th Floor", "5th Floor", "6th Floor", "7th Floor", "8th Floor", "9th Floor", "10th Floor", "11+ Floor"), defaultValue = "Ground Floor"),
            ResidentialFieldDefinition("floorType", "Floor Type", FormFieldType.SELECT, listOf("Cement", "Vitrified Tiles", "Natural Stone", "Mosaic", "Marble", "Granite", "Wooden")),
            ResidentialFieldDefinition("bathrooms", "Number of Bathrooms", FormFieldType.SELECT, listOf("1", "2", "3", "4+"), defaultValue = "2"),
            ResidentialFieldDefinition("balconies", "Number of Balconies", FormFieldType.SELECT, listOf("0", "1", "2", "3", "4+"), defaultValue = "1"),
            ResidentialFieldDefinition("furnishing", "Furnishing Status", FormFieldType.SELECT, listOf("Fully Furnished", "Semi-Furnished", "Unfurnished"), defaultValue = "Semi-Furnished")
        )
    ),
    ResidentialFormSection(
        title = "Parking & Accessibility",
        fields = listOf(
            ResidentialFieldDefinition("coveredParking", "Covered Parking", FormFieldType.SELECT, listOf("No", "Bike", "Car", "Car & Bike", "1 slot", "2 slots", "3 slots", "4+ slots"), defaultValue = "No"),
            ResidentialFieldDefinition("openParking", "Open Parking", FormFieldType.SELECT, listOf("No", "Bike", "Car", "Car & Bike", "1 slot (2-wheeler)", "1 slot (4-wheeler)", "2 slots", "3+ slots"), defaultValue = "No"),
            ResidentialFieldDefinition("parkingCharges", "Parking Charges", FormFieldType.SELECT, listOf("Included in rent", "Separate charges"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "Included in rent"),
            ResidentialFieldDefinition("liftAvailable", "Lift", FormFieldType.RADIO, listOf("Yes", "No"), defaultValue = "No")
        )
    ),
    ResidentialFormSection(
        title = "Tenancy Details",
        fields = listOf(
            ResidentialFieldDefinition("tenantType", "Preferred Tenant Type", FormFieldType.SELECT, listOf("Family", "Bachelors", "Company", "Student Boys/Girls", "Any One"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "Family"),
            ResidentialFieldDefinition("petFriendly", "Pet Friendly", FormFieldType.RADIO, listOf("Yes", "No"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "Yes"),
            ResidentialFieldDefinition("availableFrom", "Available From", FormFieldType.SELECT, listOf("Immediate", "Within 15 days", "Within 30 days", "After 30 days"), defaultValue = "Immediate"),
            ResidentialFieldDefinition("maintenanceCharges", "Maintenance Charges", FormFieldType.SELECT, listOf("Included in rent", "Separate charges"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "Included in rent"),
            ResidentialFieldDefinition("maintenanceAmount", "Monthly Maintenance Amount (₹)", FormFieldType.NUMBER, placeholder = "e.g., 2500"),
            ResidentialFieldDefinition("waterSupplyType", "Water Supply", FormFieldType.SELECT, listOf("Corporation", "Borewell", "Both", "Other")),
            ResidentialFieldDefinition("propertyShower", "Who Will Show This Property", FormFieldType.SELECT, listOf("Need help", "I will show", "Neighbours", "Friend", "Relative", "Security", "Tenants", "Other")),
            ResidentialFieldDefinition("showingDateTime", "Availability Date & Time", FormFieldType.DATETIME),
            ResidentialFieldDefinition("showingAvailability", "Availability Schedule", FormFieldType.SELECT, listOf("Weekday (Mon-Fri)", "Weekend (Sat-Sun)", "Every day (Mon-Sun)", "Available all day")),
            ResidentialFieldDefinition("currentSituation", "Current Situation of Property", FormFieldType.SELECT, listOf("Vacant", "Tenant", "Self occupied", "Sell urgent", "Not finding tenant"))
        )
    ),
    ResidentialFormSection(
        title = "Payments & Contracts",
        fields = listOf(
            ResidentialFieldDefinition("priceNegotiable", "Price Negotiable", FormFieldType.RADIO, listOf("Yes", "No"), defaultValue = "No"),
            ResidentialFieldDefinition("securityDeposit", "Security Deposit", FormFieldType.SELECT, listOf("None", "1 month", "2 months", "6 months", "Custom amount"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "1 month"),
            ResidentialFieldDefinition("lockInPeriod", "Lock-in Period", FormFieldType.SELECT, listOf("None", "1 month", "6 months", "Custom period"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "None"),
            ResidentialFieldDefinition("brokerageRequired", "Brokerage Required", FormFieldType.SELECT, listOf("No", "15 days rent", "30 days rent", "Custom amount"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "No"),
            ResidentialFieldDefinition("rentIncrease", "Expected Rent Increase (₹)", FormFieldType.NUMBER, placeholder = "e.g., 2000", listingTypes = setOf(LISTING_TYPE_RENT)),
            ResidentialFieldDefinition("nonVegAllowed", "Non-veg Allowed", FormFieldType.RADIO, listOf("Yes", "No"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "Yes"),
            ResidentialFieldDefinition("currentUnderLoan", "Current Under Loan", FormFieldType.RADIO, listOf("Yes", "No"), listingTypes = setOf(LISTING_TYPE_SALE), defaultValue = "No"),
            ResidentialFieldDefinition("commencementCertificate", "Commencement Certificate", FormFieldType.SELECT, listOf("Yes", "No", "Don't know"), listingTypes = setOf(LISTING_TYPE_SALE)),
            ResidentialFieldDefinition("occupancyCertificate", "Occupancy / Completion Certificate", FormFieldType.SELECT, listOf("Yes", "No", "Don't know"), listingTypes = setOf(LISTING_TYPE_SALE)),
            ResidentialFieldDefinition("possessionLetter", "Possession Letter", FormFieldType.SELECT, listOf("Yes", "No", "Don't know"), listingTypes = setOf(LISTING_TYPE_SALE)),
            ResidentialFieldDefinition("saleDeed", "Sale Deed", FormFieldType.SELECT, listOf("Yes", "No", "Don't know"), listingTypes = setOf(LISTING_TYPE_SALE)),
            ResidentialFieldDefinition("propertyTaxPaid", "Property Tax Paid", FormFieldType.SELECT, listOf("Yes", "No", "Don't know"), listingTypes = setOf(LISTING_TYPE_SALE)),
            ResidentialFieldDefinition("societyFormation", "Society Formation / Apartment", FormFieldType.TEXT, placeholder = "e.g., Formed Society", listingTypes = setOf(LISTING_TYPE_SALE))
        )
    ),
    ResidentialFormSection(
        title = "Room & Facilities",
        fields = listOf(
            ResidentialFieldDefinition("servantRoom", "Servant Room Available", FormFieldType.RADIO, listOf("Yes", "No"), defaultValue = "No"),
            ResidentialFieldDefinition("facing", "Property Facing", FormFieldType.SELECT, listOf("Don't Know", "North", "East", "West", "South", "North-East", "North-West", "South-East", "South-West"), defaultValue = "Don't Know"),
            ResidentialFieldDefinition("plotArea", "Plot Area", FormFieldType.NUMBER, placeholder = "e.g., 2400", visibleWhen = FieldVisibilityRule("residentialType", villaTypes)),
            ResidentialFieldDefinition("plotAreaUnit", "Plot Area Unit", FormFieldType.SELECT, listOf("sq ft", "sq mtr"), visibleWhen = FieldVisibilityRule("residentialType", villaTypes), defaultValue = "sq ft"),
            ResidentialFieldDefinition("plotLength", "Plot Length (ft)", FormFieldType.NUMBER, placeholder = "e.g., 40", visibleWhen = FieldVisibilityRule("residentialType", villaTypes)),
            ResidentialFieldDefinition("plotWidth", "Plot Width (ft)", FormFieldType.NUMBER, placeholder = "e.g., 60", visibleWhen = FieldVisibilityRule("residentialType", villaTypes))
        )
    ),
    ResidentialFormSection(
        title = "Amenities",
        fields = residentialAmenities.map { amenity ->
            ResidentialFieldDefinition(id = amenity, label = amenity, type = FormFieldType.AMENITY)
        }
    ),
    ResidentialFormSection(
        title = "Residency Details",
        fields = listOf(
            ResidentialFieldDefinition("residentsCount", "Current Number of Residents", FormFieldType.SELECT, listOf("1", "2", "3", "4", "5", "6", "7"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "2")
        )
    )
)

fun defaultResidentialFieldValues(): Map<String, String> {
    val base = mutableMapOf(
        "propertyPrice" to "",
        "propertyAddress" to "",
        "description" to "",
        "state" to "",
        "city" to "",
        "nameStreetArea" to "",
        "landmark" to ""
    )
    residentialSections.flatMap { it.fields }.forEach { field ->
        base[field.id] = field.defaultValue
    }
    return base
}

fun isFieldVisible(
    field: ResidentialFieldDefinition,
    listingType: String,
    values: Map<String, String>
): Boolean {
    if (field.listingTypes.isNotEmpty() && listingType !in field.listingTypes) return false
    val rule = field.visibleWhen ?: return true
    return values[rule.fieldId].orEmpty() in rule.values
}
