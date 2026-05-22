package com.sportsapp.presentation.mcp

import com.sportsapp.application.booking.CreateSlotUseCase
import com.sportsapp.application.booking.DeleteSlotUseCase
import com.sportsapp.application.booking.SlotResponse
import com.sportsapp.application.booking.UpdateSlotUseCase
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenAlreadyConsumedException
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenContext
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenExpiredException
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenGateway
import com.sportsapp.presentation.mcp.response.McpResponseStatus
import com.sportsapp.presentation.mcp.toolregistry.McpSlotWriteTools
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.ZonedDateTime

class McpSlotWriteToolsTest : BehaviorSpec({

    val createSlotUseCase = mockk<CreateSlotUseCase>()
    val updateSlotUseCase = mockk<UpdateSlotUseCase>()
    val deleteSlotUseCase = mockk<DeleteSlotUseCase>()
    val confirmationTokenGateway = mockk<ConfirmationTokenGateway>()
    val mcpSlotWriteTools = McpSlotWriteTools(
        createSlotUseCase,
        updateSlotUseCase,
        deleteSlotUseCase,
        confirmationTokenGateway,
    )

    val slotResponse = SlotResponse(
        id = 10L,
        facilityId = "FAC-01",
        date = ZonedDateTime.now(),
        timeRange = "09:00-10:00",
        capacity = 5,
        ownerId = 42L,
    )

    @Suppress("UNCHECKED_CAST")
    fun extractToken(data: Any?): String = (data as Map<String, Any>)["confirmationToken"] as String

    // ─── createSlot ───────────────────────────────────────────────────

    Given("[U-06] confirmationToken 없이 createSlot 1차 호출 시") {
        val issuedToken = "create-slot-token"
        val tokenSlot = slot<ConfirmationTokenContext>()
        every { confirmationTokenGateway.issue(capture(tokenSlot)) } returns issuedToken

        When("createSlot 을 호출하면") {
            val result = mcpSlotWriteTools.createSlot(
                ownerId = 42L,
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

    Given("[U-07] 유효한 confirmationToken 으로 createSlot 2차 호출 시") {
        val token = "valid-create-token"
        val storedContext = ConfirmationTokenContext(
            toolName = "createSlot",
            userId = 42L,
            paramsHash = "any-hash",
        )
        every { confirmationTokenGateway.consume(token) } returns storedContext
        every { createSlotUseCase.execute(any()) } returns slotResponse

        When("createSlot 을 호출하면") {
            val result = mcpSlotWriteTools.createSlot(
                ownerId = 42L,
                facilityId = "FAC-01",
                date = "2026-06-01T09:00:00+09:00",
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
            mcpSlotWriteTools.createSlot(
                ownerId = 10L,
                facilityId = "FAC-02",
                date = "2026-06-01T09:00:00+09:00",
                timeRange = "10:00-11:00",
                capacity = 3,
                confirmationToken = null,
            )

            Then("[U-08] toolName 이 createSlot 이다") {
                tokenSlot.captured.toolName shouldBe "createSlot"
                tokenSlot.captured.userId shouldBe 10L
                tokenSlot.captured.paramsHash shouldNotBe null
            }
        }
    }

    // ─── updateSlot ───────────────────────────────────────────────────

    Given("[U-09] confirmationToken 없이 updateSlot 1차 호출 시") {
        val issuedToken = "update-slot-token"
        every { confirmationTokenGateway.issue(any()) } returns issuedToken

        When("updateSlot 을 호출하면") {
            val result = mcpSlotWriteTools.updateSlot(
                requesterId = 42L,
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

    Given("[U-10] 유효한 confirmationToken 으로 updateSlot 2차 호출 시") {
        val token = "valid-update-token"
        val storedContext = ConfirmationTokenContext(
            toolName = "updateSlot",
            userId = 42L,
            paramsHash = "any-hash",
        )
        every { confirmationTokenGateway.consume(token) } returns storedContext
        every { updateSlotUseCase.execute(any()) } returns slotResponse

        When("updateSlot 을 호출하면") {
            val result = mcpSlotWriteTools.updateSlot(
                requesterId = 42L,
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
            val result = mcpSlotWriteTools.deleteSlot(
                requesterId = 42L,
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

    Given("[U-12] 유효한 confirmationToken 으로 deleteSlot 2차 호출 시") {
        val token = "valid-delete-token"
        val storedContext = ConfirmationTokenContext(
            toolName = "deleteSlot",
            userId = 42L,
            paramsHash = "any-hash",
        )
        every { confirmationTokenGateway.consume(token) } returns storedContext
        every { deleteSlotUseCase.execute(any()) } returns Unit

        When("deleteSlot 을 호출하면") {
            val result = mcpSlotWriteTools.deleteSlot(
                requesterId = 42L,
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
            Then("[U-13] ConfirmationTokenExpiredException 이 전파된다") {
                shouldThrow<ConfirmationTokenExpiredException> {
                    mcpSlotWriteTools.deleteSlot(42L, 10L, expiredToken)
                }
            }
        }
    }

    Given("[U-14] 이미 소진된 confirmationToken 으로 updateSlot 호출 시") {
        val consumedToken = "consumed-token"
        every { confirmationTokenGateway.consume(consumedToken) } throws ConfirmationTokenAlreadyConsumedException(consumedToken)

        When("updateSlot 을 호출하면") {
            Then("[U-14] ConfirmationTokenAlreadyConsumedException 이 전파된다") {
                shouldThrow<ConfirmationTokenAlreadyConsumedException> {
                    mcpSlotWriteTools.updateSlot(42L, 10L, null, null, consumedToken)
                }
            }
        }
    }
})
