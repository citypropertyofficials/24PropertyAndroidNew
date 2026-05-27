package com.example.myapplication.utils

import kotlin.math.pow

private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

data class GeohashRange(
    val start: String,
    val end: String
)

fun encodeGeohash(lat: Double?, lng: Double?, precision: Int = 9): String? {
    if (lat == null || lng == null || !lat.isFinite() || !lng.isFinite()) return null

    var latMin = -90.0
    var latMax = 90.0
    var lngMin = -180.0
    var lngMax = 180.0
    var geohash = ""
    var isEven = true
    var bit = 0
    var ch = 0

    while (geohash.length < precision) {
        if (isEven) {
            val mid = (lngMin + lngMax) / 2
            if (lng > mid) {
                ch = ch or (1 shl (4 - bit))
                lngMin = mid
            } else {
                lngMax = mid
            }
        } else {
            val mid = (latMin + latMax) / 2
            if (lat > mid) {
                ch = ch or (1 shl (4 - bit))
                latMin = mid
            } else {
                latMax = mid
            }
        }
        isEven = !isEven
        if (bit < 4) {
            bit++
        } else {
            geohash += BASE32[ch]
            bit = 0
            ch = 0
        }
    }

    return geohash
}

fun haversineDistanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val earthRadiusKm = 6371.0
    fun toRadians(degrees: Double) = (degrees * Math.PI) / 180.0

    val dLat = toRadians(lat2 - lat1)
    val dLng = toRadians(lng2 - lng1)
    val a = kotlin.math.sin(dLat / 2).pow(2) +
        kotlin.math.cos(toRadians(lat1)) *
        kotlin.math.cos(toRadians(lat2)) *
        kotlin.math.sin(dLng / 2).pow(2)

    return 2 * earthRadiusKm * kotlin.math.asin(kotlin.math.sqrt(a))
}

fun getGeohashQueries(lat: Double?, lng: Double?, radiusKm: Double?): List<GeohashRange> {
    if (lat == null || lng == null || radiusKm == null) return emptyList()

    val precision = when {
        radiusKm <= 0.1 -> 7
        radiusKm <= 0.5 -> 6
        radiusKm <= 5 -> 5
        radiusKm <= 20 -> 4
        else -> 3
    }

    val centerHash = encodeGeohash(lat, lng, precision) ?: return emptyList()
    val latOffset = radiusKm / 111.0
    val lngOffset = radiusKm / (111.0 * kotlin.math.cos(lat * Math.PI / 180.0))

    val north = minOf(90.0, lat + latOffset)
    val south = maxOf(-90.0, lat - latOffset)
    val east = lng + lngOffset
    val west = lng - lngOffset

    val hashes = linkedSetOf(centerHash)
    listOf(
        encodeGeohash(north, lng, precision),
        encodeGeohash(south, lng, precision),
        encodeGeohash(lat, east, precision),
        encodeGeohash(lat, west, precision),
        encodeGeohash(north, east, precision),
        encodeGeohash(north, west, precision),
        encodeGeohash(south, east, precision),
        encodeGeohash(south, west, precision)
    ).forEach { hash ->
        if (!hash.isNullOrBlank()) hashes += hash
    }

    return hashes.map { hash -> GeohashRange(start = hash, end = "$hash\uF8FF") }
}

fun normalizeCoordinate(value: Any?): Double? {
    return when (value) {
        is Number -> value.toDouble().takeIf { it.isFinite() }
        is String -> value.toDoubleOrNull()?.takeIf { it.isFinite() }
        else -> null
    }
}
