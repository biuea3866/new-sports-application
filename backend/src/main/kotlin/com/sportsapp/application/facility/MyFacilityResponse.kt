package com.sportsapp.application.facility

import com.sportsapp.domain.facility.Facility

data class MyFacilityResponse(
    val id: String,
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
    val ownerUserId: Long?,
) {
    companion object {
        fun of(facility: Facility): MyFacilityResponse = MyFacilityResponse(
            id = requireNotNull(facility.id) { "facility id must not be null" },
            code = facility.code,
            name = facility.name,
            gu = facility.gu,
            type = facility.type,
            address = facility.address,
            lat = facility.lat,
            lng = facility.lng,
            parking = facility.parking,
            tel = facility.tel,
            homePage = facility.homePage,
            eduYn = facility.eduYn,
            meta = facility.meta,
            ownerUserId = facility.ownerUserId,
        )
    }
}
