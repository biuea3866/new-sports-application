package com.sportsapp.presentation.mcp

import com.sportsapp.application.booking.BookingResponse
import com.sportsapp.application.booking.RefundBookingUseCase
import com.sportsapp.domain.booking.BookingStatus
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
import com.sportsapp.presentation.mcp.toolregistry.McpRefundTools
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

class McpRefundToolsTest : BehaviorSpec({

    val refundBookingUseCase = mockk<RefundBookingUseCase>()
    val confirmationTokenGateway = mockk<ConfirmationTokenGateway>()
    val mcpAuditLogAsyncRecorder = mockk<McpAuditLogAsyncRecorder>(relaxed = true)
    val mcpRefundTools = McpRefundTools(refundBookingUseCase, confirmationTokenGateway, mcpAuditLogAsyncRecorder)

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

    val bookingResponse = BookingResponse(
        id = 1L,
        slotId = 100L,
        userId = callerUserId,
        status = BookingStatus.REFUNDED,
        paymentId = 10L,
        paymentStatus = null,
        createdAt = ZonedDateTime.now(),
        updatedAt = ZonedDateTime.now(),
    )

    Given("[U-01] confirmationToken 없이 refundBooking 1차 호출 시") {
        val issuedToken = "refund-confirm-token"
        val tokenSlot = slot<ConfirmationTokenContext>()
        every { confirmationTokenGateway.issue(capture(tokenSlot)) } returns issuedToken

        When("refundBooking 을 호출하면") {
            setSecurityContext()
            val result = mcpRefundTools.refundBooking(
                bookingId = 1L,
                refundAmount = "50000",
                reason = "고객 요청",
                confirmationToken = null,
            )

            Then("[U-01] CONFIRM_REQUIRED 상태와 confirmationToken 이 반환된다") {
                result.status shouldBe McpResponseStatus.CONFIRM_REQUIRED
                @Suppress("UNCHECKED_CAST")
                val data = requireNotNull(result.data) as Map<String, Any>
                data["confirmationToken"] shouldBe issuedToken
                verify(exactly = 0) { refundBookingUseCase.execute(any()) }
            }
        }
    }

    Given("[U-02] 유효한 confirmationToken 과 일치하는 paramsHash 로 refundBooking 2차 호출 시") {
        val token = "valid-refund-token"
        val expectedHash = McpParamsHasher.hash("refundBooking", 1L, callerUserId, "50000")
        val storedContext = ConfirmationTokenContext(
            toolName = "refundBooking",
            userId = callerUserId,
            paramsHash = expectedHash,
        )
        every { confirmationTokenGateway.consume(token) } returns storedContext
        every { refundBookingUseCase.execute(any()) } returns bookingResponse

        When("refundBooking 을 호출하면") {
            setSecurityContext()
            val result = mcpRefundTools.refundBooking(
                bookingId = 1L,
                refundAmount = "50000",
                reason = "고객 요청",
                confirmationToken = token,
            )

            Then("[U-02] OK 상태와 REFUNDED BookingResponse 가 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                val data = requireNotNull(result.data) as BookingResponse
                data.status shouldBe BookingStatus.REFUNDED
                verify(exactly = 1) { refundBookingUseCase.execute(any()) }
            }
        }
    }

    Given("[U-03] 만료된 confirmationToken 으로 refundBooking 호출 시") {
        val expiredToken = "expired-refund-token"
        every { confirmationTokenGateway.consume(expiredToken) } throws ConfirmationTokenExpiredException(expiredToken)

        When("refundBooking 을 호출하면") {
            setSecurityContext()
            Then("[U-03] ConfirmationTokenExpiredException 이 전파된다") {
                shouldThrow<ConfirmationTokenExpiredException> {
                    mcpRefundTools.refundBooking(
                        bookingId = 1L,
                        refundAmount = "50000",
                        reason = "고객 요청",
                        confirmationToken = expiredToken,
                    )
                }
            }
        }
    }

    Given("[U-04] 이미 소진된 confirmationToken 으로 refundBooking 호출 시") {
        val consumedToken = "consumed-refund-token"
        every { confirmationTokenGateway.consume(consumedToken) } throws ConfirmationTokenAlreadyConsumedException(consumedToken)

        When("refundBooking 을 호출하면") {
            setSecurityContext()
            Then("[U-04] ConfirmationTokenAlreadyConsumedException 이 전파된다") {
                shouldThrow<ConfirmationTokenAlreadyConsumedException> {
                    mcpRefundTools.refundBooking(
                        bookingId = 1L,
                        refundAmount = "50000",
                        reason = "고객 요청",
                        confirmationToken = consumedToken,
                    )
                }
            }
        }
    }

    Given("[U-05] 1차 호출 시 ConfirmationTokenContext 에 toolName refundBooking 이 설정됨") {
        val tokenSlot = slot<ConfirmationTokenContext>()
        every { confirmationTokenGateway.issue(capture(tokenSlot)) } returns "any-token"

        When("refundBooking 을 confirmationToken 없이 호출하면") {
            setSecurityContext()
            mcpRefundTools.refundBooking(
                bookingId = 99L,
                refundAmount = "10000",
                reason = "테스트",
                confirmationToken = null,
            )

            Then("[U-05] 발급 context 의 toolName 이 refundBooking 이고 userId 가 SecurityContext 에서 추출된다") {
                tokenSlot.captured.toolName shouldBe "refundBooking"
                tokenSlot.captured.userId shouldBe callerUserId
                tokenSlot.captured.paramsHash shouldNotBe null
            }
        }
    }

    Given("[U-15] 2차 호출 시 paramsHash 가 변조된 경우") {
        val token = "tampered-refund-token"
        val storedContext = ConfirmationTokenContext(
            toolName = "refundBooking",
            userId = callerUserId,
            paramsHash = McpParamsHasher.hash("refundBooking", 999L, callerUserId, "50000"),
        )
        every { confirmationTokenGateway.consume(token) } returns storedContext

        When("실제로는 bookingId=1 로 refundBooking 을 호출하면") {
            setSecurityContext()
            Then("[U-15] ConfirmationParamsMismatchException 이 발생한다") {
                shouldThrow<ConfirmationParamsMismatchException> {
                    mcpRefundTools.refundBooking(
                        bookingId = 1L,
                        refundAmount = "50000",
                        reason = "고객 요청",
                        confirmationToken = token,
                    )
                }
            }
        }
    }

    Given("[U-audit] refundBooking 호출 시 audit recorder 가 1회 호출된다") {
        val issuedToken = "audit-refund-token"
        every { confirmationTokenGateway.issue(any()) } returns issuedToken

        When("refundBooking 을 confirmationToken 없이 호출하면") {
            setSecurityContext()
            mcpRefundTools.refundBooking(bookingId = 1L, refundAmount = "50000", reason = "테스트", confirmationToken = null)

            Then("[U-audit] mcpAuditLogAsyncRecorder.record 가 정확히 1회 호출된다") {
                verify(exactly = 1) {
                    mcpAuditLogAsyncRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any())
                }
            }
        }
    }
})
