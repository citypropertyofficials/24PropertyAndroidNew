package com.example.myapplication.utils

object FirebaseConstants {
    const val COLLECTION_USERS = "users"
    const val COLLECTION_PROPERTIES = "properties"
    const val COLLECTION_AUCTIONS = "auctions"
    const val COLLECTION_ROLE_REQUESTS = "roleRequests"

    const val FIELD_NAME = "name"
    const val FIELD_EMAIL = "email"
    const val FIELD_PHOTO_URL = "photoURL"
    const val FIELD_MOBILE = "mobile"
    const val FIELD_ROLE = "role"
    const val FIELD_MAX_PROPERTIES_ALLOWED = "maxPropertiesAllowed"
    const val FIELD_CREATED_AT = "createdAt"
    const val FIELD_UPDATED_AT = "updatedAt"
    const val FIELD_STATUS = "status"
    const val FIELD_OWNER = "owner"
    const val FIELD_OWNER_ID = "ownerId"
    const val FIELD_OWNER_ROLE = "ownerRole"
    const val FIELD_USER_ID = "userId"
    const val FIELD_REQUESTED_ROLE = "requestedRole"
    const val FIELD_PREFERRED_LOCATIONS = "preferredLocations"

    // Property fields
    const val FIELD_IMAGES = "images"
    const val FIELD_PROPERTY_TYPE = "propertyType"
    const val FIELD_LISTING_TYPE = "listingType"
    const val FIELD_PRICE = "price"
    const val FIELD_RENT = "rent"
    const val FIELD_LOCATION = "location"
    const val FIELD_CITY_STATE = "cityState"
    const val FIELD_IS_ACTIVE = "isActive"
    const val FIELD_UNIQUE_ID = "uniqueId"

    const val STORAGE_PROFILE_IMAGES = "profile-images"
    const val STORAGE_PROPERTY_IMAGES = "property-images"
    const val STORAGE_AUCTION_IMAGES = "auction-images"

    const val ROLE_USER = "user"
    const val ROLE_BROKER = "broker"
    const val ROLE_ADMIN = "admin"
    const val ROLE_SUPERADMIN = "superadmin"
    const val ROLE_DEVELOPER = "developer"

    const val STATUS_PENDING = "pending"
    const val STATUS_APPROVED = "approved"
    const val STATUS_REJECTED = "rejected"

    const val PROPERTY_LIMIT_USER = 3
}

object NavigationRoutes {
    const val LOGIN = "login"
    const val MAIN = "main"
    const val HOME = "home"
    const val PROFILE = "profile"
}
