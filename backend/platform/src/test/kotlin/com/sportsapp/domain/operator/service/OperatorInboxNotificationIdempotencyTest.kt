package com.sportsapp.domain.operator.service

import com.sportsapp.domain.operator.entity.OperatorInboxNotification
import com.sportsapp.domain.operator.repository.OperatorInboxNotificationRepository
import com.sportsapp.domain.operator.vo.OperatorInboxNotificationType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/**
 * 운영 인박스 적재 멱등.
 *
 * 운영 인박스는 Kafka 도메인 이벤트 구독으로 채워진다. Kafka는 at-least-once라 같은 이벤트를
 * 두 번 받는 것이 정상 시나리오이며, 멱등 처리가 없으면 파트너 인박스에 같은 알림이 중복으로
 * 쌓이고 안읽음 배지까지 함께 부풀어 오른다.
 */
class OperatorInboxNotificationIdempotencyTest : BehaviorSpec({

    val repository = mockk<OperatorInboxNotificationRepository>()
    val service = OperatorInboxNotificationDomainService(repository)

    val recipientUserId = 69L
    val eventId = "evt-0001"

    Given("아직 적재되지 않은 이벤트") {
        every { repository.existsByRecipientUserIdAndEventId(recipientUserId, eventId) } returns false
        every { repository.save(any()) } answers { firstArg<OperatorInboxNotification>() }

        When("운영 알림을 적재하면") {
            val created = service.createOrSkip(
                eventId = eventId,
                recipientUserId = recipientUserId,
                type = OperatorInboxNotificationType.BOOKING_RECEIVED,
                title = "신규 예약 접수",
                body = "예약이 접수됐습니다.",
                link = "/portal/bookings",
            )

            Then("알림이 생성된다") {
                created.shouldNotBeNull()
            }

            Then("저장이 한 번 수행된다") {
                verify(exactly = 1) { repository.save(any()) }
            }
        }
    }

    Given("이미 같은 이벤트로 적재된 알림이 있을 때") {
        val duplicateRepository = mockk<OperatorInboxNotificationRepository>()
        val duplicateService = OperatorInboxNotificationDomainService(duplicateRepository)
        every {
            duplicateRepository.existsByRecipientUserIdAndEventId(recipientUserId, eventId)
        } returns true

        When("같은 이벤트를 다시 수신해 적재를 시도하면") {
            val created = duplicateService.createOrSkip(
                eventId = eventId,
                recipientUserId = recipientUserId,
                type = OperatorInboxNotificationType.BOOKING_RECEIVED,
                title = "신규 예약 접수",
                body = "예약이 접수됐습니다.",
                link = "/portal/bookings",
            )

            Then("중복 적재하지 않고 건너뛴다") {
                created.shouldBeNull()
            }

            Then("저장을 시도하지 않는다") {
                verify(exactly = 0) { duplicateRepository.save(any()) }
            }
        }
    }

    Given("같은 이벤트를 서로 다른 수신자에게 보낼 때") {
        val otherRecipientId = 70L
        val multiRecipientRepository = mockk<OperatorInboxNotificationRepository>()
        val multiRecipientService = OperatorInboxNotificationDomainService(multiRecipientRepository)
        every {
            multiRecipientRepository.existsByRecipientUserIdAndEventId(recipientUserId, eventId)
        } returns true
        every {
            multiRecipientRepository.existsByRecipientUserIdAndEventId(otherRecipientId, eventId)
        } returns false
        every { multiRecipientRepository.save(any()) } answers { firstArg<OperatorInboxNotification>() }

        When("아직 받지 않은 수신자에게 적재하면") {
            val created = multiRecipientService.createOrSkip(
                eventId = eventId,
                recipientUserId = otherRecipientId,
                type = OperatorInboxNotificationType.BOOKING_RECEIVED,
                title = "신규 예약 접수",
                body = "예약이 접수됐습니다.",
                link = "/portal/bookings",
            )

            // 멱등 범위는 (수신자, 이벤트)다 — 이벤트 단독으로 잡으면 한 명이 받은 순간
            // 나머지 수신자가 영영 못 받는다.
            Then("다른 수신자에게는 정상 적재된다") {
                created.shouldNotBeNull()
            }
        }
    }
})
