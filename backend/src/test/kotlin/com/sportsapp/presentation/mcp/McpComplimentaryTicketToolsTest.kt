package com.sportsapp.presentation.mcp

import com.sportsapp.application.ticketing.dto.IssueComplimentaryTicketResponse
import com.sportsapp.application.ticketing.usecase.IssueComplimentaryTicketUseCase
import com.sportsapp.domain.mcp.McpAuthenticatedPrincipal
import com.sportsapp.domain.mcp.McpScope
import com.sportsapp.domain.mcp.confirm.ConfirmationParamsMismatchException
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenAlreadyConsumedException
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenContext
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenExpiredException
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenGateway
import com.sportsapp.domain.ticketing.entity.TicketStatus
import com.sportsapp.presentation.mcp.audit.McpAuditLogAsyncRecorder
import com.sportsapp.presentation.mcp.confirm.McpParamsHasher
import com.sportsapp.presentation.mcp.response.McpResponseStatus
import com.sportsapp.presentation.mcp.toolregistry.McpComplimentaryTicketTools
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

class McpComplimentaryTicketToolsTest : BehaviorSpec({

    val issueComplimentaryTicketUseCase = mockk<IssueComplimentaryTicketUseCase>()
    val confirmationTokenGateway = mockk<ConfirmationTokenGateway>()
    val mcpAuditLogAsyncRecorder = mockk<McpAuditLogAsyncRecorder>(relaxed = true)
    val mcpComplimentaryTicketTools = McpComplimentaryTicketTools(
        issueComplimentaryTicketUseCase,
        confirmationTokenGateway,
        mcpAuditLogAsyncRecorder,
    )

    val callerUserId = 77L
    val mockPrincipal = object : McpAuthenticatedPrincipal {
        override val tokenId: Long = 2L
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

    val ticketResponse = IssueComplimentaryTicketResponse(
        ticketId = 500L,
        seatId = 10L,
        status = TicketStatus.ISSUED,
        code = "abcdef1234567890",
    )

    Given("[U-01] confirmationToken 없이 issueComplimentaryTicket 1차 호출 시") {
        val issuedToken = "comp-ticket-confirm-token"
        val tokenSlot = slot<ConfirmationTokenContext>()
        every { confirmationTokenGateway.issue(capture(tokenSlot)) } returns issuedToken

        When("issueComplimentaryTicket 을 호출하면") {
            setSecurityContext()
            val result = mcpComplimentaryTicketTools.issueComplimentaryTicket(
                eventId = 5L,
                seatId = 10L,
                confirmationToken = null,
            )

            Then("[U-01] CONFIRM_REQUIRED 상태와 confirmationToken 이 반환된다") {
                result.status shouldBe McpResponseStatus.CONFIRM_REQUIRED
                @Suppress("UNCHECKED_CAST")
                val data = requireNotNull(result.data) as Map<String, Any>
                data["confirmationToken"] shouldBe issuedToken
                verify(exactly = 0) { issueComplimentaryTicketUseCase.execute(any()) }
            }
        }
    }

    Given("[U-02] 유효한 confirmationToken 과 일치하는 paramsHash 로 issueComplimentaryTicket 2차 호출 시") {
        val token = "valid-comp-ticket-token"
        val expectedHash = McpParamsHasher.hash("issueComplimentaryTicket", callerUserId, 5L, 10L)
        val storedContext = ConfirmationTokenContext(
            toolName = "issueComplimentaryTicket",
            userId = callerUserId,
            paramsHash = expectedHash,
        )
        every { confirmationTokenGateway.consume(token) } returns storedContext
        every { issueComplimentaryTicketUseCase.execute(any()) } returns ticketResponse

        When("issueComplimentaryTicket 을 호출하면") {
            setSecurityContext()
            val result = mcpComplimentaryTicketTools.issueComplimentaryTicket(
                eventId = 5L,
                seatId = 10L,
                confirmationToken = token,
            )

            Then("[U-02] OK 상태와 ISSUED 티켓 response 가 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                val data = requireNotNull(result.data) as IssueComplimentaryTicketResponse
                data.status shouldBe TicketStatus.ISSUED
                verify(exactly = 1) { issueComplimentaryTicketUseCase.execute(any()) }
            }
        }
    }

    Given("[U-03] 만료된 confirmationToken 으로 issueComplimentaryTicket 호출 시") {
        val expiredToken = "expired-comp-ticket-token"
        every { confirmationTokenGateway.consume(expiredToken) } throws ConfirmationTokenExpiredException(expiredToken)

        When("issueComplimentaryTicket 을 호출하면") {
            setSecurityContext()
            Then("[U-03] ConfirmationTokenExpiredException 이 전파된다") {
                shouldThrow<ConfirmationTokenExpiredException> {
                    mcpComplimentaryTicketTools.issueComplimentaryTicket(
                        eventId = 5L,
                        seatId = 10L,
                        confirmationToken = expiredToken,
                    )
                }
            }
        }
    }

    Given("[U-04] 이미 소진된 confirmationToken 으로 issueComplimentaryTicket 호출 시") {
        val consumedToken = "consumed-comp-ticket-token"
        every { confirmationTokenGateway.consume(consumedToken) } throws ConfirmationTokenAlreadyConsumedException(consumedToken)

        When("issueComplimentaryTicket 을 호출하면") {
            setSecurityContext()
            Then("[U-04] ConfirmationTokenAlreadyConsumedException 이 전파된다") {
                shouldThrow<ConfirmationTokenAlreadyConsumedException> {
                    mcpComplimentaryTicketTools.issueComplimentaryTicket(
                        eventId = 5L,
                        seatId = 10L,
                        confirmationToken = consumedToken,
                    )
                }
            }
        }
    }

    Given("[U-05] 1차 호출 시 ConfirmationTokenContext 에 toolName issueComplimentaryTicket 이 설정됨") {
        val tokenSlot = slot<ConfirmationTokenContext>()
        every { confirmationTokenGateway.issue(capture(tokenSlot)) } returns "any-token"

        When("issueComplimentaryTicket 을 confirmationToken 없이 호출하면") {
            setSecurityContext()
            mcpComplimentaryTicketTools.issueComplimentaryTicket(
                eventId = 99L,
                seatId = 5L,
                confirmationToken = null,
            )

            Then("[U-05] 발급 context 의 toolName 이 issueComplimentaryTicket 이고 userId 가 SecurityContext 에서 추출된다") {
                tokenSlot.captured.toolName shouldBe "issueComplimentaryTicket"
                tokenSlot.captured.userId shouldBe callerUserId
                tokenSlot.captured.paramsHash shouldNotBe null
            }
        }
    }

    Given("[U-15] 2차 호출 시 paramsHash 가 변조된 경우") {
        val token = "tampered-comp-token"
        val storedContext = ConfirmationTokenContext(
            toolName = "issueComplimentaryTicket",
            userId = callerUserId,
            paramsHash = McpParamsHasher.hash("issueComplimentaryTicket", callerUserId, 999L, 10L),
        )
        every { confirmationTokenGateway.consume(token) } returns storedContext

        When("실제로는 eventId=5 로 issueComplimentaryTicket 을 호출하면") {
            setSecurityContext()
            Then("[U-15] ConfirmationParamsMismatchException 이 발생한다") {
                shouldThrow<ConfirmationParamsMismatchException> {
                    mcpComplimentaryTicketTools.issueComplimentaryTicket(
                        eventId = 5L,
                        seatId = 10L,
                        confirmationToken = token,
                    )
                }
            }
        }
    }
})
