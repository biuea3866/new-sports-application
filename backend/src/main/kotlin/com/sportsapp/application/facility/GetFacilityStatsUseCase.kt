package com.sportsapp.application.facility

import com.sportsapp.domain.booking.BookingDomainService
import com.sportsapp.domain.facility.FacilityDomainService
import com.sportsapp.domain.facility.FacilityStats
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class GetFacilityStatsUseCase(
    private val facilityDomainService: FacilityDomainService,
    private val bookingDomainService: BookingDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: GetFacilityStatsCommand): List<FacilityStatsResponse> {
        val facilities = facilityDomainService.getFacilitiesForStats(command.operatorId, command.facilityId)
        val facilityIds = facilities.mapNotNull { it.id }
        val bookingStatsByFacilityId = bookingDomainService.aggregateStatsByFacilityIds(
            facilityIds = facilityIds,
            from = command.from,
            to = command.to,
        ).associateBy { it.facilityId }

        return facilities.map { facility ->
            val facilityId = requireNotNull(facility.id) { "facility id must not be null" }
            val bookingStats = bookingStatsByFacilityId[facilityId]
            FacilityStatsResponse.of(FacilityStats(
                facilityId = facilityId,
                name = facility.name,
                totalBookings = bookingStats?.totalBookings ?: 0L,
                totalRevenue = bookingStats?.totalRevenue ?: 0L,
                noShowCount = bookingStats?.noShowCount ?: 0L,
                avgRating = null,
            ))
        }
    }
}
