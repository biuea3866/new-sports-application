package com.sportsapp.infrastructure.persistence.facility

import com.sportsapp.domain.facility.Facility
import com.sportsapp.domain.facility.FacilityRepository
import org.springframework.data.geo.Distance
import org.springframework.data.geo.Metrics
import org.springframework.data.geo.Point
import org.springframework.stereotype.Repository

@Repository
class FacilityRepositoryImpl(
    private val facilityMongoRepository: FacilityMongoRepository,
) : FacilityRepository {

    override fun save(facility: Facility): Facility =
        facilityMongoRepository.save(FacilityDocument.fromDomain(facility)).toDomain()

    override fun findById(id: String): Facility? =
        facilityMongoRepository.findById(id).orElse(null)?.toDomain()

    override fun findAllByGu(gu: String): List<Facility> =
        facilityMongoRepository.findAllByGu(gu).map { it.toDomain() }

    override fun findAllByGuAndType(gu: String, type: String): List<Facility> =
        facilityMongoRepository.findAllByGuAndType(gu, type).map { it.toDomain() }

    override fun findNear(lat: Double, lng: Double, maxDistanceMeters: Double): List<Facility> {
        val point = Point(lng, lat)
        val distanceKm = maxDistanceMeters / METERS_PER_KM
        val distance = Distance(distanceKm, Metrics.KILOMETERS)
        return facilityMongoRepository.findByLocationNear(point, distance)
            .content
            .map { it.content.toDomain() }
    }

    companion object {
        private const val METERS_PER_KM = 1000.0
    }
}
