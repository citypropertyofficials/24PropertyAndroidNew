package com.example.myapplication.ui.screens.addproperty

import android.net.Uri
import com.example.myapplication.BuildConfig

const val RESIDENTIAL_PROPERTY_TYPE = "residential"
const val COMMERCIAL_PROPERTY_TYPE = "commercial"
const val INDUSTRIAL_PROPERTY_TYPE = "industrial"
const val LAND_PROPERTY_TYPE = "land"
const val LISTING_TYPE_RENT = "rent"
const val LISTING_TYPE_SALE = "sale"
const val MAX_PROPERTY_IMAGES = 8

val SHOWING_AVAILABILITY_OPTIONS = listOf("Weekday (Mon-Fri)", "Weekend (Sat-Sun)", "Available all day")
const val ALL_DAY_AVAILABILITY = "Available all day"

enum class FormFieldType {
    TEXT,
    NUMBER,
    SELECT,
    RADIO,
    DATETIME,
    DATE,
    TIME,
    MEASURE,
    AMENITY
}

data class FieldVisibilityRule(
    val fieldId: String,
    val values: Set<String>
)

data class PropertyFieldDefinition(
    val id: String,
    val label: String,
    val type: FormFieldType,
    val options: List<String> = emptyList(),
    val placeholder: String = "",
    val required: Boolean = false,
    val listingTypes: Set<String> = emptySet(),
    val visibleWhen: FieldVisibilityRule? = null,
    val disabledWhen: FieldVisibilityRule? = null,
    val defaultValue: String = "",
    val unitField: String? = null,
    val unitOptions: List<String> = emptyList(),
    val defaultUnit: String = ""
)

data class PropertyFormSection(
    val title: String,
    val fields: List<PropertyFieldDefinition>
)

// Maintain 100% backward compatibility for existing Residential classes
typealias ResidentialFieldDefinition = PropertyFieldDefinition
typealias ResidentialFormSection = PropertyFormSection

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
    val propertyId: String? = null,
    val isEditMode: Boolean = false,
    val isInitialLoading: Boolean = false,
    val originalImageUrls: List<String> = emptyList(),
    val existingImageUrls: List<String> = emptyList(),
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

data class AddCommercialPropertyUiState(
    val listingType: String = LISTING_TYPE_RENT,
    val fieldValues: Map<String, String> = defaultCommercialFieldValues(),
    val amenities: Set<String> = emptySet(),
    val propertyId: String? = null,
    val isEditMode: Boolean = false,
    val isInitialLoading: Boolean = false,
    val originalImageUrls: List<String> = emptyList(),
    val existingImageUrls: List<String> = emptyList(),
    val imageUris: List<Uri> = emptyList(),
    val selectedLocation: SelectedLocation? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val isSavingDraft: Boolean = false,
    val isPublishing: Boolean = false,
    val mapsApiConfigured: Boolean = BuildConfig.MAPS_API_KEY.isNotBlank()
)

sealed class AddCommercialPropertyEvent {
    data class ShowMessage(val message: String) : AddCommercialPropertyEvent()
    data object PropertySaved : AddCommercialPropertyEvent()
}

data class AddIndustrialPropertyUiState(
    val listingType: String = LISTING_TYPE_RENT,
    val fieldValues: Map<String, String> = defaultIndustrialFieldValues(),
    val amenities: Set<String> = emptySet(),
    val propertyId: String? = null,
    val isEditMode: Boolean = false,
    val isInitialLoading: Boolean = false,
    val originalImageUrls: List<String> = emptyList(),
    val existingImageUrls: List<String> = emptyList(),
    val imageUris: List<Uri> = emptyList(),
    val selectedLocation: SelectedLocation? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val isSavingDraft: Boolean = false,
    val isPublishing: Boolean = false,
    val mapsApiConfigured: Boolean = BuildConfig.MAPS_API_KEY.isNotBlank()
)

sealed class AddIndustrialPropertyEvent {
    data class ShowMessage(val message: String) : AddIndustrialPropertyEvent()
    data object PropertySaved : AddIndustrialPropertyEvent()
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
    "Heating", "Laundry", "Storage", "High-Speed Internet", "Furnished", "Parking", "Power Backup",
    "Water Supply", "Waste Disposal", "Landscaping", "CCTV", "Gated Community", "Intercom",
    "Visitor Parking", "Gas Pipeline", "Club", "Playground", "House Keeping", "Fire Safety",
    "Children Play Area", "Servant Room", "Park"
)

val commercialAmenities = listOf(
    "On Main Road", "Corner Property"
)

