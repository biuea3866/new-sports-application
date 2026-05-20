package com.sportsapp.infrastructure.persistence.facility

import com.sportsapp.domain.facility.Facility
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.geo.Distance
import org.springframework.data.geo.GeoResults
import org.springframework.data.geo.Point
import org.springframework.data.mongodb.repository.MongoRepository

interface FacilityMongoRepository : MongoRepository<Facility, String> {
    fun findAllByGuAndDeletedAtIsNull(gu: String): List<Facility>
    fun findAllByGuAndTypeAndDeletedAtIsNull(gu: String, type: String): List<Facility>
    fun findByLocationNearAndDeletedAtIsNull(location: Point, distance: Distance): GeoResults<Facility>
    fun findAllByDeletedAtIsNull(pageable: Pageable): Page<Facility>
    fun findAllByGuAndDeletedAtIsNull(gu: String, pageable: Pageable): Page<Facility>
    fun findAllByTypeAndDeletedAtIsNull(type: String, pageable: Pageable): Page<Facility>
    fun findAllByGuAndTypeAndDeletedAtIsNull(gu: String, type: String, pageable: Pageable): Page<Facility>
}
