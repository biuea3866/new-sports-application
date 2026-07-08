package com.sportsapp.application.dashboard.usecase
import com.sportsapp.application.dashboard.dto.GetOperationKpiCommand

import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.booking.dto.FacilityKpiSummary
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.goods.dto.GoodsKpiSummary
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import com.sportsapp.domain.ticketing.dto.TicketKpiSummary
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.ZonedDateTime

class GetOperationKpiUseCaseTest : BehaviorSpec({

    val bookingDomainService = mockk<BookingDomainService>()
    val goodsDomainService = mockk<GoodsDomainService>()
    val ticketingDomainService = mockk<TicketingDomainService>()
    val useCase = GetOperationKpiUseCase(bookingDomainService, goodsDomainService, ticketingDomainService)

    val ownerUserId = 10L
    val from = ZonedDateTime.now().minusDays(30)
    val to = ZonedDateTime.now()
    val command = GetOperationKpiCommand(ownerUserId = ownerUserId, from = from, to = to)

    Given("[U-01] 모든 도메인에 정상 KPI 데이터가 있을 때") {
        every { bookingDomainService.aggregateFacilityKpi(ownerUserId, from, to) } returns
            FacilityKpiSummary(
                utilizationRate = BigDecimal("72.50"),
                noShowRate = BigDecimal("5.00"),
                topFacilityIds = listOf("fac-001", "fac-002", "fac-003"),
            )
        every { goodsDomainService.aggregateGoodsKpi(ownerUserId, from, to) } returns
            GoodsKpiSummary(
                dailyRevenueTotal = BigDecimal("1500000"),
                inventoryTurnoverRate = BigDecimal("2.30"),
                outOfStockSkuCount = 3L,
            )
        every { ticketingDomainService.aggregateTicketKpi(ownerUserId, from, to) } returns
            TicketKpiSummary(
                totalSoldCount = 250L,
                refundRate = BigDecimal("3.20"),
                complimentaryCount = 10L,
            )

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("[U-01] 시설/굿즈/티켓 KPI가 모두 응답에 포함된다") {
                result.ownerUserId shouldBe ownerUserId
                result.facility.utilizationRate shouldBe BigDecimal("72.50")
                result.facility.noShowRate shouldBe BigDecimal("5.00")
                result.facility.topFacilityIds.size shouldBe 3
                result.goods.dailyRevenueTotal shouldBe BigDecimal("1500000")
                result.goods.outOfStockSkuCount shouldBe 3L
                result.ticket.totalSoldCount shouldBe 250L
                result.ticket.refundRate shouldBe BigDecimal("3.20")
                result.ticket.complimentaryCount shouldBe 10L
            }
        }
    }

    Given("[U-02] 데이터가 전혀 없는 신규 운영자일 때") {
        every { bookingDomainService.aggregateFacilityKpi(ownerUserId, from, to) } returns
            FacilityKpiSummary(
                utilizationRate = BigDecimal.ZERO,
                noShowRate = BigDecimal.ZERO,
                topFacilityIds = emptyList(),
            )
        every { goodsDomainService.aggregateGoodsKpi(ownerUserId, from, to) } returns
            GoodsKpiSummary(
                dailyRevenueTotal = BigDecimal.ZERO,
                inventoryTurnoverRate = BigDecimal.ZERO,
                outOfStockSkuCount = 0L,
            )
        every { ticketingDomainService.aggregateTicketKpi(ownerUserId, from, to) } returns
            TicketKpiSummary(
                totalSoldCount = 0L,
                refundRate = BigDecimal.ZERO,
                complimentaryCount = 0L,
            )

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("[U-02] 모든 집계값이 0인 응답이 반환된다") {
                result.facility.utilizationRate shouldBe BigDecimal.ZERO
                result.facility.topFacilityIds shouldBe emptyList()
                result.goods.dailyRevenueTotal shouldBe BigDecimal.ZERO
                result.ticket.totalSoldCount shouldBe 0L
                result.ticket.complimentaryCount shouldBe 0L
            }
        }
    }

    Given("[U-03] from이 to와 같은 경계 입력일 때") {
        val sameTime = ZonedDateTime.now()
        val boundaryCommand = GetOperationKpiCommand(ownerUserId = ownerUserId, from = sameTime, to = sameTime)

        every { bookingDomainService.aggregateFacilityKpi(ownerUserId, sameTime, sameTime) } returns
            FacilityKpiSummary(BigDecimal.ZERO, BigDecimal.ZERO, emptyList())
        every { goodsDomainService.aggregateGoodsKpi(ownerUserId, sameTime, sameTime) } returns
            GoodsKpiSummary(BigDecimal.ZERO, BigDecimal.ZERO, 0L)
        every { ticketingDomainService.aggregateTicketKpi(ownerUserId, sameTime, sameTime) } returns
            TicketKpiSummary(0L, BigDecimal.ZERO, 0L)

        When("execute를 호출하면") {
            val result = useCase.execute(boundaryCommand)

            Then("[U-03] 예외 없이 빈 KPI 응답이 반환된다") {
                result.ownerUserId shouldBe ownerUserId
                result.ticket.totalSoldCount shouldBe 0L
            }
        }
    }
})
