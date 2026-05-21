package com.sportsapp.application.facility

data class RegisterMyFacilityCommand(
    val code: String,
    val name: String,
    val gu: String,
    val type: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val parking: Boolean,
    val tel: String,
    val homePage: String,
    val eduYn: Boolean,
    val meta: Map<String, String>,
    val ownerUserId: Long,
)
