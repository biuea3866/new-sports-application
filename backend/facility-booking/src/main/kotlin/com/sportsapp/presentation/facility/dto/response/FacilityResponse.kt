package com.sportsapp.presentation.facility.dto.response

import com.sportsapp.domain.facility.entity.Facility

data class FacilityResponse(
    val id: String,
    /**
     * 시설 고유 코드 — 등록 시 필수 입력값이라 포털 목록의 "코드" 컬럼·상세의 "코드" 필드가
     * 이 값을 그대로 렌더한다. 응답에서 빠지면 두 화면이 공백으로 보일 뿐 아니라, 상세의
     * 수정 폼이 이 값을 초기값으로 채우므로 빈 코드를 되돌려 보내는 2차 피해가 난다.
     */
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
            sidoCode = facility.sidoCode,
            sidoName = facility.sidoName,
            sigunguCode = facility.sigunguCode,
            sigunguName = facility.sigunguName,
            operatingHours = facility.operatingHours.map { OperatingHoursResponse.of(it) },
            holidays = facility.holidays.map { it.date.toString() },
        )
    }
}
