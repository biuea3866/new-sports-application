package com.sportsapp.domain.facility

interface GeocodingGateway {
    fun geocode(address: String): Coordinate?
}
