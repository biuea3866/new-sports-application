package com.sportsapp.domain.facility

/**
 * 공공데이터포털 전국 공공체육시설 목록을 조회하는 외부 Gateway.
 * 구현체는 data.go.kr API(또는 동일 스키마 mock)를 호출합니다.
 */
interface PublicSportsFacilityGateway {
    fun fetchPage(pageNo: Int, numOfRows: Int): List<PublicFacility>
}

data class PublicFacility(
    val externalId: String,
    val name: String,
    val gu: String,
    val type: String,
    val address: String,
    val lat: Double?,
    val lng: Double?,
    val tel: String,
) {
    fun toAttributes(): FacilityAttributes? {
        if (externalId.isBlank() || name.isBlank()) return null
        return FacilityAttributes(
            code = externalId,
            name = name,
            gu = gu,
            type = type,
            address = address,
            lat = lat ?: 0.0,
            lng = lng ?: 0.0,
            parking = false,
            tel = tel,
            homePage = "",
            eduYn = false,
            meta = emptyMap(),
        )
    }
}
