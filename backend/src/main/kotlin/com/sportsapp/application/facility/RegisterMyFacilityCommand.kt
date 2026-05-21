package com.sportsapp.application.facility

import com.sportsapp.domain.facility.FacilityAttributes

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
    val authUserId: Long,
) {
    fun toAttributes(): FacilityAttributes = FacilityAttributes(
        code = code,
        name = name,
        gu = gu,
        type = type,
        address = address,
        lat = lat,
        lng = lng,
        parking = parking,
        tel = tel,
        homePage = homePage,
        eduYn = eduYn,
        meta = meta,
    )
}
