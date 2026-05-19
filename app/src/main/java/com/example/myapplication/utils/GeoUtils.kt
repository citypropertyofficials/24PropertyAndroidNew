package com.example.myapplication.utils

private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

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
