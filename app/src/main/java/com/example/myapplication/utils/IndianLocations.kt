package com.example.myapplication.utils

import android.content.Context
import com.example.myapplication.R
import org.json.JSONObject

data class IndianState(val name: String, val isoCode: String, val districts: List<String>)

object IndianLocations {
    private var states: List<IndianState>? = null

    fun initialize(context: Context) {
        if (states != null) return
        val jsonString = context.resources.openRawResource(R.raw.indian_districts)
            .bufferedReader().use { it.readText() }
        val json = JSONObject(jsonString)
        val statesArray = json.getJSONArray("states")
        val loadedStates = mutableListOf<IndianState>()
        for (i in 0 until statesArray.length()) {
            val stateObj = statesArray.getJSONObject(i)
            val stateName = stateObj.getString("state")
            val isoCode = resolveStateCode(stateName)
            val districtsArray = stateObj.getJSONArray("districts")
            val districts = mutableListOf<String>()
            for (j in 0 until districtsArray.length()) {
                districts.add(districtsArray.getString(j))
            }
            loadedStates.add(IndianState(stateName, isoCode, districts))
        }
        states = loadedStates
    }

    private fun ensureLoaded() {
        if (states == null) {
            throw IllegalStateException("IndianLocations not initialized. Call initialize(context) first.")
        }
    }

    val allStates: List<IndianState>
        get() {
            ensureLoaded()
            return states!!
        }

    val allStateNames: List<String>
        get() = allStates.map { it.name }

    fun getStateByName(name: String): IndianState? {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return null
        val normalized = trimmed.lowercase()
        // Exact match first
        allStates.find { it.name.equals(trimmed, ignoreCase = true) }?.let { return it }
        // Loose match: contains
        return allStates.find {
            it.name.lowercase().contains(normalized) || normalized.contains(it.name.lowercase())
        }
    }

    fun getStateByCode(code: String): IndianState? {
        return allStates.find { it.isoCode.equals(code.trim(), ignoreCase = true) }
    }

    fun getDistrictsForState(stateName: String): List<String> {
        return getStateByName(stateName)?.districts ?: emptyList()
    }
}
