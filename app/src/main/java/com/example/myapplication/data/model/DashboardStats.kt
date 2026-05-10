package com.example.myapplication.data.model

data class DashboardStats(
    val totalUsers: Int = 0,
    val activeUsers: Int = 0,
    val blockedUsers: Int = 0,
    val roleStats: Map<String, Int> = emptyMap(),
    val totalRequests: Int = 0
)
