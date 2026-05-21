package com.sportsapp.domain.facility

import com.sportsapp.domain.booking.BookingRepository
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import java.time.ZonedDateTime
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
@Profile("!test-jpa")
class FacilityDomainService(
    private val facilityRepository: FacilityRepository,
    private val bookingRepository: BookingRepository,
) {

    fun register(attributes: FacilityAttributes): Facility {
        val facility = Facility.create(attributes)
        return facilityRepository.save(facility)
    }

    fun findByGu(gu: String): List<Facility> =
        facilityRepository.findAllByGu(gu)

    fun findByGuAndType(gu: String, type: String): List<Facility> =
        facilityRepository.findAllByGuAndType(gu, type)

    fun findNear(lat: Double, lng: Double, maxDistanceMeters: Double): List<Facility> =
        facilityRepository.findNear(lat, lng, maxDistanceMeters)

    fun list(gu: String?, type: String?, pageable: Pageable): Page<Facility> =
        facilityRepository.findAll(gu, type, pageable)

    fun getById(id: String): Facility =
        facilityRepository.findById(id) ?: throw FacilityNotFoundException(id)

    fun aggregateGuType(): List<GuTypeCount> =
        facilityRepository.aggregateGuType()

    fun findIdsByOwnerUserId(ownerUserId: Long): List<String> =
        facilityRepository.findIdsByOwnerUserId(ownerUserId)

    fun countByOwnerUserId(ownerUserId: Long): Long =
        facilityRepository.countByOwnerUserId(ownerUserId)

    fun getByIdAndOwnerUserId(id: String, ownerUserId: Long): Facility =
        facilityRepository.findByIdAndOwnerUserId(id, ownerUserId)
            ?: throw FacilityNotFoundException(id)

    fun listByOwnerUserId(ownerUserId: Long, pageable: Pageable): Page<Facility> =
        facilityRepository.findByOwnerUserId(ownerUserId, pageable)

    fun registerForOwner(attributes: FacilityAttributes, authUserId: Long): Facility {
        val facility = Facility.create(attributes)
        facility.assignOwner(authUserId)
        return facilityRepository.save(facility)
    }

    fun updateMetaForOwner(id: String, authUserId: Long, patch: Map<String, String>): Facility {
        val facility = facilityRepository.findByIdAndOwnerUserId(id, authUserId)
            ?: throw FacilityNotFoundException(id)
        val updated = facility.updateMeta(patch)
        return facilityRepository.save(updated)
    }

    fun deleteForOwner(id: String, authUserId: Long) {
        val facility = facilityRepository.findByIdAndOwnerUserId(id, authUserId)
            ?: throw FacilityNotFoundException(id)
        facility.softDelete(authUserId)
        facilityRepository.save(facility)
    }

    fun aggregateStats(
        operatorId: Long,
        facilityId: String?,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): List<FacilityStats> {
        val facilities = if (facilityId != null) {
            val facility = facilityRepository.findById(facilityId) ?: throw FacilityNotFoundException(facilityId)
            facility.requireOwnedBy(operatorId)
            listOf(facility)
        } else {
            facilityRepository.findIdsByOwnerUserId(operatorId)
                .mapNotNull { facilityRepository.findById(it) }
        }

        val facilityIds = facilities.mapNotNull { it.id }
        val bookingStatsByFacilityId = bookingRepository.aggregateStatsByFacilityIds(facilityIds, from, to)
            .associateBy { it.facilityId }

        return facilities.map { facility ->
            val fid = requireNotNull(facility.id) { "facility id must not be null" }
            val bookingStats = bookingStatsByFacilityId[fid]
            FacilityStats(
                facilityId = fid,
                name = facility.name,
                totalBookings = bookingStats?.totalBookings ?: 0L,
                totalRevenue = bookingStats?.totalRevenue ?: 0L,
                noShowCount = bookingStats?.noShowCount ?: 0L,
                avgRating = null,
            )
        }
    }

    fun update(operatorId: Long, facilityId: String, command: UpdateFacilityInfoCommand): Facility {
        val facility = facilityRepository.findById(facilityId) ?: throw FacilityNotFoundException(facilityId)
        facility.requireOwnedBy(operatorId)
        val updated = facility.updateInfo(
            name = command.name,
            address = command.address,
            operatingHours = command.operatingHours,
            basePrice = command.basePrice,
        )
        return facilityRepository.save(updated)
    }

    fun bulkImport(rows: List<LegacyRow>): BulkImportResult {
        var insertedCount = 0
        var updatedCount = 0
        var skippedCount = 0

        rows.forEach { row ->
            val attributes = LegacyToFacilityMapper.map(row)
            if (attributes == null) {
                skippedCount++
                return@forEach
            }
            val facility = Facility.create(attributes)
            val existing = facilityRepository.findByCode(attributes.code)
            if (existing == null) {
                facilityRepository.save(facility)
                insertedCount++
            } else {
                facilityRepository.upsertByCode(facility)
                updatedCount++
            }
        }

        return BulkImportResult(
            insertedCount = insertedCount,
            updatedCount = updatedCount,
            skippedCount = skippedCount,
        )
    }
}
