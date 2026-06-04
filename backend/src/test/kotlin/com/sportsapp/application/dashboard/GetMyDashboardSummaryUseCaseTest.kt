package com.sportsapp.application.dashboard

import com.sportsapp.domain.booking.service.SlotDomainService
import com.sportsapp.domain.facility.service.FacilityDomainService
import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.domain.ticketing.EventStatus
import com.sportsapp.domain.ticketing.TicketingDomainService
import com.sportsapp.domain.user.entity.Role
import com.sportsapp.domain.user.service.UserDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class GetMyDashboardSummaryUseCaseTest : BehaviorSpec({

    val facilityDomainService = mockk<FacilityDomainService>()
    val slotDomainService = mockk<SlotDomainService>()
    val ticketingDomainService = mockk<TicketingDomainService>()
    val goodsDomainService = mockk<GoodsDomainService>()
    val userDomainService = mockk<UserDomainService>()
    val useCase = GetMyDashboardSummaryUseCase(
        facilityDomainService,
        slotDomainService,
        ticketingDomainService,
        goodsDomainService,
        userDomainService,
    )

    Given("[U-01] FACILITY_OWNER + EVENT_HOST + GOODS_SELLER 모두 보유한 사용자") {
        val userId = 1L

        every { userDomainService.getRolesForUser(userId) } returns listOf(
            Role(name = "FACILITY_OWNER"),
            Role(name = "EVENT_HOST"),
            Role(name = "GOODS_SELLER"),
        )

        every { facilityDomainService.countByOwnerUserId(userId) } returns 3L
        every { facilityDomainService.findIdsByOwnerUserId(userId) } returns listOf("fac-001", "fac-002")
        every { slotDomainService.countTodayByFacilityIds(any()) } returns 12L

        every { ticketingDomainService.countEventsByOwnerIdGroupByStatus(userId) } returns
            mapOf(
                EventStatus.SCHEDULED to 2L,
                EventStatus.OPEN to 1L,
                EventStatus.CLOSED to 5L,
            )
        every { ticketingDomainService.sumTotalSeatsByOwnerId(userId) } returns 800L
        every { ticketingDomainService.sumSoldSeatsByOwnerId(userId) } returns 213L

        every { goodsDomainService.countActiveProductsByOwnerId(userId) } returns 8L
        every { goodsDomainService.countOutOfStockProductsByOwnerId(userId) } returns 2L

        When("execute를 호출하면") {
            val result = useCase.execute(userId)

            Then("[U-01] 응답에 모든 도메인 필드가 포함된다") {
                val facilities = result.facilities
                facilities.shouldNotBeNull()
                facilities.count shouldBe 3L
                facilities.activeSlotsToday shouldBe 12L

                val events = result.events
                events.shouldNotBeNull()
                events.scheduled shouldBe 2L
                events.open shouldBe 1L
                events.closed shouldBe 5L
                events.totalSeats shouldBe 800L
                events.soldSeats shouldBe 213L

                val products = result.products
                products.shouldNotBeNull()
                products.active shouldBe 8L
                products.outOfStock shouldBe 2L
            }
        }
    }

    Given("[U-02] GOODS_SELLER 만 보유한 사용자") {
        val userId = 2L

        every { userDomainService.getRolesForUser(userId) } returns listOf(
            Role(name = "GOODS_SELLER"),
        )
        every { goodsDomainService.countActiveProductsByOwnerId(userId) } returns 5L
        every { goodsDomainService.countOutOfStockProductsByOwnerId(userId) } returns 1L

        When("execute를 호출하면") {
            val result = useCase.execute(userId)

            Then("[U-02] facilities, events 필드는 null, products만 존재한다") {
                result.facilities.shouldBeNull()
                result.events.shouldBeNull()

                val products = result.products
                products.shouldNotBeNull()
                products.active shouldBe 5L
                products.outOfStock shouldBe 1L
            }
        }
    }

    Given("[U-02b] GOODS_SELLER 사용자의 outOfStock 카운트 검증") {
        val userId = 3L

        every { userDomainService.getRolesForUser(userId) } returns listOf(
            Role(name = "GOODS_SELLER"),
        )
        every { goodsDomainService.countActiveProductsByOwnerId(userId) } returns 4L
        every { goodsDomainService.countOutOfStockProductsByOwnerId(userId) } returns 3L

        When("execute를 호출하면") {
            val result = useCase.execute(userId)

            Then("[U-02] outOfStock이 Stock.quantity=0 기준 카운트를 반환한다") {
                val products = result.products
                products.shouldNotBeNull()
                products.outOfStock shouldBe 3L
            }
        }
    }
})
