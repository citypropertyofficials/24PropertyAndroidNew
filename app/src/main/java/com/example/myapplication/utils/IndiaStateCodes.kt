package com.example.myapplication.utils

private val stateCodeMap = mapOf(
    "Andhra Pradesh" to "AP",
    "Arunachal Pradesh" to "AR",
    "Assam" to "AS",
    "Bihar" to "BR",
    "Chhattisgarh" to "CG",
    "Goa" to "GA",
    "Gujarat" to "GJ",
    "Haryana" to "HR",
    "Himachal Pradesh" to "HP",
    "Jharkhand" to "JH",
    "Karnataka" to "KA",
    "Kerala" to "KL",
    "Madhya Pradesh" to "MP",
    "Maharashtra" to "MH",
    "Manipur" to "MN",
    "Meghalaya" to "ML",
    "Mizoram" to "MZ",
    "Nagaland" to "NL",
    "Odisha" to "OR",
    "Punjab" to "PB",
    "Rajasthan" to "RJ",
    "Sikkim" to "SK",
    "Tamil Nadu" to "TN",
    "Telangana" to "TS",
    "Tripura" to "TR",
    "Uttar Pradesh" to "UP",
    "Uttarakhand" to "UK",
    "West Bengal" to "WB",
    "Andaman and Nicobar Islands" to "AN",
    "Chandigarh" to "CH",
    "Dadra and Nagar Haveli and Daman and Diu" to "DH",
    "Delhi" to "DL",
    "Jammu and Kashmir" to "JK",
    "Ladakh" to "LA",
    "Lakshadweep" to "LD",
    "Puducherry" to "PY"
)

fun resolveStateCode(stateName: String): String {
    return stateCodeMap[stateName.trim()] ?: ""
}
