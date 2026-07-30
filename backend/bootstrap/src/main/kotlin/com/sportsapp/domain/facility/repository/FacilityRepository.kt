package com.sportsapp.domain.facility.repository

import com.sportsapp.domain.facility.dto.GuTypeCount
import com.sportsapp.domain.facility.dto.RegionTypeCount
import com.sportsapp.domain.facility.entity.Facility
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
    fun findAll(sidoCode: String?, sigunguCode: String?, gu: String?, type: String?, pageable: Pageable): Page<Facility>
    fun aggregateGuType(): List<GuTypeCount>
    fun aggregateRegionType(): List<RegionTypeCount>
    fun upsertByCode(facility: Facility): Facility
    fun findByOwnerUserId(ownerUserId: Long, pageable: Pageable): Page<Facility>
    fun findByIdAndOwnerUserId(id: String, ownerUserId: Long): Facility?
    fun findIdsByOwnerUserId(ownerUserId: Long): List<String>
    fun countByOwnerUserId(ownerUserId: Long): Long
    fun findAllForBackfill(pageable: Pageable): Page<Facility>
}
