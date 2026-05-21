package com.sportsapp.application.dashboard

import com.sportsapp.domain.booking.SlotDomainService
import com.sportsapp.domain.facility.FacilityDomainService
import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.domain.ticketing.EventStatus
import com.sportsapp.domain.ticketing.TicketingDomainService
import com.sportsapp.domain.user.UserDomainService
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class GetMyDashboardSummaryUseCase(
    private val facilityDomainService: FacilityDomainService,
    private val slotDomainService: SlotDomainService,
    private val ticketingDomainService: TicketingDomainService,
    private val goodsDomainService: GoodsDomainService,
    private val userDomainService: UserDomainService,
) {
    @Transactional(readOnly = true)
    @Cacheable(value = ["b2bDashboardSummary"], key = "#userId")
    fun execute(userId: Long): DashboardSummaryResponse {
        val roleNames = userDomainService.getRolesForUser(userId).map { it.name }.toSet()

        val facilities = if (roleNames.contains("FACILITY_OWNER")) {
            val facilityIds = facilityDomainService.findIdsByOwnerUserId(userId)
            DashboardSummaryResponse.FacilitiesSummary(
                count = facilityDomainService.countByOwnerUserId(userId),
                activeSlotsToday = slotDomainService.countTodayByFacilityIds(facilityIds),
            )
        } else null

        val events = if (roleNames.contains("EVENT_HOST")) {
            val statusCounts = ticketingDomainService.countEventsByOwnerIdGroupByStatus(userId)
            DashboardSummaryResponse.EventsSummary(
                scheduled = statusCounts[EventStatus.SCHEDULED] ?: 0L,
                open = statusCounts[EventStatus.OPEN] ?: 0L,
                closed = statusCounts[EventStatus.CLOSED] ?: 0L,
                totalSeats = ticketingDomainService.sumTotalSeatsByOwnerId(userId),
                soldSeats = ticketingDomainService.sumSoldSeatsByOwnerId(userId),
            )
        } else null

        val products = if (roleNames.contains("GOODS_SELLER")) {
            DashboardSummaryResponse.ProductsSummary(
                active = goodsDomainService.countActiveProductsByOwnerId(userId),
                outOfStock = goodsDomainService.countOutOfStockProductsByOwnerId(userId),
            )
        } else null

        return DashboardSummaryResponse(
            facilities = facilities,
            events = events,
            products = products,
        )
    }
}
