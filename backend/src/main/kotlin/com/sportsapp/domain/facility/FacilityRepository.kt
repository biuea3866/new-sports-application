package com.sportsapp.domain.facility

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface FacilityRepository {
    fun save(facility: Facility): Facility
    fun saveAll(facilities: List<Facility>): List<Facility>
    fun findById(id: String): Facility?
    fun findByCode(code: String): Facility?
    fun findAllByGu(gu: String): List<Facility>
    fun findAllByGuAndType(gu: String, type: String): List<Facility>
    fun findNear(lat: Double, lng: Double, maxDistanceMeters: Double): List<Facility>
    fun findAll(gu: String?, type: String?, pageable: Pageable): Page<Facility>
    fun aggregateGuType(): List<GuTypeCount>
    fun upsertByCode(facility: Facility): Facility
    fun findByOwnerUserId(ownerUserId: Long, pageable: Pageable): Page<Facility>
    fun findByIdAndOwnerUserId(id: String, ownerUserId: Long): Facility?
}
