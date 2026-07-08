package com.sportsapp.domain.facility.vo

data class FacilityAttributes(
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
    val region: FacilityRegion = FacilityRegion.UNSPECIFIED,
    val sidoHint: String? = null,
)
