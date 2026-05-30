package com.sportsapp.presentation.mcp

import com.sportsapp.application.booking.CreateSlotUseCase
import com.sportsapp.application.booking.DeleteSlotUseCase
import com.sportsapp.application.booking.SlotResponse
import com.sportsapp.application.booking.UpdateSlotUseCase
import com.sportsapp.domain.mcp.McpAuthenticatedPrincipal
import com.sportsapp.domain.mcp.McpScope
import com.sportsapp.domain.mcp.confirm.ConfirmationParamsMismatchException
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenAlreadyConsumedException
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenContext
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenExpiredException
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenGateway
import com.sportsapp.presentation.mcp.audit.McpAuditLogAsyncRecorder
import com.sportsapp.presentation.mcp.confirm.McpParamsHasher
import com.sportsapp.presentation.mcp.response.McpResponseStatus
import com.sportsapp.presentation.mcp.toolregistry.McpSlotWriteTools
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.ZonedDateTime

class McpSlotWriteToolsTest : BehaviorSpec({

    val createSlotUseCase = mockk<CreateSlotUseCase>()
    val updateSlotUseCase = mockk<UpdateSlotUseCase>()
    val deleteSlotUseCase = mockk<DeleteSlotUseCase>()
    val confirmationTokenGateway = mockk<ConfirmationTokenGateway>()
    val mcpAuditLogAsyncRecorder = mockk<McpAuditLogAsyncRecorder>(relaxed = true)
    val mcpSlotWriteTools = McpSlotWriteTools(
        createSlotUseCase,
        updateSlotUseCase,
        deleteSlotUseCase,
        confirmationTokenGateway,
        mcpAuditLogAsyncRecorder,
    )

    val callerUserId = 42L
    val mockPrincipal = object : McpAuthenticatedPrincipal {
        override val tokenId: Long = 1L
        override val userId: Long = callerUserId
        override val grantedScopes: Set<McpScope> = emptySet()
    }

    fun setSecurityContext() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(mockPrincipal, null, emptyList())
    }

    afterEach {
        SecurityContextHolder.clearContext()
        clearMocks(mcpAuditLogAsyncRecorder)
    }

    val slotResponse = SlotResponse(
        id = 10L,
        facilityId = "FAC-01",
        date = ZonedDateTime.now(),
        timeRange = "09:00-10:00",
        capacity = 5,
        ownerId = callerUserId,
    )

    @Suppress("UNCHECKED_CAST")
    fun extractToken(data: Any?): String = (data as Map<String, Any>)["confirmationToken"] as String

    // ─── createSlot ───────────────────────────────────────────────────

    Given("[U-06] confirmationToken 없이 createSlot 1차 호출 시") {
        val issuedToken = "create-slot-token"
        val tokenSlot = slot<ConfirmationTokenContext>()
        every { confirmationTokenGateway.issue(capture(tokenSlot)) } returns issuedToken

        When("createSlot 을 호출하면") {
            setSecurityContext()
            val result = mcpSlotWriteTools.createSlot(
                facilityId = "FAC-01",
                date = "2026-06-01T09:00:00+09:00",
                timeRange = "09:00-10:00",
                capacity = 5,
                confirmationToken = null,
            )

            Then("[U-06] CONFIRM_REQUIRED 상태와 confirmationToken 이 반환된다") {
                result.status shouldBe McpResponseStatus.CONFIRM_REQUIRED
                extractToken(result.data) shouldBe issuedToken
                verify(exactly = 0) { createSlotUseCase.execute(any()) }
            }
        }
    }

    Given("[U-07] 유효한 confirmationToken 과 일치하는 paramsHash 로 createSlot 2차 호출 시") {
        val token = "valid-create-token"
        val dateStr = "2026-06-01T09:00:00+09:00"
        val expectedHash = McpParamsHasher.hash("createSlot", callerUserId, "FAC-01", dateStr, "09:00-10:00", 5)
        val storedContext = ConfirmationTokenContext(
            toolName = "createSlot",
            userId = callerUserId,
            paramsHash = expectedHash,
        )
        every { confirmationTokenGateway.consume(token) } returns storedContext
        every { createSlotUseCase.execute(any()) } returns slotResponse

        When("createSlot 을 호출하면") {
            setSecurityContext()
            val result = mcpSlotWriteTools.createSlot(
                facilityId = "FAC-01",
                date = dateStr,
                timeRange = "09:00-10:00",
                capacity = 5,
                confirmationToken = token,
            )

            Then("[U-07] OK 상태와 SlotResponse 가 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                val data = requireNotNull(result.data) as SlotResponse
                data.id shouldBe 10L
                verify(exactly = 1) { createSlotUseCase.execute(any()) }
            }
        }
    }

    Given("[U-08] 1차 호출 시 createSlot ConfirmationTokenContext 에 toolName 이 설정됨") {
        val tokenSlot = slot<ConfirmationTokenContext>()
        every { confirmationTokenGateway.issue(capture(tokenSlot)) } returns "any-token"

        When("createSlot 을 confirmationToken 없이 호출하면") {
            setSecurityContext()
            mcpSlotWriteTools.createSlot(
                facilityId = "FAC-02",
                date = "2026-06-01T09:00:00+09:00",
                timeRange = "10:00-11:00",
                capacity = 3,
                confirmationToken = null,
            )

            Then("[U-08] toolName 이 createSlot 이고 userId 가 SecurityContext 에서 추출된다") {
                tokenSlot.captured.toolName shouldBe "createSlot"
                tokenSlot.captured.userId shouldBe callerUserId
                tokenSlot.captured.paramsHash shouldNotBe null
            }
        }
    }

    // ─── updateSlot ───────────────────────────────────────────────────

    Given("[U-09] confirmationToken 없이 updateSlot 1차 호출 시") {
        val issuedToken = "update-slot-token"
        every { confirmationTokenGateway.issue(any()) } returns issuedToken

        When("updateSlot 을 호출하면") {
            setSecurityContext()
            val result = mcpSlotWriteTools.updateSlot(
                slotId = 10L,
                timeRange = "10:00-11:00",
                capacity = 8,
                confirmationToken = null,
            )

            Then("[U-09] CONFIRM_REQUIRED 상태와 confirmationToken 이 반환된다") {
                result.status shouldBe McpResponseStatus.CONFIRM_REQUIRED
                extractToken(result.data) shouldBe issuedToken
                verify(exactly = 0) { updateSlotUseCase.execute(any()) }
            }
        }
    }

    Given("[U-10] 유효한 confirmationToken 과 일치하는 paramsHash 로 updateSlot 2차 호출 시") {
        val token = "valid-update-token"
        val expectedHash = McpParamsHasher.hash("updateSlot", callerUserId, 10L, "10:00-11:00", 8)
        val storedContext = ConfirmationTokenContext(
            toolName = "updateSlot",
            userId = callerUserId,
            paramsHash = expectedHash,
        )
        every { confirmationTokenGateway.consume(token) } returns storedContext
        every { updateSlotUseCase.execute(any()) } returns slotResponse

        When("updateSlot 을 호출하면") {
            setSecurityContext()
            val result = mcpSlotWriteTools.updateSlot(
                slotId = 10L,
                timeRange = "10:00-11:00",
                capacity = 8,
                confirmationToken = token,
            )

            Then("[U-10] OK 상태와 업데이트된 SlotResponse 가 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                val data = requireNotNull(result.data) as SlotResponse
                data.id shouldBe 10L
                verify(exactly = 1) { updateSlotUseCase.execute(any()) }
            }
        }
    }

    // ─── deleteSlot ───────────────────────────────────────────────────

    Given("[U-11] confirmationToken 없이 deleteSlot 1차 호출 시") {
        val issuedToken = "delete-slot-token"
        every { confirmationTokenGateway.issue(any()) } returns issuedToken

        When("deleteSlot 을 호출하면") {
            setSecurityContext()
            val result = mcpSlotWriteTools.deleteSlot(
                slotId = 10L,
                confirmationToken = null,
            )

            Then("[U-11] CONFIRM_REQUIRED 상태와 confirmationToken 이 반환된다") {
                result.status shouldBe McpResponseStatus.CONFIRM_REQUIRED
                extractToken(result.data) shouldBe issuedToken
                verify(exactly = 0) { deleteSlotUseCase.execute(any()) }
            }
        }
    }

    Given("[U-12] 유효한 confirmationToken 과 일치하는 paramsHash 로 deleteSlot 2차 호출 시") {
        val token = "valid-delete-token"
        val expectedHash = McpParamsHasher.hash("deleteSlot", callerUserId, 10L)
        val storedContext = ConfirmationTokenContext(
            toolName = "deleteSlot",
            userId = callerUserId,
            paramsHash = expectedHash,
        )
        every { confirmationTokenGateway.consume(token) } returns storedContext
        every { deleteSlotUseCase.execute(any()) } returns Unit

        When("deleteSlot 을 호출하면") {
            setSecurityContext()
            val result = mcpSlotWriteTools.deleteSlot(
                slotId = 10L,
                confirmationToken = token,
            )

            Then("[U-12] OK 상태와 slotId 가 포함된 응답이 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                @Suppress("UNCHECKED_CAST")
                val data = requireNotNull(result.data) as Map<String, Any>
                data["deletedSlotId"] shouldBe 10L
                verify(exactly = 1) { deleteSlotUseCase.execute(any()) }
            }
        }
    }

    Given("[U-13] 만료된 confirmationToken 으로 deleteSlot 호출 시") {
        val expiredToken = "expired-token"
        every { confirmationTokenGateway.consume(expiredToken) } throws ConfirmationTokenExpiredException(expiredToken)

        When("deleteSlot 을 호출하면") {
            setSecurityContext()
            Then("[U-13] ConfirmationTokenExpiredException 이 전파된다") {
                shouldThrow<ConfirmationTokenExpiredException> {
                    mcpSlotWriteTools.deleteSlot(10L, expiredToken)
                }
            }
        }
    }

    Given("[U-14] 이미 소진된 confirmationToken 으로 updateSlot 호출 시") {
        val consumedToken = "consumed-token"
        every { confirmationTokenGateway.consume(consumedToken) } throws ConfirmationTokenAlreadyConsumedException(consumedToken)

        When("updateSlot 을 호출하면") {
            setSecurityContext()
            Then("[U-14] ConfirmationTokenAlreadyConsumedException 이 전파된다") {
                shouldThrow<ConfirmationTokenAlreadyConsumedException> {
                    mcpSlotWriteTools.updateSlot(10L, null, null, consumedToken)
                }
            }
        }
    }

    Given("[U-audit-09] createSlot 1차 호출(confirmationToken=null) 시 audit recorder가 1회 호출된다") {
        every { confirmationTokenGateway.issue(any()) } returns "audit-create-token"

        When("createSlot 을 confirmationToken 없이 호출하면") {
            setSecurityContext()
            mcpSlotWriteTools.createSlot(
                facilityId = "FAC-01",
                date = "2026-06-01T09:00:00+09:00",
                timeRange = "09:00-10:00",
                capacity = 5,
                confirmationToken = null,
            )

            Then("[U-audit-09] mcpAuditLogAsyncRecorder.record가 정확히 1회 호출된다") {
                verify(exactly = 1) {
                    mcpAuditLogAsyncRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any())
                }
            }
        }
    }

    Given("[U-audit-10] deleteSlot 2차 호출(실행) 시 audit recorder가 1회 호출된다") {
        val token = "valid-delete-token-for-audit"
        val expectedHash = McpParamsHasher.hash("deleteSlot", callerUserId, 10L)
        val storedContext = ConfirmationTokenContext(
            toolName = "deleteSlot",
            userId = callerUserId,
            paramsHash = expectedHash,
        )
        every { confirmationTokenGateway.consume(token) } returns storedContext
        every { deleteSlotUseCase.execute(any()) } returns Unit

        When("deleteSlot 을 confirmationToken 포함해 호출하면") {
            setSecurityContext()
            mcpSlotWriteTools.deleteSlot(slotId = 10L, confirmationToken = token)

            Then("[U-audit-10] mcpAuditLogAsyncRecorder.record가 정확히 1회 호출된다") {
                verify(exactly = 1) {
                    mcpAuditLogAsyncRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any())
                }
            }
        }
    }

    Given("[U-16] 2차 호출 시 deleteSlot paramsHash 가 변조된 경우") {
        val token = "tampered-delete-token"
        val storedContext = ConfirmationTokenContext(
            toolName = "deleteSlot",
            userId = callerUserId,
            paramsHash = McpParamsHasher.hash("deleteSlot", callerUserId, 999L), // 다른 slotId 로 발급
        )
        every { confirmationTokenGateway.consume(token) } returns storedContext

        When("실제로는 slotId=10 으로 deleteSlot 을 호출하면") {
            setSecurityContext()
            Then("[U-16] ConfirmationParamsMismatchException 이 발생한다") {
                shouldThrow<ConfirmationParamsMismatchException> {
                    mcpSlotWriteTools.deleteSlot(10L, token)
                }
            }
        }
    }

    Given("[U-17] 2차 호출 시 createSlot paramsHash 가 변조된 경우") {
        val token = "tampered-create-token"
        val storedContext = ConfirmationTokenContext(
            toolName = "createSlot",
            userId = callerUserId,
            paramsHash = McpParamsHasher.hash("createSlot", callerUserId, "OTHER-FAC", "2026-06-01T09:00:00+09:00", "09:00-10:00", 5),
        )
        every { confirmationTokenGateway.consume(token) } returns storedContext

        When("실제로는 facilityId=FAC-01 로 createSlot 을 호출하면") {
            setSecurityContext()
            Then("[U-17] ConfirmationParamsMismatchException 이 발생한다") {
                shouldThrow<ConfirmationParamsMismatchException> {
                    mcpSlotWriteTools.createSlot(
                        facilityId = "FAC-01",
                        date = "2026-06-01T09:00:00+09:00",
                        timeRange = "09:00-10:00",
                        capacity = 5,
                        confirmationToken = token,
                    )
                }
            }
        }
    }
})
