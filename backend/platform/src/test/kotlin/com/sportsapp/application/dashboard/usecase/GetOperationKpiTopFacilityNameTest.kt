package com.sportsapp.application.dashboard.usecase

import com.sportsapp.application.dashboard.dto.GetOperationKpiCommand
import com.sportsapp.domain.booking.dto.FacilityKpiSummary
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.service.FacilityDomainService
import com.sportsapp.domain.goods.dto.GoodsKpiSummary
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.ticketing.dto.TicketKpiSummary
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.geo.Point
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * 운영 인사이트 "인기 시설 TOP5" — 시설 id가 아니라 사람이 읽는 시설명을 실어야 한다.
 *
 * booking 집계는 시설 id(Mongo ObjectId 문자열)만 알고 있어, 이름 해석 없이 응답하면 화면에
 * `시설 #6a6c334c3fc5f44fbb2c26d8`처럼 내부 식별자가 그대로 노출된다. 두 컨텍스트(booking·facility)를
 * 아는 유일한 레이어인 application이 소유 컨텍스트의 DomainService로 이름을 조회해 조합한다.
 */
class GetOperationKpiTopFacilityNameTest : BehaviorSpec({

    val bookingDomainService = mockk<BookingDomainService>()
    val goodsDomainService = mockk<GoodsDomainService>()
    val ticketingDomainService = mockk<TicketingDomainService>()
    val facilityDomainService = mockk<FacilityDomainService>()
    val useCase = GetOperationKpiUseCase(
        bookingDomainService,
        goodsDomainService,
        ticketingDomainService,
        facilityDomainService,
    )

    val ownerUserId = 10L
    val from = ZonedDateTime.now().minusDays(7)
    val to = ZonedDateTime.now()
    val command = GetOperationKpiCommand(ownerUserId = ownerUserId, from = from, to = to)

    fun buildFacility(id: String, name: String) = Facility(
        id = id, code = "C-$id", name = name,
        gu = "강남구", type = "체육관", address = "서울시 강남구",
        location = Point(127.0, 37.5),
        parking = true, tel = "02-555-0101", homePage = "", eduYn = false,
        meta = emptyMap(), ownerUserId = ownerUserId,
        sidoCode = null, sidoName = null, sigunguCode = null, sigunguName = null,
    )

    fun stubNonFacilityKpi() {
        every { goodsDomainService.aggregateGoodsKpi(ownerUserId, from, to) } returns
            GoodsKpiSummary(BigDecimal.ZERO, BigDecimal.ZERO, 0L)
        every { ticketingDomainService.aggregateTicketKpi(ownerUserId, from, to) } returns
            TicketKpiSummary(0L, BigDecimal.ZERO, 0L)
    }

    Given("인기 시설 집계에 시설 id가 실려 있을 때") {
        stubNonFacilityKpi()
        every { bookingDomainService.aggregateFacilityKpi(ownerUserId, from, to) } returns
            FacilityKpiSummary(
                utilizationRate = BigDecimal("1.00"),
                noShowRate = BigDecimal.ZERO,
                topFacilityIds = listOf("6a6c334c3fc5f44fbb2c26d8"),
            )
        every { facilityDomainService.findBy("6a6c334c3fc5f44fbb2c26d8") } returns
            buildFacility("6a6c334c3fc5f44fbb2c26d8", "강남 스포츠센터")

        When("운영 KPI를 조회하면") {
            val result = useCase.execute(command)

            Then("시설명이 응답에 실린다") {
                result.facility.topFacilities.first().name shouldBe "강남 스포츠센터"
            }

            Then("시설 id도 함께 실려 상세로 이동할 수 있다") {
                result.facility.topFacilities.first().id shouldBe "6a6c334c3fc5f44fbb2c26d8"
            }
        }
    }

    Given("집계에는 있으나 삭제되어 조회되지 않는 시설이 섞여 있을 때") {
        stubNonFacilityKpi()
        every { bookingDomainService.aggregateFacilityKpi(ownerUserId, from, to) } returns
            FacilityKpiSummary(
                utilizationRate = BigDecimal("1.00"),
                noShowRate = BigDecimal.ZERO,
                topFacilityIds = listOf("alive-id", "deleted-id"),
            )
        every { facilityDomainService.findBy("alive-id") } returns buildFacility("alive-id", "잠실 실내체육관")
        every { facilityDomainService.findBy("deleted-id") } returns null

        When("운영 KPI를 조회하면") {
            val result = useCase.execute(command)

            // 이름을 못 찾는다고 순위에서 빼면 TOP5 개수가 달라져 다른 지표와 어긋난다.
            Then("조회되지 않는 시설도 순위에서 빠지지 않는다") {
                result.facility.topFacilities.size shouldBe 2
            }

            Then("이름을 못 찾은 시설은 대체 문구를 쓰고 내부 식별자를 노출하지 않는다") {
                val deletedFacility = result.facility.topFacilities[1]
                deletedFacility.name shouldBe "알 수 없는 시설"
            }
        }
    }

    Given("인기 시설 집계가 비어 있을 때") {
        stubNonFacilityKpi()
        every { bookingDomainService.aggregateFacilityKpi(ownerUserId, from, to) } returns
            FacilityKpiSummary(BigDecimal.ZERO, BigDecimal.ZERO, emptyList())

        When("운영 KPI를 조회하면") {
            val result = useCase.execute(command)

            Then("빈 목록이 반환된다") {
                result.facility.topFacilities shouldBe emptyList()
            }
        }
    }
})
