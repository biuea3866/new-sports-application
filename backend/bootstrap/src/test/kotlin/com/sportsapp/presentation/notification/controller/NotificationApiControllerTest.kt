package com.sportsapp.presentation.notification.controller

import com.sportsapp.application.notification.dto.NotificationPageResult
import com.sportsapp.application.notification.dto.PushTokenResult
import com.sportsapp.application.notification.usecase.GetUnreadCountUseCase
import com.sportsapp.application.notification.usecase.ListMyNotificationsUseCase
import com.sportsapp.application.notification.usecase.MarkNotificationReadUseCase
import com.sportsapp.application.notification.usecase.RegisterPushTokenUseCase
import com.sportsapp.domain.notification.dto.NotificationResult
import com.sportsapp.domain.notification.entity.NotificationStatus
import com.sportsapp.domain.notification.entity.PushPlatform
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.presentation.exception.GlobalExceptionHandler
import com.sportsapp.presentation.support.fixedPrincipalResolver
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

private const val TEST_USER_ID = 100L

/** AUTH-04 — 알림 전 엔드포인트는 개인 데이터라 `@AuthenticationPrincipal UserPrincipal`로 식별한다. */
class NotificationApiControllerTest : BehaviorSpec({

    fun buildMockMvc(
        listMyNotificationsUseCase: ListMyNotificationsUseCase = mockk(),
        markNotificationReadUseCase: MarkNotificationReadUseCase = mockk(),
        getUnreadCountUseCase: GetUnreadCountUseCase = mockk(),
        registerPushTokenUseCase: RegisterPushTokenUseCase = mockk(),
    ) = MockMvcBuilders.standaloneSetup(
        NotificationApiController(listMyNotificationsUseCase, markNotificationReadUseCase, getUnreadCountUseCase, registerPushTokenUseCase),
    )
        .setControllerAdvice(GlobalExceptionHandler())
        .setCustomArgumentResolvers(fixedPrincipalResolver(TEST_USER_ID))
        .build()

    Given("본인 푸시 토큰 등록 요청") {
        val registerPushTokenUseCase = mockk<RegisterPushTokenUseCase>()
        every {
            registerPushTokenUseCase.execute(match { it.userId == TEST_USER_ID })
        } returns PushTokenResult(id = 1L, platform = "ANDROID")
        val mockMvc = buildMockMvc(registerPushTokenUseCase = registerPushTokenUseCase)

        When("POST /notifications/push-tokens 요청 시") {
            val result = mockMvc.perform(
                post("/notifications/push-tokens")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"token":"tok-1","platform":"${PushPlatform.ANDROID}"}"""),
            )

            Then("principal.id 로 등록되고 200을 반환한다") {
                result.andExpect(status().isOk)
                verify(exactly = 1) { registerPushTokenUseCase.execute(match { it.userId == TEST_USER_ID }) }
            }
        }
    }

    Given("본인 알림 목록 조회 요청") {
        val listMyNotificationsUseCase = mockk<ListMyNotificationsUseCase>()
        every {
            listMyNotificationsUseCase.execute(match { it.userId == TEST_USER_ID })
        } returns NotificationPageResult(content = emptyList(), totalElements = 0, totalPages = 0, page = 0, size = 20)
        val mockMvc = buildMockMvc(listMyNotificationsUseCase = listMyNotificationsUseCase)

        When("GET /notifications/me 요청 시") {
            val result = mockMvc.perform(get("/notifications/me"))

            Then("principal.id 기준으로 조회되고 200을 반환한다") {
                result.andExpect(status().isOk)
                verify(exactly = 1) { listMyNotificationsUseCase.execute(match { it.userId == TEST_USER_ID }) }
            }
        }
    }

    Given("본인 미읽음 카운트 조회 요청") {
        val getUnreadCountUseCase = mockk<GetUnreadCountUseCase>()
        every { getUnreadCountUseCase.execute(TEST_USER_ID) } returns 3L
        val mockMvc = buildMockMvc(getUnreadCountUseCase = getUnreadCountUseCase)

        When("GET /notifications/me/unread-count 요청 시") {
            val result = mockMvc.perform(get("/notifications/me/unread-count"))

            Then("200과 함께 unreadCount=3을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.unreadCount").value(3))
            }
        }
    }

    Given("본인 알림 읽음 처리 요청") {
        val markNotificationReadUseCase = mockk<MarkNotificationReadUseCase>()
        val now = ZonedDateTime.now()
        every {
            markNotificationReadUseCase.execute(match { it.notificationId == 5L && it.userId == TEST_USER_ID })
        } returns NotificationResult(
            id = 5L,
            userId = TEST_USER_ID,
            channel = NotificationChannel.PUSH,
            templateId = "tpl-1",
            status = NotificationStatus.SENT,
            sentAt = now,
            readAt = now,
            createdAt = now,
        )
        val mockMvc = buildMockMvc(markNotificationReadUseCase = markNotificationReadUseCase)

        When("PATCH /notifications/5/read 요청 시") {
            val result = mockMvc.perform(patch("/notifications/5/read"))

            Then("200과 함께 readAt이 채워진 상태를 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.readAt").exists())
            }
        }
    }
})
