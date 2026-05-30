package com.sportsapp.domain.facility

/**
 * 주소를 위경도 좌표로 변환하는 외부 Gateway.
 * 구현체는 Kakao Local REST API(또는 동일 스키마 mock)를 호출합니다.
 */
interface GeocodingGateway {
    fun geocode(address: String): Coordinate?
}

data class Coordinate(
    val lat: Double,
    val lng: Double,
)
