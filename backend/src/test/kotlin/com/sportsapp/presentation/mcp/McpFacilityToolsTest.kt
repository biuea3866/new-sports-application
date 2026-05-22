package com.sportsapp.presentation.mcp

import com.sportsapp.application.facility.FacilityCriteria
import com.sportsapp.application.facility.FacilityResponse
import com.sportsapp.application.facility.ListFacilitiesUseCase
import com.sportsapp.presentation.mcp.response.McpResponseStatus
import com.sportsapp.presentation.mcp.toolregistry.McpFacilityTools
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class McpFacilityToolsTest : BehaviorSpec({

    val listFacilitiesUseCase = mockk<ListFacilitiesUseCase>()
    val mcpFacilityTools = McpFacilityTools(listFacilitiesUseCase)

    Given("getFacilities tool") {
        val facility = FacilityResponse(
            id = "FAC-001",
            name = "서울 강남 풋살장",
            gu = "강남구",
            type = "FUTSAL",
            address = "서울 강남구 역삼동 123",
            lat = 37.4979,
            lng = 127.0276,
            parking = true,
            tel = "02-1234-5678",
            homePage = "https://example.com",
            eduYn = false,
        )

        When("[U-01] gu, type 필터 없이 getFacilities를 호출하면") {
            val criteriaSlot = slot<FacilityCriteria>()
            every { listFacilitiesUseCase.execute(capture(criteriaSlot)) } returns
                PageImpl(listOf(facility), PageRequest.of(0, 50), 1)

            val result = mcpFacilityTools.getFacilities(gu = null, type = null, page = 0, size = 20)

            Then("[U-01] OK 상태와 시설 목록이 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                result.data shouldNotBe null
                val data = requireNotNull(result.data)
                data.size shouldBe 1
                data[0].id shouldBe "FAC-001"
                data[0].name shouldBe "서울 강남 풋살장"
            }
        }

        When("[U-02] gu, type 필터를 지정해 getFacilities를 호출하면") {
            val criteriaSlot = slot<FacilityCriteria>()
            every { listFacilitiesUseCase.execute(capture(criteriaSlot)) } returns
                PageImpl(listOf(facility), PageRequest.of(0, 20), 1)

            mcpFacilityTools.getFacilities(gu = "강남구", type = "FUTSAL", page = 0, size = 20)

            Then("[U-02] FacilityCriteria에 필터 값이 전달된다") {
                criteriaSlot.captured.gu shouldBe "강남구"
                criteriaSlot.captured.type shouldBe "FUTSAL"
            }
        }

        When("[U-03] 결과가 없으면") {
            every { listFacilitiesUseCase.execute(any()) } returns
                PageImpl(emptyList(), PageRequest.of(0, 20), 0)

            val result = mcpFacilityTools.getFacilities(gu = "존재안함구", type = null, page = 0, size = 20)

            Then("[U-03] OK 상태와 빈 목록이 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                result.data shouldNotBe null
                val data = requireNotNull(result.data)
                data.size shouldBe 0
            }
        }
    }
})
