package com.sportsapp.infrastructure.mcp.gateway

import com.sportsapp.domain.user.service.PermissionDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/**
 * mcp 도메인의 McpPermissionGateway ACL 구현체 테스트.
 * user 도메인의 PermissionDomainService(공급자)를 주입받아 위임만 수행함을 검증한다.
 */
class McpPermissionGatewayImplTest : BehaviorSpec({

    val permissionDomainService = mockk<PermissionDomainService>()
    val gateway = McpPermissionGatewayImpl(permissionDomainService)

    Given("존재하는 권한명으로 findPermissionIdBy를 호출하면") {
        every { permissionDomainService.findIdByName("mcp.facility.read.own") } returns 10L

        When("findPermissionIdBy를 호출하면") {
            val result = gateway.findPermissionIdBy("mcp.facility.read.own")

            Then("해당 권한 id가 반환된다") {
                result shouldBe 10L
                verify(exactly = 1) { permissionDomainService.findIdByName("mcp.facility.read.own") }
            }
        }
    }

    Given("존재하지 않는 권한명으로 findPermissionIdBy를 호출하면") {
        every { permissionDomainService.findIdByName("mcp.unknown.read.own") } returns null

        When("findPermissionIdBy를 호출하면") {
            val result = gateway.findPermissionIdBy("mcp.unknown.read.own")

            Then("null이 반환된다") {
                result.shouldBeNull()
            }
        }
    }
})
