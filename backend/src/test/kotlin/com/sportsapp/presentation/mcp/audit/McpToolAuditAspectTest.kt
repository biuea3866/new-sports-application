package com.sportsapp.presentation.mcp.audit

import com.sportsapp.domain.mcp.McpAuthenticatedPrincipal
import com.sportsapp.infrastructure.security.McpUserPrincipal
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.ZonedDateTime

class McpToolAuditAspectTest : BehaviorSpec({

    val mcpAuditLogAsyncRecorder = mockk<McpAuditLogAsyncRecorder>(relaxed = true)
    val aspect = McpToolAuditAspect(mcpAuditLogAsyncRecorder)

    beforeEach { SecurityContextHolder.clearContext() }
    afterEach { SecurityContextHolder.clearContext() }

    fun setSecurityContext(tokenId: Long, userId: Long) {
        val principal = McpUserPrincipal(tokenId = tokenId, userId = userId, grantedScopes = emptySet())
        val auth = UsernamePasswordAuthenticationToken(
            principal, null, listOf(SimpleGrantedAuthority("ROLE_MCP_TOKEN"))
        )
        SecurityContextHolder.getContext().authentication = auth
    }

    fun mockJoinPoint(toolName: String, paramNames: Array<String> = emptyArray(), args: Array<Any?> = emptyArray()): ProceedingJoinPoint {
        val joinPoint = mockk<ProceedingJoinPoint>()
        val signature = mockk<MethodSignature>()
        every { signature.name } returns toolName
        every { signature.parameterNames } returns paramNames
        every { joinPoint.signature } returns signature
        every { joinPoint.args } returns args
        every { joinPoint.proceed() } returns "result"
        return joinPoint
    }

    Given("[U-03] 정상 tool 호출 시 McpUserPrincipal이 SecurityContext에 있으면") {
        setSecurityContext(tokenId = 5L, userId = 100L)
        val joinPoint = mockJoinPoint(
            toolName = "getBookings",
            paramNames = arrayOf("userId", "status", "page", "size"),
            args = arrayOf(100L, "PENDING", 0, 10),
        )

        When("aroundToolInvocation을 호출하면") {
            val result = aspect.aroundToolInvocation(joinPoint)

            Then("[U-03] 결과가 그대로 반환되고 recorder에 tokenId/userId/toolName/statusCode=200이 전달된다") {
                result shouldBe "result"
                verify(exactly = 1) {
                    mcpAuditLogAsyncRecorder.record(
                        tokenId = 5L,
                        userId = 100L,
                        toolName = "getBookings",
                        namedParams = any(),
                        statusCode = 200,
                        latencyMs = any(),
                        calledAt = any(),
                    )
                }
            }
        }
    }

    Given("[U-04] tool 호출 중 RuntimeException이 발생하면") {
        setSecurityContext(tokenId = 7L, userId = 150L)
        val joinPoint = mockk<ProceedingJoinPoint>()
        val signature = mockk<MethodSignature>()
        every { signature.name } returns "getFacilities"
        every { signature.parameterNames } returns emptyArray()
        every { joinPoint.signature } returns signature
        every { joinPoint.args } returns emptyArray()
        every { joinPoint.proceed() } throws RuntimeException("tool failure")

        When("aroundToolInvocation을 호출하면") {
            runCatching { aspect.aroundToolInvocation(joinPoint) }

            Then("[U-04] statusCode=500 으로 recorder가 호출된다") {
                verify(exactly = 1) {
                    mcpAuditLogAsyncRecorder.record(
                        tokenId = 7L,
                        userId = 150L,
                        toolName = "getFacilities",
                        namedParams = any(),
                        statusCode = 500,
                        latencyMs = any(),
                        calledAt = any(),
                    )
                }
            }
        }
    }

    Given("[U-05] SecurityContext에 McpUserPrincipal이 없으면") {
        val joinPoint = mockJoinPoint("getBookings", emptyArray(), emptyArray())

        When("aroundToolInvocation을 호출하면") {
            aspect.aroundToolInvocation(joinPoint)

            Then("[U-05] tokenId=null, userId=0으로 recorder가 호출된다") {
                verify(exactly = 1) {
                    mcpAuditLogAsyncRecorder.record(
                        tokenId = null,
                        userId = 0L,
                        toolName = "getBookings",
                        namedParams = any(),
                        statusCode = 200,
                        latencyMs = any(),
                        calledAt = any(),
                    )
                }
            }
        }
    }
})
