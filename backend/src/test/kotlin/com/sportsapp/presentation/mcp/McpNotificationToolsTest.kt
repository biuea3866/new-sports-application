package com.sportsapp.presentation.mcp

import com.sportsapp.application.notification.ListMyNotificationsCommand
import com.sportsapp.application.notification.ListMyNotificationsUseCase
import com.sportsapp.application.notification.NotificationPageResponse
import com.sportsapp.application.notification.NotificationResponse
import com.sportsapp.domain.notification.NotificationChannel
import com.sportsapp.domain.notification.NotificationStatus
import com.sportsapp.presentation.mcp.response.McpResponseStatus
import com.sportsapp.presentation.mcp.toolregistry.McpNotificationTools
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.ZonedDateTime

class McpNotificationToolsTest : BehaviorSpec({

    val listMyNotificationsUseCase = mockk<ListMyNotificationsUseCase>()
    val mcpNotificationTools = McpNotificationTools(listMyNotificationsUseCase)

    Given("getNotifications tool") {
        val notificationResponse = NotificationResponse(
            id = 1L,
            userId = 42L,
            channel = NotificationChannel.IN_APP,
            templateId = "booking.confirmed",
            status = NotificationStatus.SENT,
            sentAt = ZonedDateTime.now(),
            readAt = null,
            createdAt = ZonedDateTime.now(),
        )
        val pageResponse = NotificationPageResponse(
            content = listOf(notificationResponse),
            totalElements = 1L,
            totalPages = 1,
            page = 0,
            size = 20,
        )

        When("[U-13] userId와 onlyUnread=false로 getNotifications를 호출하면") {
            val commandSlot = slot<ListMyNotificationsCommand>()
            every { listMyNotificationsUseCase.execute(capture(commandSlot)) } returns pageResponse

            val result = mcpNotificationTools.getNotifications(
                userId = 42L,
                onlyUnread = false,
                page = 0,
                size = 20,
            )

            Then("[U-13] OK 상태와 알림 목록이 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                result.data shouldNotBe null
                val data = requireNotNull(result.data)
                data.size shouldBe 1
                data[0].id shouldBe 1L
                data[0].userId shouldBe 42L
            }
        }

        When("[U-14] onlyUnread=true로 getNotifications를 호출하면") {
            val commandSlot = slot<ListMyNotificationsCommand>()
            every { listMyNotificationsUseCase.execute(capture(commandSlot)) } returns pageResponse

            mcpNotificationTools.getNotifications(userId = 42L, onlyUnread = true, page = 0, size = 20)

            Then("[U-14] ListMyNotificationsCommand에 onlyUnread=true가 전달된다") {
                commandSlot.captured.userId shouldBe 42L
                commandSlot.captured.onlyUnread shouldBe true
            }
        }

        When("[U-15] 결과가 없으면") {
            every { listMyNotificationsUseCase.execute(any()) } returns NotificationPageResponse(
                content = emptyList(),
                totalElements = 0L,
                totalPages = 0,
                page = 0,
                size = 20,
            )

            val result = mcpNotificationTools.getNotifications(
                userId = 99L,
                onlyUnread = false,
                page = 0,
                size = 20,
            )

            Then("[U-15] OK 상태와 빈 목록이 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                val data = requireNotNull(result.data)
                data.size shouldBe 0
            }
        }

        When("[U-16] pagination 정보가 McpResponse에 포함되는지 확인") {
            val commandSlot = slot<ListMyNotificationsCommand>()
            every { listMyNotificationsUseCase.execute(capture(commandSlot)) } returns pageResponse

            val result = mcpNotificationTools.getNotifications(
                userId = 42L,
                onlyUnread = false,
                page = 0,
                size = 20,
            )

            Then("[U-16] pagination 객체가 반환된다") {
                result.pagination shouldNotBe null
                result.pagination?.total shouldBe 1L
                result.pagination?.page shouldBe 0
            }
        }
    }
})
