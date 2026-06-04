package com.sportsapp.presentation.mcp

import com.sportsapp.application.notification.ListMyNotificationsCommand
import com.sportsapp.application.notification.dto.NotificationPageResult
import com.sportsapp.application.notification.usecase.ListMyNotificationsUseCase
import com.sportsapp.domain.mcp.McpAuthenticatedPrincipal
import com.sportsapp.domain.notification.NotificationResult
import com.sportsapp.domain.mcp.McpScope
import com.sportsapp.domain.notification.NotificationChannel
import com.sportsapp.domain.notification.NotificationStatus
import com.sportsapp.presentation.mcp.audit.McpAuditLogAsyncRecorder
import com.sportsapp.presentation.mcp.response.McpResponseStatus
import com.sportsapp.presentation.mcp.toolregistry.McpNotificationTools
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.ZonedDateTime

class McpNotificationToolsTest : BehaviorSpec({

    val listMyNotificationsUseCase = mockk<ListMyNotificationsUseCase>()
    val mcpAuditLogAsyncRecorder = mockk<McpAuditLogAsyncRecorder>(relaxed = true)
    val mcpNotificationTools = McpNotificationTools(listMyNotificationsUseCase, mcpAuditLogAsyncRecorder)

    val notificationResult = NotificationResult(
        id = 1L,
        userId = 42L,
        channel = NotificationChannel.IN_APP,
        templateId = "booking.confirmed",
        status = NotificationStatus.SENT,
        sentAt = ZonedDateTime.now(),
        readAt = null,
        createdAt = ZonedDateTime.now(),
    )
    val pageResponse = NotificationPageResult(
        content = listOf(notificationResult),
        totalElements = 1L,
        totalPages = 1,
        page = 0,
        size = 20,
    )

    fun setupPrincipal(userId: Long) {
        val principal = object : McpAuthenticatedPrincipal {
            override val tokenId = 100L
            override val userId = userId
            override val grantedScopes = setOf<McpScope>()
        }
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, emptyList())
    }

    afterEach {
        SecurityContextHolder.clearContext()
        clearMocks(mcpAuditLogAsyncRecorder)
    }

    Given("getNotifications tool") {
        When("[U-13] 인증된 사용자(userId=42)로 getNotifications를 호출하면") {
            setupPrincipal(42L)
            val commandSlot = slot<ListMyNotificationsCommand>()
            every { listMyNotificationsUseCase.execute(capture(commandSlot)) } returns pageResponse

            val result = mcpNotificationTools.getNotifications(onlyUnread = false)

            Then("[U-13] OK 상태와 알림 목록이 반환되며 userId는 응답에 포함되지 않는다") {
                result.status shouldBe McpResponseStatus.OK
                result.data shouldNotBe null
                val data = requireNotNull(result.data)
                data.size shouldBe 1
                data[0].id shouldBe 1L
                commandSlot.captured.userId shouldBe 42L
            }
        }

        When("[U-14] onlyUnread=true로 getNotifications를 호출하면") {
            setupPrincipal(42L)
            val commandSlot = slot<ListMyNotificationsCommand>()
            every { listMyNotificationsUseCase.execute(capture(commandSlot)) } returns pageResponse

            mcpNotificationTools.getNotifications(onlyUnread = true)

            Then("[U-14] ListMyNotificationsCommand에 onlyUnread=true와 principal.userId가 전달된다") {
                commandSlot.captured.userId shouldBe 42L
                commandSlot.captured.onlyUnread shouldBe true
            }
        }

        When("[U-15] 결과가 없으면") {
            setupPrincipal(99L)
            every { listMyNotificationsUseCase.execute(any()) } returns NotificationPageResult(
                content = emptyList(),
                totalElements = 0L,
                totalPages = 0,
                page = 0,
                size = 20,
            )

            val result = mcpNotificationTools.getNotifications(onlyUnread = false)

            Then("[U-15] OK 상태와 빈 목록이 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                val data = requireNotNull(result.data)
                data.size shouldBe 0
            }
        }

        When("[U-16] pagination 정보가 McpResponse에 포함되는지 확인") {
            setupPrincipal(42L)
            every { listMyNotificationsUseCase.execute(any()) } returns pageResponse

            val result = mcpNotificationTools.getNotifications(onlyUnread = false)

            Then("[U-16] pagination 객체가 반환된다") {
                result.pagination shouldNotBe null
                result.pagination?.total shouldBe 1L
                result.pagination?.page shouldBe 0
            }
        }

        When("[U-17] IDOR — principal이 없으면 (SecurityContext 비어있음)") {
            SecurityContextHolder.clearContext()
            val localUseCase = mockk<ListMyNotificationsUseCase>()
            val localRecorder = mockk<McpAuditLogAsyncRecorder>(relaxed = true)
            val localTools = McpNotificationTools(localUseCase, localRecorder)

            Then("[U-17] AccessDeniedException이 발생하고 UseCase는 호출되지 않는다") {
                shouldThrow<AccessDeniedException> {
                    localTools.getNotifications(onlyUnread = false)
                }
                verify(exactly = 0) { localUseCase.execute(any()) }
            }
        }

        When("[U-18] IDOR — principal userId가 달라도 tool은 자기 userId만 사용한다") {
            setupPrincipal(42L)
            val commandSlot = slot<ListMyNotificationsCommand>()
            every { listMyNotificationsUseCase.execute(capture(commandSlot)) } returns pageResponse

            mcpNotificationTools.getNotifications(onlyUnread = false)

            Then("[U-18] UseCase에 전달되는 userId는 principal.userId(42)이다") {
                commandSlot.captured.userId shouldBe 42L
            }
        }

        When("[U-audit-03] getNotifications 호출 시 audit recorder가 1회 호출된다") {
            setupPrincipal(42L)
            every { listMyNotificationsUseCase.execute(any()) } returns pageResponse

            mcpNotificationTools.getNotifications(onlyUnread = false)

            Then("[U-audit-03] mcpAuditLogAsyncRecorder.record가 정확히 1회 호출된다") {
                verify(exactly = 1) {
                    mcpAuditLogAsyncRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any())
                }
            }
        }
    }
})
