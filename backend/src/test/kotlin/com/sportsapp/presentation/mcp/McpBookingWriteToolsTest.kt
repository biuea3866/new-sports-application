package com.sportsapp.presentation.mcp

import com.sportsapp.application.booking.BookingResponse
import com.sportsapp.application.booking.CancelBookingUseCase
import com.sportsapp.domain.booking.BookingStatus
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenAlreadyConsumedException
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenContext
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenExpiredException
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenGateway
import com.sportsapp.presentation.mcp.response.McpResponseStatus
import com.sportsapp.presentation.mcp.toolregistry.McpBookingWriteTools
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.ZonedDateTime

class McpBookingWriteToolsTest : BehaviorSpec({

    val cancelBookingUseCase = mockk<CancelBookingUseCase>()
    val confirmationTokenGateway = mockk<ConfirmationTokenGateway>()
    val mcpBookingWriteTools = McpBookingWriteTools(cancelBookingUseCase, confirmationTokenGateway)

    val bookingResponse = BookingResponse(
        id = 1L,
        slotId = 100L,
        userId = 42L,
        status = BookingStatus.CANCELLED,
        paymentId = null,
        paymentStatus = null,
        createdAt = ZonedDateTime.now(),
        updatedAt = ZonedDateTime.now(),
    )

    Given("[U-01] confirmationToken 없이 cancelBooking 1차 호출 시") {
        val issuedToken = "test-token-uuid"
        val tokenSlot = slot<ConfirmationTokenContext>()
        every { confirmationTokenGateway.issue(capture(tokenSlot)) } returns issuedToken

        When("cancelBooking 을 호출하면") {
            val result = mcpBookingWriteTools.cancelBooking(
                bookingId = 1L,
                userId = 42L,
                reason = "테스트 취소",
                confirmationToken = null,
            )

            Then("[U-01] CONFIRM_REQUIRED 상태와 confirmationToken 이 반환된다") {
                result.status shouldBe McpResponseStatus.CONFIRM_REQUIRED
                @Suppress("UNCHECKED_CAST")
                val data = requireNotNull(result.data) as Map<String, Any>
                data["confirmationToken"] shouldBe issuedToken
                verify(exactly = 0) { cancelBookingUseCase.execute(any()) }
            }
        }
    }

    Given("[U-02] 유효한 confirmationToken 으로 cancelBooking 2차 호출 시") {
        val token = "valid-token"
        val storedContext = ConfirmationTokenContext(
            toolName = "cancelBooking",
            userId = 42L,
            paramsHash = "expected-hash",
        )
        every { confirmationTokenGateway.consume(token) } returns storedContext
        every { cancelBookingUseCase.execute(any()) } returns bookingResponse

        When("cancelBooking 을 호출하면") {
            val result = mcpBookingWriteTools.cancelBooking(
                bookingId = 1L,
                userId = 42L,
                reason = "테스트 취소",
                confirmationToken = token,
            )

            Then("[U-02] OK 상태와 취소된 BookingResponse 가 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                val data = requireNotNull(result.data) as BookingResponse
                data.status shouldBe BookingStatus.CANCELLED
                verify(exactly = 1) { cancelBookingUseCase.execute(any()) }
            }
        }
    }

    Given("[U-03] 만료된 confirmationToken 으로 cancelBooking 호출 시") {
        val expiredToken = "expired-token"
        every { confirmationTokenGateway.consume(expiredToken) } throws ConfirmationTokenExpiredException(expiredToken)

        When("cancelBooking 을 호출하면") {
            Then("[U-03] ConfirmationTokenExpiredException 이 전파된다") {
                shouldThrow<ConfirmationTokenExpiredException> {
                    mcpBookingWriteTools.cancelBooking(
                        bookingId = 1L,
                        userId = 42L,
                        reason = null,
                        confirmationToken = expiredToken,
                    )
                }
            }
        }
    }

    Given("[U-04] 이미 소진된 confirmationToken 으로 cancelBooking 호출 시") {
        val consumedToken = "consumed-token"
        every { confirmationTokenGateway.consume(consumedToken) } throws ConfirmationTokenAlreadyConsumedException(consumedToken)

        When("cancelBooking 을 호출하면") {
            Then("[U-04] ConfirmationTokenAlreadyConsumedException 이 전파된다") {
                shouldThrow<ConfirmationTokenAlreadyConsumedException> {
                    mcpBookingWriteTools.cancelBooking(
                        bookingId = 1L,
                        userId = 42L,
                        reason = null,
                        confirmationToken = consumedToken,
                    )
                }
            }
        }
    }

    Given("[U-05] 1차 호출 시 ConfirmationTokenContext 에 toolName cancelBooking 이 설정됨") {
        val tokenSlot = slot<ConfirmationTokenContext>()
        every { confirmationTokenGateway.issue(capture(tokenSlot)) } returns "any-token"

        When("cancelBooking 을 confirmationToken 없이 호출하면") {
            mcpBookingWriteTools.cancelBooking(
                bookingId = 99L,
                userId = 7L,
                reason = null,
                confirmationToken = null,
            )

            Then("[U-05] 발급 context 의 toolName 이 cancelBooking 이고 userId 가 전달된다") {
                tokenSlot.captured.toolName shouldBe "cancelBooking"
                tokenSlot.captured.userId shouldBe 7L
                tokenSlot.captured.paramsHash shouldNotBe null
            }
        }
    }
})
