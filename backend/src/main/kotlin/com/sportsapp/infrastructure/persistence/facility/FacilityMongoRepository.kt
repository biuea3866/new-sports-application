package com.sportsapp.infrastructure.persistence.facility

import org.springframework.data.geo.Distance
import org.springframework.data.geo.GeoResults
import org.springframework.data.geo.Point
import org.springframework.data.mongodb.repository.MongoRepository

interface FacilityMongoRepository : MongoRepository<FacilityDocument, String> {
    fun findAllByGu(gu: String): List<FacilityDocument>
    fun findAllByGuAndType(gu: String, type: String): List<FacilityDocument>
    fun findByLocationNear(location: Point, distance: Distance): GeoResults<FacilityDocument>
}
