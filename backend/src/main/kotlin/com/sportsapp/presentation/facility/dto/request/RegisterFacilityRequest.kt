package com.sportsapp.presentation.facility.dto.request

import com.sportsapp.application.facility.dto.RegisterMyFacilityCommand

data class RegisterFacilityRequest(
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
) {
    fun toCommand(ownerUserId: Long) = RegisterMyFacilityCommand(
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
        ownerUserId = ownerUserId,
    )
}
