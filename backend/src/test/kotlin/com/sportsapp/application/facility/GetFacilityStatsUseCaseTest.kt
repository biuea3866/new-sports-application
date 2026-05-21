package com.sportsapp.application.facility

import com.sportsapp.domain.facility.FacilityDomainService
import com.sportsapp.domain.facility.FacilityStats
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

class GetFacilityStatsUseCaseTest : BehaviorSpec({

    val facilityDomainService = mockk<FacilityDomainService>()
    val getFacilityStatsUseCase = GetFacilityStatsUseCase(facilityDomainService)

    val from = ZonedDateTime.now().minusDays(30)
    val to = ZonedDateTime.now()

    Given("[U-03] facilityId + period가 DomainService에 전달되는지 검증") {
        val command = GetFacilityStatsCommand(
            operatorId = 1L,
            facilityId = "fac-001",
            from = from,
            to = to,
        )
        val stats = FacilityStats(
            facilityId = "fac-001",
            name = "테스트 시설",
            totalBookings = 10L,
            totalRevenue = 50000L,
            noShowCount = 1L,
            avgRating = null,
        )
        every {
            facilityDomainService.aggregateStats(1L, "fac-001", any(), any())
        } returns listOf(stats)

        When("[U-03] execute 호출 시") {
            val result = getFacilityStatsUseCase.execute(command)

            Then("[U-03] operatorId=1, facilityId=fac-001 이 DomainService에 전달된다") {
                verify(exactly = 1) { facilityDomainService.aggregateStats(1L, "fac-001", any(), any()) }
            }

            Then("[U-03] FacilityStatsResponse가 올바르게 반환된다") {
                result.size shouldBe 1
                result[0].facilityId shouldBe "fac-001"
                result[0].totalBookings shouldBe 10L
                result[0].totalRevenue shouldBe 50000L
            }
        }
    }

    Given("[U-03] facilityId=null 전체 집계") {
        val command = GetFacilityStatsCommand(
            operatorId = 1L,
            facilityId = null,
            from = from,
            to = to,
        )
        every { facilityDomainService.aggregateStats(1L, null, any(), any()) } returns emptyList()

        When("[U-03] execute 호출 시") {
            val result = getFacilityStatsUseCase.execute(command)

            Then("[U-03] facilityId=null로 DomainService가 호출되고 빈 리스트가 반환된다") {
                verify(exactly = 1) { facilityDomainService.aggregateStats(1L, null, any(), any()) }
                result.size shouldBe 0
            }
        }
    }
})
