package com.sportsapp.presentation.operator.worker

import com.sportsapp.application.operator.dto.RecordOperatorInboxEventCommand
import com.sportsapp.application.operator.usecase.RecordOperatorInboxEventUseCase
import com.sportsapp.domain.booking.event.BookingEvent
import com.sportsapp.domain.operator.vo.OperatorInboxNotificationType
import com.sportsapp.domain.ticketing.event.TicketEvent
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

private const val BOOKER_USER_ID = 68L
private const val PARTNER_USER_ID = 69L

/**
 * 운영 인박스 적재 워커.
 *
 * `OperatorInboxNotificationDomainService.create()` 호출부가 main 전체에 하나도 없어 운영
 * 인박스가 영구히 빈 화면이었다. 예약·티켓 이벤트를 구독해 파트너가 조치할 사건을 쌓는다.
 */
class OperatorInboxEventWorkerTest : BehaviorSpec({

    fun buildWorker(captured: MutableList<RecordOperatorInboxEventCommand>): OperatorInboxEventWorker {
        val useCase = mockk<RecordOperatorInboxEventUseCase>()
        every { useCase.execute(any()) } answers { captured.add(firstArg()); Unit }
        return OperatorInboxEventWorker(useCase)
    }

    Given("시설 소유주가 실린 예약 확정 이벤트") {
        val captured = mutableListOf<RecordOperatorInboxEventCommand>()
        val worker = buildWorker(captured)
        val event = BookingEvent.Confirmed(
            bookingId = 1L,
            paymentId = 500L,
            recipientUserId = BOOKER_USER_ID,
            facilityOwnerUserId = PARTNER_USER_ID,
        )

        When("consumeBooking 을 호출하면") {
            worker.consumeBooking(event)

            Then("시설 소유주 앞으로 신규 예약 접수가 적재된다") {
                captured.size shouldBe 1
                captured.first().recipientUserId shouldBe PARTNER_USER_ID
                captured.first().type shouldBe OperatorInboxNotificationType.BOOKING_RECEIVED
            }

            // 운영 인박스는 조치 대상 피드다 — 상세로 이동할 링크가 없으면 쓸모가 없다.
            Then("예약 관리로 이동할 링크가 담긴다") {
                captured.first().link shouldBe "/portal/bookings"
            }

            // Kafka는 at-least-once — 중복 수신을 하류가 가려낼 수 있어야 한다.
            Then("멱등 키로 쓸 이벤트 id가 전달된다") {
                captured.first().eventId shouldBe event.eventId
            }
        }
    }

    Given("소유주가 비어 있는 예약 확정 이벤트") {
        val captured = mutableListOf<RecordOperatorInboxEventCommand>()
        val worker = buildWorker(captured)
        val event = BookingEvent.Confirmed(
            bookingId = 1L,
            paymentId = 500L,
            recipientUserId = BOOKER_USER_ID,
            facilityOwnerUserId = null,
        )

        When("consumeBooking 을 호출하면") {
            worker.consumeBooking(event)

            Then("적재 대상이 없으므로 아무것도 쌓지 않는다") {
                captured.shouldBeEmpty()
            }
        }
    }

    Given("주최자가 실린 티켓 발권 이벤트") {
        val captured = mutableListOf<RecordOperatorInboxEventCommand>()
        val worker = buildWorker(captured)
        val event = TicketEvent.Issued(
            ticketOrderId = 2L,
            recipientUserId = BOOKER_USER_ID,
            eventTitle = "2026 시티리그 4강 홈경기",
            eventOwnerUserId = PARTNER_USER_ID,
        )

        When("consumeTicket 을 호출하면") {
            worker.consumeTicket(event)

            Then("주최자 앞으로 티켓 판매가 적재된다") {
                captured.size shouldBe 1
                captured.first().recipientUserId shouldBe PARTNER_USER_ID
                captured.first().type shouldBe OperatorInboxNotificationType.TICKET_SOLD
            }

            Then("본문에 경기 제목이 채워진다") {
                captured.first().body shouldBe "2026 시티리그 4강 홈경기 티켓이 발권됐습니다."
            }
        }
    }

    Given("주최자가 비어 있는 티켓 발권 이벤트") {
        val captured = mutableListOf<RecordOperatorInboxEventCommand>()
        val worker = buildWorker(captured)
        val event = TicketEvent.Issued(
            ticketOrderId = 2L,
            recipientUserId = BOOKER_USER_ID,
            eventTitle = "2026 시티리그 4강 홈경기",
            eventOwnerUserId = null,
        )

        When("consumeTicket 을 호출하면") {
            worker.consumeTicket(event)

            Then("아무것도 쌓지 않는다") {
                captured.shouldBeEmpty()
            }
        }
    }
})
