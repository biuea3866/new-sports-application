package com.sportsapp.presentation.mcp.controller

import com.sportsapp.application.facility.usecase.GetGuTypeStatsUseCase
import com.sportsapp.application.facility.usecase.GetRegionTypeStatsUseCase
import com.sportsapp.domain.facility.dto.GuTypeCount
import com.sportsapp.domain.facility.dto.RegionTypeCount
import com.sportsapp.presentation.mcp.audit.McpAuditLogAsyncRecorder
import com.sportsapp.presentation.mcp.dto.response.McpResponseStatus
import com.sportsapp.presentation.mcp.controller.McpFacilityStatsTools
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class McpFacilityStatsToolsTest : BehaviorSpec({

    val getGuTypeStatsUseCase = mockk<GetGuTypeStatsUseCase>()
    val getRegionTypeStatsUseCase = mockk<GetRegionTypeStatsUseCase>()
    val mcpAuditLogAsyncRecorder = mockk<McpAuditLogAsyncRecorder>(relaxed = true)
    val mcpFacilityStatsTools = McpFacilityStatsTools(getGuTypeStatsUseCase, getRegionTypeStatsUseCase, mcpAuditLogAsyncRecorder)

    afterEach { clearMocks(mcpAuditLogAsyncRecorder) }

    Given("getFacilityStats tool") {
        val statsList = listOf(
            GuTypeCount(gu = "강남구", type = "FUTSAL", count = 5L),
            GuTypeCount(gu = "강남구", type = "TENNIS", count = 3L),
            GuTypeCount(gu = "마포구", type = "FUTSAL", count = 2L),
        )

        When("[U-07] getFacilityStats를 호출하면") {
            every { getGuTypeStatsUseCase.execute() } returns statsList

            val result = mcpFacilityStatsTools.getFacilityStats()

            Then("[U-07] OK 상태와 구-유형별 통계 목록이 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                result.data shouldNotBe null
                val data = requireNotNull(result.data)
                data.size shouldBe 3
                data[0].gu shouldBe "강남구"
                data[0].type shouldBe "FUTSAL"
                data[0].count shouldBe 5L
            }
        }

        When("[U-08] 통계가 비어있으면") {
            every { getGuTypeStatsUseCase.execute() } returns emptyList()

            val result = mcpFacilityStatsTools.getFacilityStats()

            Then("[U-08] OK 상태와 빈 목록이 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                val data = requireNotNull(result.data)
                data.size shouldBe 0
            }
        }

        When("[U-09] getFacilityStats를 호출하면 UseCase가 호출된다") {
            val localUseCase = mockk<GetGuTypeStatsUseCase>()
            val localRegionUseCase = mockk<GetRegionTypeStatsUseCase>()
            val localRecorder = mockk<McpAuditLogAsyncRecorder>(relaxed = true)
            val localTools = McpFacilityStatsTools(localUseCase, localRegionUseCase, localRecorder)
            every { localUseCase.execute() } returns statsList

            localTools.getFacilityStats()

            Then("[U-09] GetGuTypeStatsUseCase.execute()가 정확히 1회 호출된다") {
                verify(exactly = 1) { localUseCase.execute() }
            }
        }

        When("[U-audit-01] getFacilityStats 호출 시 audit recorder가 1회 호출된다") {
            every { getGuTypeStatsUseCase.execute() } returns statsList

            mcpFacilityStatsTools.getFacilityStats()

            Then("[U-audit-01] mcpAuditLogAsyncRecorder.record가 정확히 1회 호출된다") {
                verify(exactly = 1) {
                    mcpAuditLogAsyncRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any())
                }
            }
        }
    }

    Given("getRegionTypeStats tool") {
        val regionStatsList = listOf(
            RegionTypeCount(sidoCode = "26", sidoName = "부산광역시", sigunguCode = "26410", sigunguName = "해운대구", type = "수영장", count = 5L),
            RegionTypeCount(sidoCode = "11", sidoName = "서울특별시", sigunguCode = "11680", sigunguName = "강남구", type = "헬스장", count = 2L),
        )

        When("getRegionTypeStats를 호출하면") {
            every { getRegionTypeStatsUseCase.execute() } returns regionStatsList

            val result = mcpFacilityStatsTools.getRegionTypeStats()

            Then("OK 상태와 시도·시군구·유형별 통계 목록이 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                val data = requireNotNull(result.data)
                data.size shouldBe 2
                data[0].sidoName shouldBe "부산광역시"
                data[0].sigunguName shouldBe "해운대구"
                data[0].count shouldBe 5L
            }
        }

        When("통계가 비어있으면") {
            every { getRegionTypeStatsUseCase.execute() } returns emptyList()

            val result = mcpFacilityStatsTools.getRegionTypeStats()

            Then("OK 상태와 빈 목록이 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                val data = requireNotNull(result.data)
                data.size shouldBe 0
            }
        }

        When("getRegionTypeStats 호출 시 audit recorder가 1회 호출된다") {
            every { getRegionTypeStatsUseCase.execute() } returns regionStatsList

            mcpFacilityStatsTools.getRegionTypeStats()

            Then("mcpAuditLogAsyncRecorder.record가 정확히 1회 호출된다") {
                verify(exactly = 1) {
                    mcpAuditLogAsyncRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any())
                }
            }
        }
    }
})
