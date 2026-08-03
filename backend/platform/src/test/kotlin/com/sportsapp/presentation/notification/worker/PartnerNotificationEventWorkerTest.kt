package com.sportsapp.presentation.notification.worker

import com.sportsapp.application.notification.dto.EnqueueNotificationCommand
import com.sportsapp.application.notification.usecase.EnqueueNotificationUseCase
import com.sportsapp.domain.booking.event.BookingEvent
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.ticketing.event.TicketEvent
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

private const val BOOKER_USER_ID = 68L
private const val FACILITY_OWNER_USER_ID = 69L
private const val EVENT_OWNER_USER_ID = 69L

/**
 * 파트너(소유주) 대상 알림 발행.
 *
 * 기존 워커는 세 이벤트 모두 구매자(`recipientUserId`)만 대상으로 해 파트너는 어떤 알림도
 * 받지 못했다(알림 수신함 영구 0건). 구매자 알림은 그대로 두고 소유주 알림을 **추가**한다.
 */
class PartnerNotificationEventWorkerTest : BehaviorSpec({

    fun buildWorker(captured: MutableList<EnqueueNotificationCommand>): NotificationEventWorker {
        val useCase = mockk<EnqueueNotificationUseCase>()
        every { useCase.execute(any()) } answers { captured.add(firstArg()); Unit }
        return NotificationEventWorker(useCase)
    }

    Given("시설 소유주가 실린 예약 확정 이벤트") {
        val captured = mutableListOf<EnqueueNotificationCommand>()
        val worker = buildWorker(captured)
        val event = BookingEvent.Confirmed(
            bookingId = 1L,
            paymentId = 500L,
            recipientUserId = BOOKER_USER_ID,
            facilityOwnerUserId = FACILITY_OWNER_USER_ID,
        )

        When("consumeBooking 을 호출하면") {
            worker.consumeBooking(event)

            Then("구매자 알림은 그대로 발행된다") {
                captured.filter { it.recipientUserId == BOOKER_USER_ID }
                    .map { it.channel } shouldContainExactlyInAnyOrder
                    listOf(NotificationChannel.IN_APP, NotificationChannel.PUSH)
            }

            Then("시설 소유주에게도 알림이 발행된다") {
                captured.filter { it.recipientUserId == FACILITY_OWNER_USER_ID }
                    .map { it.channel } shouldContainExactlyInAnyOrder
                    listOf(NotificationChannel.IN_APP, NotificationChannel.PUSH)
            }

            Then("소유주 알림은 구매자와 다른 템플릿을 쓴다") {
                val ownerTemplates = captured.filter { it.recipientUserId == FACILITY_OWNER_USER_ID }
                    .map { it.templateId }.toSet()
                ownerTemplates shouldBe setOf("booking-received-owner")
            }

            // eventId가 겹치면 하류 enqueueOrSkip이 둘 중 하나를 중복으로 보고 건너뛴다.
            Then("구매자·소유주 알림의 멱등 키가 서로 겹치지 않는다") {
                captured.map { it.eventId }.toSet().size shouldBe captured.size
            }
        }
    }

    Given("소유주가 비어 있는 예약 확정 이벤트") {
        val captured = mutableListOf<EnqueueNotificationCommand>()
        val worker = buildWorker(captured)
        val event = BookingEvent.Confirmed(
            bookingId = 1L,
            paymentId = 500L,
            recipientUserId = BOOKER_USER_ID,
            facilityOwnerUserId = null,
        )

        When("consumeBooking 을 호출하면") {
            worker.consumeBooking(event)

            // 소유주를 못 찾은 경우까지 구매자 알림을 막으면 안 된다.
            Then("구매자 알림만 발행된다") {
                captured.size shouldBe 2
                captured.all { it.recipientUserId == BOOKER_USER_ID } shouldBe true
            }
        }
    }

    Given("구매자와 소유주가 동일인인 예약 확정 이벤트") {
        val captured = mutableListOf<EnqueueNotificationCommand>()
        val worker = buildWorker(captured)
        val event = BookingEvent.Confirmed(
            bookingId = 1L,
            paymentId = 500L,
            recipientUserId = FACILITY_OWNER_USER_ID,
            facilityOwnerUserId = FACILITY_OWNER_USER_ID,
        )

        When("consumeBooking 을 호출하면") {
            worker.consumeBooking(event)

            // 자기 시설을 자기가 예약한 경우 같은 사건으로 알림이 두 벌 쌓이면 소음이다.
            Then("소유주 알림을 중복 발행하지 않는다") {
                captured.size shouldBe 2
                captured.map { it.templateId }.toSet() shouldBe setOf("booking-confirmed")
            }
        }
    }

    Given("주최자가 실린 티켓 발권 이벤트") {
        val captured = mutableListOf<EnqueueNotificationCommand>()
        val worker = buildWorker(captured)
        val event = TicketEvent.Issued(
            ticketOrderId = 2L,
            recipientUserId = BOOKER_USER_ID,
            eventTitle = "2026 시티리그 4강 홈경기",
            eventOwnerUserId = EVENT_OWNER_USER_ID,
        )

        When("consumeTicket 을 호출하면") {
            worker.consumeTicket(event)

            Then("주최자에게도 알림이 발행된다") {
                captured.filter { it.recipientUserId == EVENT_OWNER_USER_ID }
                    .map { it.channel } shouldContainExactlyInAnyOrder
                    listOf(NotificationChannel.IN_APP, NotificationChannel.PUSH)
            }

            Then("주최자 알림 본문에 쓸 경기 제목이 payload로 공급된다") {
                val ownerCommand = captured.first { it.recipientUserId == EVENT_OWNER_USER_ID }
                ownerCommand.payload.data["eventTitle"] shouldBe "2026 시티리그 4강 홈경기"
            }
        }
    }

    Given("주최자가 비어 있는 티켓 발권 이벤트") {
        val captured = mutableListOf<EnqueueNotificationCommand>()
        val worker = buildWorker(captured)
        val event = TicketEvent.Issued(
            ticketOrderId = 2L,
            recipientUserId = BOOKER_USER_ID,
            eventTitle = "2026 시티리그 4강 홈경기",
            eventOwnerUserId = null,
        )

        When("consumeTicket 을 호출하면") {
            worker.consumeTicket(event)

            Then("구매자 알림만 발행된다") {
                captured.filter { it.recipientUserId != BOOKER_USER_ID }.shouldBeEmpty()
            }
        }
    }
})
