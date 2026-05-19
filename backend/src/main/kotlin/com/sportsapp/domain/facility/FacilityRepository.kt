package com.sportsapp.domain.facility

interface FacilityRepository {
    fun save(facility: Facility): Facility
    fun findById(id: String): Facility?
    fun findAllByGu(gu: String): List<Facility>
    fun findAllByGuAndType(gu: String, type: String): List<Facility>
    fun findNear(lat: Double, lng: Double, maxDistanceMeters: Double): List<Facility>
}