val residentialSections = listOf(
    PropertyFormSection(
        title = "Type & Category",
        fields = listOf(
            PropertyFieldDefinition("residentialType", "Residential Type", FormFieldType.SELECT, listOf("Flat", "Individual House", "Villa", "Bungalow", "Farmhouse", "Studio", "Apartment", "Duplex", "Penthouse", "PG/Hostel"), defaultValue = "Flat"),
            PropertyFieldDefinition("plotArea", "Plot Area", FormFieldType.MEASURE, placeholder = "e.g., 2400", visibleWhen = FieldVisibilityRule("residentialType", villaTypes), unitField = "plotAreaUnit", unitOptions = listOf("sq ft", "sq mtr"), defaultUnit = "sq ft"),
            PropertyFieldDefinition("ownership", "Ownership", FormFieldType.SELECT, listOf("Self owned", "On lease"), listingTypes = setOf(LISTING_TYPE_SALE), defaultValue = "Self owned")
        )
    ),
    PropertyFormSection(
        title = "Configuration & Area",
        fields = listOf(
            PropertyFieldDefinition("bhkConfig", "BHK Configuration", FormFieldType.SELECT, listOf("1 BHK", "1.5 BHK", "2 BHK", "2.5 BHK", "3 BHK", "3.5 BHK", "4 BHK", "5 BHK+", "4+ BHK"), required = true, defaultValue = "2 BHK"),
            PropertyFieldDefinition("possessionStatus", "Property Status", FormFieldType.SELECT, listOf("Ready Possession", "Under Construction"), required = true),
            PropertyFieldDefinition("builtUpArea", "Built Up Area", FormFieldType.MEASURE, placeholder = "e.g., 1200", required = true, unitField = "builtUpAreaUnit", unitOptions = listOf("sq ft", "sq mtr"), defaultUnit = "sq ft"),
            PropertyFieldDefinition("carpetArea", "Carpet Area", FormFieldType.MEASURE, placeholder = "e.g., 1000", unitField = "carpetAreaUnit", unitOptions = listOf("sq ft", "sq mtr"), defaultUnit = "sq ft"),
            PropertyFieldDefinition("propertyAge", "Age of Property", FormFieldType.SELECT, listOf("Less than 1 year", "1-3 years", "3-5 years", "5-10 years", "More than 10 years"), defaultValue = "Less than 1 year"),
            PropertyFieldDefinition("totalFloors", "Total Floors in Building", FormFieldType.SELECT, listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "10-20", "20+"), defaultValue = "5"),
            PropertyFieldDefinition("floorNumber", "Floor Number", FormFieldType.SELECT, listOf("Ground Floor", "1st Floor", "2nd Floor", "3rd Floor", "4th Floor", "5th Floor", "6th Floor", "7th Floor", "8th Floor", "9th Floor", "10th Floor", "11+ Floor"), defaultValue = "Ground Floor"),
            PropertyFieldDefinition("floorType", "Floor Type", FormFieldType.SELECT, listOf("Cement", "Vitrified Tiles", "Natural Stone", "Mosaic", "Marble", "Granite", "Wooden")),
            PropertyFieldDefinition("bathrooms", "Number of Bathrooms", FormFieldType.SELECT, listOf("1", "2", "3", "4+"), defaultValue = "2"),
            PropertyFieldDefinition("balconies", "Number of Balconies", FormFieldType.SELECT, listOf("0", "1", "2", "3", "4+"), defaultValue = "1"),
            PropertyFieldDefinition("furnishing", "Furnishing Status", FormFieldType.SELECT, listOf("Fully Furnished", "Semi-Furnished", "Unfurnished"), defaultValue = "Semi-Furnished")
        )
    ),
    PropertyFormSection(
        title = "Parking & Accessibility",
        fields = listOf(
            PropertyFieldDefinition("coveredParking", "Covered Parking", FormFieldType.SELECT, listOf("No", "Bike", "Car", "Car & Bike", "1 slot", "2 slots", "3 slots", "4+ slots"), defaultValue = "No"),
            PropertyFieldDefinition("openParking", "Open Parking", FormFieldType.SELECT, listOf("No", "Bike", "Car", "Car & Bike", "1 slot (2-wheeler)", "1 slot (4-wheeler)", "2 slots", "3+ slots"), defaultValue = "No"),
            PropertyFieldDefinition("parkingCharges", "Parking Charges", FormFieldType.SELECT, listOf("Included in rent", "Separate charges"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "Included in rent"),
            PropertyFieldDefinition("liftAvailable", "Lift", FormFieldType.RADIO, listOf("Yes", "No"), defaultValue = "No")
        )
    ),
    PropertyFormSection(
        title = "Tenancy Details",
        fields = listOf(
            PropertyFieldDefinition("tenantType", "Preferred Tenant Type", FormFieldType.SELECT, listOf("Family", "Bachelors", "Company", "Student Boys/Girls", "Any One"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "Family"),
            PropertyFieldDefinition("petFriendly", "Pet Friendly", FormFieldType.RADIO, listOf("Yes", "No"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "Yes"),
            PropertyFieldDefinition("availableFrom", "Available From", FormFieldType.SELECT, listOf("Immediate", "Within 15 days", "Within 30 days", "After 30 days"), defaultValue = "Immediate"),
            PropertyFieldDefinition("maintenanceCharges", "Maintenance Charges", FormFieldType.SELECT, listOf("Included in rent", "Separate charges"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "Included in rent"),
            PropertyFieldDefinition("maintenanceAmount", "Monthly Maintenance Amount (₹)", FormFieldType.NUMBER, placeholder = "e.g., 2500"),
            PropertyFieldDefinition("waterSupplyType", "Water Supply", FormFieldType.SELECT, listOf("Corporation", "Borewell", "Both", "Other")),
            PropertyFieldDefinition("propertyShower", "Who Will Show This Property", FormFieldType.SELECT, listOf("Need help", "I will show", "Neighbours", "Friend", "Relative", "Security", "Tenants", "Other")),
            PropertyFieldDefinition("showingDate", "Availability Date", FormFieldType.DATE, disabledWhen = FieldVisibilityRule("showingAvailability", setOf(ALL_DAY_AVAILABILITY))),
            PropertyFieldDefinition("showingStartTime", "Start Time", FormFieldType.TIME),
            PropertyFieldDefinition("showingEndTime", "End Time", FormFieldType.TIME),
            PropertyFieldDefinition("showingAvailability", "Availability Schedule", FormFieldType.SELECT, SHOWING_AVAILABILITY_OPTIONS),
            PropertyFieldDefinition("currentSituation", "Current Situation of Property", FormFieldType.SELECT, listOf("Vacant", "Tenant", "Self occupied", "Sell urgent", "Not finding tenant"))
        )
    ),
    PropertyFormSection(
        title = "Payments & Contracts",
        fields = listOf(
            PropertyFieldDefinition("priceNegotiable", "Price Negotiable", FormFieldType.RADIO, listOf("Yes", "No"), defaultValue = "No"),
            PropertyFieldDefinition("securityDeposit", "Security Deposit", FormFieldType.SELECT, listOf("None", "1 month", "2 months", "6 months", "Custom amount"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "1 month"),
            PropertyFieldDefinition("lockInPeriod", "Lock-in Period", FormFieldType.SELECT, listOf("None", "1 month", "6 months", "Custom period"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "None"),
            PropertyFieldDefinition("brokerageRequired", "Brokerage Required", FormFieldType.SELECT, listOf("No", "15 days rent", "30 days rent", "Custom amount"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "No"),
            PropertyFieldDefinition("rentIncrease", "Expected Rent Increase (₹)", FormFieldType.NUMBER, placeholder = "e.g., 2000", listingTypes = setOf(LISTING_TYPE_RENT)),
            PropertyFieldDefinition("nonVegAllowed", "Non-veg Allowed", FormFieldType.RADIO, listOf("Yes", "No"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "Yes"),
            PropertyFieldDefinition("currentUnderLoan", "Current Under Loan", FormFieldType.RADIO, listOf("Yes", "No"), listingTypes = setOf(LISTING_TYPE_SALE), defaultValue = "No"),
            PropertyFieldDefinition("commencementCertificate", "Commencement Certificate", FormFieldType.SELECT, listOf("Yes", "No", "Don't know"), listingTypes = setOf(LISTING_TYPE_SALE)),
            PropertyFieldDefinition("occupancyCertificate", "Occupancy / Completion Certificate", FormFieldType.SELECT, listOf("Yes", "No", "Don't know"), listingTypes = setOf(LISTING_TYPE_SALE)),
            PropertyFieldDefinition("possessionLetter", "Possession Letter", FormFieldType.SELECT, listOf("Yes", "No", "Don't know"), listingTypes = setOf(LISTING_TYPE_SALE)),
            PropertyFieldDefinition("saleDeed", "Sale Deed", FormFieldType.SELECT, listOf("Yes", "No", "Don't know"), listingTypes = setOf(LISTING_TYPE_SALE)),
            PropertyFieldDefinition("propertyTaxPaid", "Property Tax Paid", FormFieldType.SELECT, listOf("Yes", "No", "Don't know"), listingTypes = setOf(LISTING_TYPE_SALE)),
            PropertyFieldDefinition("societyFormation", "Formation", FormFieldType.SELECT, listOf("Society", "Apartment"), listingTypes = setOf(LISTING_TYPE_SALE))
        )
    ),
    PropertyFormSection(
        title = "Room & Facilities",
        fields = listOf(
            PropertyFieldDefinition("servantRoom", "Servant Room Available", FormFieldType.RADIO, listOf("Yes", "No"), defaultValue = "No"),
            PropertyFieldDefinition("facing", "Property Facing", FormFieldType.SELECT, listOf("Don't Know", "North", "East", "West", "South", "North-East", "North-West", "South-East", "South-West"), defaultValue = "Don't Know"),
            PropertyFieldDefinition("plotLength", "Plot Length (ft)", FormFieldType.NUMBER, placeholder = "e.g., 40", visibleWhen = FieldVisibilityRule("residentialType", villaTypes)),
            PropertyFieldDefinition("plotWidth", "Plot Width (ft)", FormFieldType.NUMBER, placeholder = "e.g., 60", visibleWhen = FieldVisibilityRule("residentialType", villaTypes))
        )
    ),
    PropertyFormSection(
        title = "Amenities",
        fields = residentialAmenities.map { amenity ->
            PropertyFieldDefinition(id = amenity, label = amenity, type = FormFieldType.AMENITY)
        }
    ),
    PropertyFormSection(
        title = "Residency Details",
        fields = listOf(
            PropertyFieldDefinition("residentsCount", "Current Number of Residents", FormFieldType.SELECT, listOf("1", "2", "3", "4", "5", "6", "7"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "2")
        )
    )
)

val commercialSections = listOf(
    PropertyFormSection(
        title = "Type & Category",
        fields = listOf(
            PropertyFieldDefinition("commercialType", "Commercial Type", FormFieldType.SELECT, listOf("Office Space", "Retail Shop", "Shop", "Showroom", "Restaurant", "Cafe", "Co-working", "Other"), defaultValue = "Office Space"),
            PropertyFieldDefinition("buildingType", "Building Type", FormFieldType.SELECT, listOf("Independent House", "Standalone Building", "Business Park", "Independent Shop", "Mall"))
        )
    ),
    PropertyFormSection(
        title = "Location & Building",
        fields = listOf(
            PropertyFieldDefinition("buildingName", "Building/Project/Society/MIDC Name", FormFieldType.TEXT, placeholder = "e.g., Phoenix Mall"),
            PropertyFieldDefinition("locality", "Locality", FormFieldType.TEXT, placeholder = "e.g., Andheri East"),
            PropertyFieldDefinition("zoneType", "Zone Type", FormFieldType.SELECT, listOf("Commercial", "Industrial", "Residential", "Special Economic", "Open Space", "Agricultural Zone", "Other"), defaultValue = "Commercial"),
            PropertyFieldDefinition("locationHub", "Location Hub", FormFieldType.SELECT, listOf("IT Park", "Business Park", "Other"), defaultValue = "Other")
        )
    ),
    PropertyFormSection(
        title = "Status & Availability",
        fields = listOf(
            PropertyFieldDefinition("possessionStatus", "Possession Status", FormFieldType.SELECT, listOf("Ready to Move", "Under Construction"), listingTypes = setOf(LISTING_TYPE_SALE), defaultValue = "Ready to Move"),
            PropertyFieldDefinition("availableFrom", "Available From", FormFieldType.SELECT, listOf("Immediate", "Within 15 Days", "Within 30 Days", "After 30 Days"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "Immediate"),
            PropertyFieldDefinition("currentSituation", "Current Situation of Property", FormFieldType.SELECT, listOf("Vacant", "Rental", "Self occupied", "Sell urgent")),
            PropertyFieldDefinition("waterSupplyType", "Water Supply", FormFieldType.SELECT, listOf("Corporation", "Borewell", "Both", "Other")),
            PropertyFieldDefinition("currentBusiness", "Current Business Running", FormFieldType.SELECT, listOf("Office", "Restaurant", "Cafe", "Salon", "Spa", "Store", "Showroom", "ATM", "Other")),
            PropertyFieldDefinition("showingDate", "Property Showing Date", FormFieldType.DATE, disabledWhen = FieldVisibilityRule("showingAvailability", setOf(ALL_DAY_AVAILABILITY))),
            PropertyFieldDefinition("showingStartTime", "Start Time", FormFieldType.TIME),
            PropertyFieldDefinition("showingEndTime", "End Time", FormFieldType.TIME),
            PropertyFieldDefinition("showingAvailability", "Availability Schedule", FormFieldType.SELECT, SHOWING_AVAILABILITY_OPTIONS)
        )
    ),
    PropertyFormSection(
        title = "Property & Legal",
        fields = listOf(
            PropertyFieldDefinition("propertyCondition", "Property Condition", FormFieldType.SELECT, listOf("Ready to Use", "Bare Shell"), defaultValue = "Ready to Use"),
            PropertyFieldDefinition("ownership", "Ownership", FormFieldType.SELECT, listOf("Freehold", "Leasehold", "Cooperative Society", "Power of Attorney"), defaultValue = "Leasehold"),
            PropertyFieldDefinition("plotArea", "Plot Area", FormFieldType.MEASURE, placeholder = "e.g., 5000", unitField = "plotAreaUnit", unitOptions = listOf("sq ft", "sq mtr"), defaultUnit = "sq ft"),
            PropertyFieldDefinition("builtUpArea", "Built-up Area", FormFieldType.MEASURE, placeholder = "e.g., 3000", required = true, unitField = "builtUpAreaUnit", unitOptions = listOf("sq ft", "sq mtr"), defaultUnit = "sq ft"),
            PropertyFieldDefinition("carpetArea", "Carpet Area", FormFieldType.MEASURE, placeholder = "e.g., 2500", unitField = "carpetAreaUnit", unitOptions = listOf("sq ft", "sq mtr"), defaultUnit = "sq ft"),
            PropertyFieldDefinition("propertyAge", "Age of Property", FormFieldType.SELECT, listOf("Less than 1 year", "1-3 years", "3-5 years", "5-10 years", "More than 10 years"), defaultValue = "Less than 1 year"),
            PropertyFieldDefinition("totalConstructionArea", "Total Construction Area (sq ft)", FormFieldType.NUMBER, placeholder = "e.g., 3500"),
            PropertyFieldDefinition("frontage", "Frontage (ft)", FormFieldType.NUMBER, placeholder = "e.g., 50"),
            PropertyFieldDefinition("roadAccess", "Road Access (ft)", FormFieldType.NUMBER, placeholder = "e.g., 40")
        )
    ),
    PropertyFormSection(
        title = "Pricing & Financials",
        fields = listOf(
            PropertyFieldDefinition("priceNegotiable", "Price Negotiable", FormFieldType.RADIO, listOf("Yes", "No"), listingTypes = setOf(LISTING_TYPE_SALE), defaultValue = "No"),
            PropertyFieldDefinition("rentNegotiable", "Is Rent Negotiable", FormFieldType.RADIO, listOf("Yes", "No"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "No"),
            PropertyFieldDefinition("securityDeposit", "Security Deposit", FormFieldType.SELECT, listOf("None", "1 month", "6 months", "Custom amount"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "1 month"),
            PropertyFieldDefinition("rentIncrease", "Expected Rent Increase (₹)", FormFieldType.NUMBER, placeholder = "e.g., 5000", listingTypes = setOf(LISTING_TYPE_RENT)),
            PropertyFieldDefinition("lockInPeriod", "Lock-in Period", FormFieldType.SELECT, listOf("None", "6 months", "1 year", "2 years", "Custom"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "1 year"),
            PropertyFieldDefinition("maintenanceAmount", "Monthly Maintenance Amount (₹)", FormFieldType.NUMBER, placeholder = "e.g., 3000"),
            PropertyFieldDefinition("maintenanceCharges", "Maintenance Charges", FormFieldType.SELECT, listOf("Included in rent", "Separate charges"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "Included in rent")
        )
    ),
    PropertyFormSection(
        title = "Charges & Inclusions",
        fields = listOf(
            PropertyFieldDefinition("dampUpsIncluded", "DG & UPS Charge Included", FormFieldType.RADIO, listOf("Yes", "No"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "No"),
            PropertyFieldDefinition("electricityIncluded", "Electricity Charge Included", FormFieldType.RADIO, listOf("Yes", "No"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "No"),
            PropertyFieldDefinition("waterChargesIncluded", "Water Charges Included", FormFieldType.RADIO, listOf("Yes", "No"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "No")
        )
    ),
    PropertyFormSection(
        title = "Floors & Elevation",
        fields = listOf(
            PropertyFieldDefinition("yourFloor", "Floor Info", FormFieldType.SELECT, listOf("Lower Basement", "Upper Basement", "Full Building", "Ground Floor", "1st Floor", "2nd Floor", "3rd Floor", "4th Floor", "5th Floor", "6th Floor", "7th Floor", "8th Floor", "9th Floor", "10th Floor", "11th Floor", "12th Floor", "13th Floor", "14th Floor", "15th Floor", "16th Floor", "17th Floor", "18th Floor", "19th Floor", "20th Floor", "20+ Floor"), defaultValue = "Ground Floor"),
            PropertyFieldDefinition("totalFloors", "Total Floors", FormFieldType.SELECT, listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "10-20", "20+"), defaultValue = "5"),
            PropertyFieldDefinition("staircases", "Number of Staircases", FormFieldType.SELECT, listOf("1", "2", "3", "4+"), defaultValue = "1"),
            PropertyFieldDefinition("liftType", "Lift", FormFieldType.SELECT, listOf("None", "Passenger Lift", "Service Lift"), listingTypes = setOf(LISTING_TYPE_SALE), defaultValue = "None"),
            PropertyFieldDefinition("liftType", "Lift", FormFieldType.SELECT, listOf("None", "Personal", "Common", "Passenger Lift", "Service Lift"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "None")
        )
    ),
    PropertyFormSection(
        title = "Parking & Washrooms",
        fields = listOf(
            PropertyFieldDefinition("parkingType", "Parking", FormFieldType.SELECT, listOf("None", "Reserved", "Public", "Public Reserved", "Private"), defaultValue = "None"),
            PropertyFieldDefinition("washroomType", "Washroom", FormFieldType.SELECT, listOf("No washroom", "Shared", "Private", "Public"), defaultValue = "Private")
        )
    ),
    PropertyFormSection(
        title = "Facing & Facilities",
        fields = listOf(
            PropertyFieldDefinition("facing", "Facing", FormFieldType.SELECT, listOf("Don't Know", "North", "East", "West", "South", "North-East", "North-West", "South-East", "South-West"), defaultValue = "Don't Know"),
            PropertyFieldDefinition("roadFacing", "Road", FormFieldType.TEXT, placeholder = "e.g., Main Road")
        )
    ),
    PropertyFormSection(
        title = "Other Features",
        fields = commercialAmenities.map { amenity ->
            PropertyFieldDefinition(id = amenity, label = amenity, type = FormFieldType.AMENITY)
        }
    ),
    PropertyFormSection(
        title = "Legal Documents",
        fields = listOf(
            PropertyFieldDefinition("commencementCertificate", "Commencement Certificate", FormFieldType.SELECT, listOf("Yes", "No", "Don't know"), listingTypes = setOf(LISTING_TYPE_SALE)),
            PropertyFieldDefinition("occupancyCertificate", "Occupancy / Completion Certificate", FormFieldType.SELECT, listOf("Yes", "No", "Don't know"), listingTypes = setOf(LISTING_TYPE_SALE)),
            PropertyFieldDefinition("possessionLetter", "Possession Letter", FormFieldType.SELECT, listOf("Yes", "No", "Don't know"), listingTypes = setOf(LISTING_TYPE_SALE)),
            PropertyFieldDefinition("saleDeed", "Sale Deed", FormFieldType.SELECT, listOf("Yes", "No", "Don't know"), listingTypes = setOf(LISTING_TYPE_SALE)),
            PropertyFieldDefinition("propertyTaxPaid", "Property Tax Paid", FormFieldType.SELECT, listOf("Yes", "No", "Don't know"), listingTypes = setOf(LISTING_TYPE_SALE)),
            PropertyFieldDefinition("societyFormation", "Formation", FormFieldType.SELECT, listOf("Society", "Apartment"), listingTypes = setOf(LISTING_TYPE_SALE))
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
        if (field.type == FormFieldType.MEASURE && field.unitField != null) {
            base[field.unitField] = field.defaultUnit
        }
    }
    return base
}

fun defaultCommercialFieldValues(): Map<String, String> {
    val base = mutableMapOf(
        "propertyPrice" to "",
        "buildingName" to "",
        "description" to "",
        "state" to "",
        "city" to "",
        "nameStreetArea" to "",
        "landmark" to ""
    )
    commercialSections.flatMap { it.fields }.forEach { field ->
        base[field.id] = field.defaultValue
        if (field.type == FormFieldType.MEASURE && field.unitField != null) {
            base[field.unitField] = field.defaultUnit
        }
    }
    return base
}

val industrialSections = listOf(
    PropertyFormSection(
        title = "Type & Category",
        fields = listOf(
            PropertyFieldDefinition("industrialType", "Industrial Type", FormFieldType.SELECT, listOf("Warehouse", "Plot", "Industrial Plot", "Industrial Shed", "Shed with Plant and machinery", "Industrial Plot with Factory Building", "Other"), defaultValue = "Industrial Plot")
        )
    ),
    PropertyFormSection(
        title = "Location & Building",
        fields = listOf(
            PropertyFieldDefinition("buildingName", "Property Name", FormFieldType.TEXT, placeholder = "e.g., MIDC Industrial Area"),
            PropertyFieldDefinition("locality", "Locality", FormFieldType.TEXT, placeholder = "e.g., Waluj MIDC"),
            PropertyFieldDefinition("zoneType", "Zone Type", FormFieldType.SELECT, listOf("Industrial", "R-zone", "Hill top", "Red zone", "Eco sensitive zone", "Agri zone", "MIDC", "Commercial", "Residential", "Special Economic", "Open Space", "Agricultural Zone", "Other"), defaultValue = "Industrial"),
            PropertyFieldDefinition("locationHub", "Location Hub", FormFieldType.SELECT, listOf("IT Park", "Business Park", "Other"), defaultValue = "Other")
        )
    ),
    PropertyFormSection(
        title = "Status & Availability",
        fields = listOf(
            PropertyFieldDefinition("possessionStatus", "Possession Status", FormFieldType.SELECT, listOf("Ready to Move", "Under Construction"), listingTypes = setOf(LISTING_TYPE_SALE), defaultValue = "Ready to Move"),
            PropertyFieldDefinition("availableFrom", "Available From", FormFieldType.SELECT, listOf("Immediate", "Within 15 Days", "Within 30 Days", "After 30 Days"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "Immediate"),
            PropertyFieldDefinition("showingDate", "Property Showing Date", FormFieldType.DATE, disabledWhen = FieldVisibilityRule("showingAvailability", setOf(ALL_DAY_AVAILABILITY))),
            PropertyFieldDefinition("showingStartTime", "Start Time", FormFieldType.TIME),
            PropertyFieldDefinition("showingEndTime", "End Time", FormFieldType.TIME),
            PropertyFieldDefinition("showingAvailability", "Availability Schedule", FormFieldType.SELECT, SHOWING_AVAILABILITY_OPTIONS)
        )
    ),
    PropertyFormSection(
        title = "Property & Legal",
        fields = listOf(
            PropertyFieldDefinition("propertyCondition", "Property Condition", FormFieldType.SELECT, listOf("Ready to Use", "Bare Shell"), defaultValue = "Ready to Use"),
            PropertyFieldDefinition("ownership", "Ownership", FormFieldType.SELECT, listOf("Freehold", "Leasehold", "Cooperative Society", "Power of Attorney"), defaultValue = "Leasehold"),
            PropertyFieldDefinition("plotArea", "Plot Area", FormFieldType.MEASURE, placeholder = "e.g., 10000", required = true, unitField = "plotAreaUnit", unitOptions = listOf("sq ft", "sq mtr", "Acre", "Hector"), defaultUnit = "sq ft"),
            PropertyFieldDefinition("plotWidth", "Plot Width (ft)", FormFieldType.NUMBER, placeholder = "e.g., 100"),
            PropertyFieldDefinition("plotLength", "Plot Length (ft)", FormFieldType.NUMBER, placeholder = "e.g., 200"),
            PropertyFieldDefinition("builtUpArea", "Built-up Area", FormFieldType.MEASURE, placeholder = "e.g., 8000", unitField = "builtUpAreaUnit", unitOptions = listOf("sq ft", "sq mtr"), defaultUnit = "sq ft"),
            PropertyFieldDefinition("carpetArea", "Carpet Area", FormFieldType.MEASURE, placeholder = "e.g., 7000", unitField = "carpetAreaUnit", unitOptions = listOf("sq ft", "sq mtr"), defaultUnit = "sq ft"),
            PropertyFieldDefinition("totalConstructionArea", "Total Construction Area (sq ft)", FormFieldType.NUMBER, placeholder = "e.g., 9000", listingTypes = setOf(LISTING_TYPE_RENT)),
            PropertyFieldDefinition("frontage", "Frontage (ft)", FormFieldType.NUMBER, placeholder = "e.g., 100"),
            PropertyFieldDefinition("roadAccess", "Road Access (ft)", FormFieldType.NUMBER, placeholder = "e.g., 80", listingTypes = setOf(LISTING_TYPE_RENT))
        )
    ),
    PropertyFormSection(
        title = "Industrial/Shed Specific",
        fields = listOf(
            PropertyFieldDefinition("shedHeight", "Shed Height (ft)", FormFieldType.NUMBER, placeholder = "e.g., 20"),
            PropertyFieldDefinition("shedSideWallHeight", "Shed Side Wall Height (ft)", FormFieldType.NUMBER, placeholder = "e.g., 15"),
            PropertyFieldDefinition("plotDimensions", "Width, Length", FormFieldType.TEXT, placeholder = "e.g., 100 ft x 200 ft"),
            PropertyFieldDefinition("shedBuiltUpArea", "Built-up Area (Shed)", FormFieldType.MEASURE, placeholder = "e.g., 5000", unitField = "shedBuiltUpAreaUnit", unitOptions = listOf("sq ft", "sq mtr"), defaultUnit = "sq ft"),
            PropertyFieldDefinition("builtUpConstructionArea", "Built-up Construction Area", FormFieldType.MEASURE, placeholder = "e.g., 6000", unitField = "builtUpConstructionAreaUnit", unitOptions = listOf("sq ft", "sq mtr"), defaultUnit = "sq ft"),
            PropertyFieldDefinition("electricityLoad", "Electricity Load", FormFieldType.SELECT, listOf("Up to 50 KW", "50-100 KW", "100-200 KW", "200-300 KW", "500+ KW"), defaultValue = "Up to 50 KW"),
            PropertyFieldDefinition("waterSupplyType", "Water Supply", FormFieldType.SELECT, listOf("Corporation", "Borewell", "MIDC", "Other")),
            PropertyFieldDefinition("plotLayout", "Plot Layout", FormFieldType.RADIO, listOf("Yes", "No"), defaultValue = "No"),
            PropertyFieldDefinition("preLeased", "Is it Pre-leased", FormFieldType.RADIO, listOf("Yes", "No"), defaultValue = "No"),
            PropertyFieldDefinition("preRented", "Is it Pre-rented", FormFieldType.RADIO, listOf("Yes", "No"), defaultValue = "No")
        )
    ),
    PropertyFormSection(
        title = "Lease & Financials",
        fields = listOf(
            PropertyFieldDefinition("priceNegotiable", "Price Negotiable", FormFieldType.RADIO, listOf("Yes", "No"), listingTypes = setOf(LISTING_TYPE_SALE), defaultValue = "No"),
            PropertyFieldDefinition("rentNegotiable", "Is Rent Negotiable", FormFieldType.RADIO, listOf("Yes", "No"), listingTypes = setOf(LISTING_TYPE_RENT), defaultValue = "No"),
            PropertyFieldDefinition("securityDeposit", "Security Deposit", FormFieldType.SELECT, listOf("1 month", "2 months", "3 months", "Custom amount"), defaultValue = "2 months", listingTypes = setOf(LISTING_TYPE_RENT)),
            PropertyFieldDefinition("rentIncrease", "Expected Rent Increase", FormFieldType.SELECT, listOf("5% annually", "10% annually", "15% annually", "Custom", "No increase"), defaultValue = "10% annually", listingTypes = setOf(LISTING_TYPE_RENT)),
            PropertyFieldDefinition("lockInPeriod", "Lock-in Period", FormFieldType.SELECT, listOf("None", "6 months", "1 year", "2 years", "Custom"), defaultValue = "1 year", listingTypes = setOf(LISTING_TYPE_RENT))
        )
    ),
    PropertyFormSection(
        title = "Charges & Inclusions",
        fields = listOf(
            PropertyFieldDefinition("dampUpsIncluded", "Damp UPS Charge Included", FormFieldType.RADIO, listOf("Yes", "No"), defaultValue = "No"),
            PropertyFieldDefinition("electricityIncluded", "Electricity Charge Included", FormFieldType.RADIO, listOf("Yes", "No"), defaultValue = "No"),
            PropertyFieldDefinition("waterChargesIncluded", "Water Charges Included", FormFieldType.RADIO, listOf("Yes", "No"), defaultValue = "No")
        )
    ),
    PropertyFormSection(
        title = "Floors & Elevation",
        fields = listOf(
            PropertyFieldDefinition("yourFloor", "Your Floor", FormFieldType.TEXT, placeholder = "e.g., Ground Floor"),
            PropertyFieldDefinition("totalFloors", "Total Floors", FormFieldType.SELECT, listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "10-20", "20+"), defaultValue = "1"),
            PropertyFieldDefinition("staircases", "Number of Staircases", FormFieldType.SELECT, listOf("1", "2", "3", "4+"), defaultValue = "1"),
            PropertyFieldDefinition("passengerLift", "Passenger Lift", FormFieldType.RADIO, listOf("Yes", "No"), defaultValue = "No"),
            PropertyFieldDefinition("serviceLift", "Service Lift", FormFieldType.RADIO, listOf("Yes", "No"), defaultValue = "No")
        )
    ),
    PropertyFormSection(
        title = "Parking & Washrooms",
        fields = listOf(
            PropertyFieldDefinition("parkingType", "Parking", FormFieldType.SELECT, listOf("Private", "Public"), defaultValue = "Private"),
            PropertyFieldDefinition("washroomType", "Washroom", FormFieldType.SELECT, listOf("Private", "Public"), defaultValue = "Private")
        )
    ),
    PropertyFormSection(
        title = "Facing & Facilities",
        fields = listOf(
            PropertyFieldDefinition("rearFacing", "Rear", FormFieldType.TEXT, placeholder = "e.g., Open Space", listingTypes = setOf(LISTING_TYPE_RENT)),
            PropertyFieldDefinition("facing", "Facing", FormFieldType.SELECT, listOf("Don't Know", "North", "East", "West", "South", "North-East", "North-West", "South-East", "South-West"), defaultValue = "Don't Know"),
            PropertyFieldDefinition("roadFacing", "Road", FormFieldType.TEXT, placeholder = "e.g., Service Road", listingTypes = setOf(LISTING_TYPE_RENT))
        )
    ),
    PropertyFormSection(
        title = "Legal Documents",
        fields = listOf(
            PropertyFieldDefinition("commencementCertificate", "Building / Shed Commencement Certificate", FormFieldType.SELECT, listOf("Yes", "No", "Don't know"), listingTypes = setOf(LISTING_TYPE_SALE)),
            PropertyFieldDefinition("occupancyCertificate", "Building / Shed Occupancy / Completion Certificate", FormFieldType.SELECT, listOf("Yes", "No", "Don't know"), listingTypes = setOf(LISTING_TYPE_SALE))
        )
    )
)

fun defaultIndustrialFieldValues(): Map<String, String> {
    val base = mutableMapOf(
        "propertyPrice" to "",
        "buildingName" to "",
        "description" to "",
        "state" to "",
        "city" to "",
        "nameStreetArea" to "",
        "landmark" to ""
    )
    industrialSections.flatMap { it.fields }.forEach { field ->
        base[field.id] = field.defaultValue
        if (field.type == FormFieldType.MEASURE && field.unitField != null) {
            base[field.unitField] = field.defaultUnit
        }
    }
    return base
}

fun isFieldVisible(
    field: PropertyFieldDefinition,
    listingType: String,
    values: Map<String, String>
): Boolean {
    if (field.listingTypes.isNotEmpty() && listingType !in field.listingTypes) return false
    val rule = field.visibleWhen ?: return true
    return values[rule.fieldId].orEmpty() in rule.values
}

fun isFieldDisabled(
    field: PropertyFieldDefinition,
    values: Map<String, String>
): Boolean {
    val rule = field.disabledWhen ?: return false
    return values[rule.fieldId].orEmpty() in rule.values
}

// Land Property Add/Edit Configuration
data class AddLandPropertyUiState(
    val listingType: String = LISTING_TYPE_RENT,
    val fieldValues: Map<String, String> = defaultLandFieldValues(),
    val amenities: Set<String> = emptySet(),
    val propertyId: String? = null,
    val isEditMode: Boolean = false,
    val isInitialLoading: Boolean = false,
    val originalImageUrls: List<String> = emptyList(),
    val existingImageUrls: List<String> = emptyList(),
    val imageUris: List<Uri> = emptyList(),
    val selectedLocation: SelectedLocation? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val isSavingDraft: Boolean = false,
    val isPublishing: Boolean = false,
    val mapsApiConfigured: Boolean = BuildConfig.MAPS_API_KEY.isNotBlank()
)

sealed class AddLandPropertyEvent {
    data class ShowMessage(val message: String) : AddLandPropertyEvent()
    data object PropertySaved : AddLandPropertyEvent()
}

val landAmenities = listOf(
    "Water Supply",
    "Electricity Connection",
    "Drainage",
    "Road Access",
    "Boundary Wall",
    "Corner Plot",
    "Gated Security",
    "Park Facing",
    "Main Road Facing",
    "Metro Nearby",
    "School Nearby",
    "Hospital Nearby"
)

val landSections = listOf(
    PropertyFormSection(
        title = "Land Features",
        fields = listOf(
            PropertyFieldDefinition(
                "landType",
                "Land Type",
                FormFieldType.SELECT,
                listOf("Residential Plot", "Commercial Plot", "Industrial Plot", "Agricultural Land", "Farm Land", "NA Plot", "NA Land"),
                defaultValue = "Residential Plot"
            ),
            PropertyFieldDefinition(
                "zoneType",
                "Zone Type",
                FormFieldType.SELECT,
                listOf(
                    "Agriculture Zone", "Green zone", "Green Zone-2", "Residential zone", "Commercial zone",
                    "Industrial zone", "PSP - Public/Semi Public", "Public utility zone",
                    "Traffic & transportation zone", "Recreational/Open space/Park", "Hill", "Hill-slope",
                    "No development Zone", "Logistic/Warehouse Zone"
                ),
                defaultValue = "Agriculture Zone"
            ),
            PropertyFieldDefinition(
                "naType",
                "NA Type",
                FormFieldType.SELECT,
                listOf("No", "Residential NA", "Commercial NA", "Industrial NA", "Warehouse NA", "IT NA", "Resort NA"),
                defaultValue = "No",
                visibleWhen = FieldVisibilityRule("landType", setOf("NA Plot", "NA Land"))
            ),
            PropertyFieldDefinition(
                "propertyArea",
                "Area (sq ft)",
                FormFieldType.NUMBER,
                placeholder = "e.g., 5000",
                required = true
            ),
            PropertyFieldDefinition(
                "propertyAreaUnit",
                "Area Unit",
                FormFieldType.SELECT,
                listOf("sq ft", "sq mtr", "Acre", "Hector"),
                defaultValue = "sq ft"
            ),
            PropertyFieldDefinition(
                "areaAcres",
                "Area (Acres)",
                FormFieldType.NUMBER,
                placeholder = "e.g., 0.11"
            ),
            PropertyFieldDefinition(
                "numberOfPlots",
                "Number of Plots",
                FormFieldType.NUMBER,
                placeholder = "e.g., 4"
            ),
            PropertyFieldDefinition(
                "plotLength",
                "Plot Length (ft)",
                FormFieldType.NUMBER,
                placeholder = "e.g., 100"
            ),
            PropertyFieldDefinition(
                "plotBreadth",
                "Plot Width (ft)",
                FormFieldType.NUMBER,
                placeholder = "e.g., 50"
            ),
            PropertyFieldDefinition(
                "landFacing",
                "Facing",
                FormFieldType.SELECT,
                listOf("North", "South", "East", "West", "North-East", "North-West", "South-East", "South-West"),
                defaultValue = "North"
            ),
            PropertyFieldDefinition(
                "roadWidth",
                "Road Width",
                FormFieldType.SELECT,
                listOf("Less than 20 ft", "20-30 ft", "30-40 ft", "40-60 ft", "60+ ft"),
                defaultValue = "20-30 ft"
            ),
            PropertyFieldDefinition(
                "frontage",
                "Frontage (ft)",
                FormFieldType.NUMBER,
                placeholder = "e.g., 80"
            ),
            PropertyFieldDefinition(
                "roadAccess",
                "Road Access (ft)",
                FormFieldType.NUMBER,
                placeholder = "e.g., 30"
            ),
            PropertyFieldDefinition(
                "priceNegotiable",
                "Price Negotiable",
                FormFieldType.RADIO,
                listOf("Yes", "No"),
                listingTypes = setOf(LISTING_TYPE_SALE),
                defaultValue = "No"
            ),
            PropertyFieldDefinition(
                "rentNegotiable",
                "Is Rent Negotiable",
                FormFieldType.RADIO,
                listOf("Yes", "No"),
                listingTypes = setOf(LISTING_TYPE_RENT),
                defaultValue = "No"
            ),
            PropertyFieldDefinition(
                "sewageType",
                "Sewage",
                FormFieldType.SELECT,
                listOf("None", "Open", "Underground"),
                defaultValue = "None"
            ),
            PropertyFieldDefinition(
                "landStatus",
                "Status",
                FormFieldType.SELECT,
                listOf("Clear Title", "Litigation", "Under Development", "Ready for Construction"),
                defaultValue = "Clear Title"
            )
        )
    ),
    PropertyFormSection(
        title = "Land Amenities",
        fields = landAmenities.map { amenity ->
            PropertyFieldDefinition(id = amenity, label = amenity, type = FormFieldType.AMENITY)
        }
    )
)

fun defaultLandFieldValues(): Map<String, String> {
    val base = mutableMapOf(
        "propertyName" to "",
        "propertyPrice" to "",
        "propertyAddress" to "",
        "description" to "",
        "state" to "",
        "city" to "",
        "nameStreetArea" to "",
        "landmark" to ""
    )
    landSections.flatMap { it.fields }.forEach { field ->
        base[field.id] = field.defaultValue
    }
    return base
}
