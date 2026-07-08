package com.sportsapp.application.facility.dto

import com.sportsapp.domain.facility.vo.FacilityAttributes

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
    val sido: String? = null,
) {
    // region은 미해석 상태(UNSPECIFIED)로 두고 sidoHint만 실어 보낸다 — 실제 해석은 DomainService가 수행한다.
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
        sidoHint = sido,
    )
}
