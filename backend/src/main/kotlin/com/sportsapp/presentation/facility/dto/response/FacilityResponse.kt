package com.sportsapp.presentation.facility.dto.response

import com.sportsapp.domain.facility.entity.Facility

data class FacilityResponse(
    val id: String,
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
    val sidoCode: String,
    val sidoName: String,
    val sigunguCode: String,
    val sigunguName: String,
    val operatingHours: List<OperatingHoursResponse>,
    val holidays: List<String>,
) {
    companion object {
        fun of(facility: Facility): FacilityResponse = FacilityResponse(
            id = requireNotNull(facility.id) { "facility id must not be null" },
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
            sidoCode = facility.sidoCode,
            sidoName = facility.sidoName,
            sigunguCode = facility.sigunguCode,
            sigunguName = facility.sigunguName,
            operatingHours = facility.operatingHours.map { OperatingHoursResponse.of(it) },
            holidays = facility.holidays.map { it.date.toString() },
        )
    }
}
