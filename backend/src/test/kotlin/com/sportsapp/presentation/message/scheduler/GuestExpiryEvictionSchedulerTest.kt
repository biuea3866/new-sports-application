package com.sportsapp.presentation.message.scheduler

import com.sportsapp.application.message.usecase.ExpireGuestsUseCase
import com.sportsapp.application.notification.usecase.SendRawNotificationUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class GuestExpiryEvictionSchedulerTest : BehaviorSpec({

    Given("만료 방출 플래그가 활성화된 상태에서 배치가 정상 처리되는 경우") {
        val expireGuestsUseCase = mockk<ExpireGuestsUseCase>()
        val sendRawNotificationUseCase = mockk<SendRawNotificationUseCase>(relaxed = true)
        val scheduler = GuestExpiryScheduler(
            expireGuestsUseCase = expireGuestsUseCase,
            sendRawNotificationUseCase = sendRawNotificationUseCase,
            expiryEnabled = true,
            notifyUserId = 1L,
        )
        every { expireGuestsUseCase.execute() } returns 2

        When("evictExpiredGuests 를 호출하면") {
            scheduler.evictExpiredGuests()

            Then("ExpireGuestsUseCase 가 호출되고 실패 알림은 발송되지 않는다") {
                verify { expireGuestsUseCase.execute() }
                verify(exactly = 0) { sendRawNotificationUseCase.execute(any()) }
            }
        }
    }

    Given("배치가 예외로 실패하는 경우") {
        val expireGuestsUseCase = mockk<ExpireGuestsUseCase>()
        val sendRawNotificationUseCase = mockk<SendRawNotificationUseCase>(relaxed = true)
        val scheduler = GuestExpiryScheduler(
            expireGuestsUseCase = expireGuestsUseCase,
            sendRawNotificationUseCase = sendRawNotificationUseCase,
            expiryEnabled = true,
            notifyUserId = 1L,
        )
        every { expireGuestsUseCase.execute() } throws RuntimeException("db unavailable")

        When("evictExpiredGuests 를 호출하면") {
            scheduler.evictExpiredGuests()

            Then("NotificationChannelGateway(SendRawNotificationUseCase 경유) 알림이 발송된다") {
                verify { sendRawNotificationUseCase.execute(any()) }
            }
        }
    }

    Given("만료 방출 플래그가 비활성화된 상태") {
        val expireGuestsUseCase = mockk<ExpireGuestsUseCase>()
        val sendRawNotificationUseCase = mockk<SendRawNotificationUseCase>(relaxed = true)
        val scheduler = GuestExpiryScheduler(
            expireGuestsUseCase = expireGuestsUseCase,
            sendRawNotificationUseCase = sendRawNotificationUseCase,
            expiryEnabled = false,
            notifyUserId = 1L,
        )

        When("evictExpiredGuests 를 호출하면") {
            scheduler.evictExpiredGuests()

            Then("ExpireGuestsUseCase 는 호출되지 않는다 (롤백 플래그 OFF)") {
                verify(exactly = 0) { expireGuestsUseCase.execute() }
            }
        }
    }
})
