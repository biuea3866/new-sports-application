package com.sportsapp.infrastructure.persistence.facility

import com.sportsapp.domain.facility.Facility
import com.sportsapp.domain.facility.FacilityRepository
import org.springframework.data.geo.Distance
import org.springframework.data.geo.Metrics
import org.springframework.data.geo.Point
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class FacilityRepositoryImpl(
    private val facilityMongoRepository: FacilityMongoRepository,
) : FacilityRepository {

    override fun save(facility: Facility): Facility =
        facilityMongoRepository.save(facility)

    override fun saveAll(facilities: List<Facility>): List<Facility> =
        facilityMongoRepository.saveAll(facilities)

    override fun findById(id: String): Facility? =
        facilityMongoRepository.findByIdOrNull(id)?.takeIf { !it.isDeleted }

    override fun findAllByGu(gu: String): List<Facility> =
        facilityMongoRepository.findAllByGuAndDeletedAtIsNull(gu)

    override fun findAllByGuAndType(gu: String, type: String): List<Facility> =
        facilityMongoRepository.findAllByGuAndTypeAndDeletedAtIsNull(gu, type)

    override fun findNear(lat: Double, lng: Double, maxDistanceMeters: Double): List<Facility> {
        val point = Point(lng, lat)
        val distanceKm = maxDistanceMeters / METERS_PER_KM
        val distance = Distance(distanceKm, Metrics.KILOMETERS)
        return facilityMongoRepository.findByLocationNearAndDeletedAtIsNull(point, distance)
            .content
            .map { it.content }
    }

    companion object {
        private const val METERS_PER_KM = 1000.0
    }
}
