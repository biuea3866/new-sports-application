package com.sportsapp.application.dashboard

import com.sportsapp.domain.booking.SlotDomainService
import com.sportsapp.domain.common.UserRoleName
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
        val roles = userDomainService.getRolesForUser(userId)
            .mapNotNull { UserRoleName.fromNameOrNull(it.name) }
            .toSet()
        return DashboardSummaryResponse(
            facilities = if (roles.contains(UserRoleName.FACILITY_OWNER)) buildFacilitiesSummary(userId) else null,
            events = if (roles.contains(UserRoleName.EVENT_HOST)) buildEventsSummary(userId) else null,
            products = if (roles.contains(UserRoleName.GOODS_SELLER)) buildProductsSummary(userId) else null,
        )
    }

    private fun buildFacilitiesSummary(userId: Long): DashboardSummaryResponse.FacilitiesSummary {
        val facilityIds = facilityDomainService.findIdsByOwnerUserId(userId)
        return DashboardSummaryResponse.FacilitiesSummary(
            count = facilityDomainService.countByOwnerUserId(userId),
            activeSlotsToday = slotDomainService.countTodayByFacilityIds(facilityIds),
        )
    }

    private fun buildEventsSummary(userId: Long): DashboardSummaryResponse.EventsSummary {
        val statusCounts = ticketingDomainService.countEventsByOwnerIdGroupByStatus(userId)
        return DashboardSummaryResponse.EventsSummary(
            scheduled = statusCounts[EventStatus.SCHEDULED] ?: 0L,
            open = statusCounts[EventStatus.OPEN] ?: 0L,
            closed = statusCounts[EventStatus.CLOSED] ?: 0L,
            totalSeats = ticketingDomainService.sumTotalSeatsByOwnerId(userId),
            soldSeats = ticketingDomainService.sumSoldSeatsByOwnerId(userId),
        )
    }

    private fun buildProductsSummary(userId: Long): DashboardSummaryResponse.ProductsSummary =
        DashboardSummaryResponse.ProductsSummary(
            active = goodsDomainService.countActiveProductsByOwnerId(userId),
            outOfStock = goodsDomainService.countOutOfStockProductsByOwnerId(userId),
        )
}
