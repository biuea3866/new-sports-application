package com.sportsapp.presentation.facility

import com.sportsapp.application.facility.RegisterMyFacilityCommand

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
    val meta: Map<String, String> = emptyMap(),
) {
    fun toCommand(authUserId: Long): RegisterMyFacilityCommand = RegisterMyFacilityCommand(
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
        authUserId = authUserId,
    )
}
