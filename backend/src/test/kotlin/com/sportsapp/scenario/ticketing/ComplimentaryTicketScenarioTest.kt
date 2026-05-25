package com.sportsapp.scenario.ticketing

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.ticketing.IssueComplimentaryTicketCommand
import com.sportsapp.application.ticketing.IssueComplimentaryTicketUseCase
import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.EventStatus
import com.sportsapp.domain.ticketing.Seat
import com.sportsapp.domain.ticketing.Ticket
import com.sportsapp.domain.ticketing.TicketStatus
import com.sportsapp.infrastructure.persistence.ticketing.EventJpaRepository
import com.sportsapp.infrastructure.persistence.ticketing.SeatJpaRepository
import com.sportsapp.infrastructure.persistence.ticketing.TicketJpaRepository
import com.sportsapp.infrastructure.persistence.ticketing.TicketOrderJpaRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime

@AutoConfigureMockMvc
class ComplimentaryTicketScenarioTest(
    @Autowired private val issueComplimentaryTicketUseCase: IssueComplimentaryTicketUseCase,
    @Autowired private val eventJpaRepository: EventJpaRepository,
    @Autowired private val seatJpaRepository: SeatJpaRepository,
    @Autowired private val ticketJpaRepository: TicketJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    private val baseTime = ZonedDateTime.of(2027, 3, 15, 19, 0, 0, 0, ZoneOffset.UTC)
    private val ownerUserId = 10L

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM tickets")
            jdbcTemplate.execute("DELETE FROM ticket_orders")
        }

        Given("[S-01] 이벤트 소유자가 무료 티켓 발급 요청") {
            val event = eventJpaRepository.save(
                Event(0L, "Complimentary Concert", "Seoul", baseTime, EventStatus.OPEN, ownerUserId)
            )
            val seat = seatJpaRepository.save(Seat(0L, event.id, "A", "1", "1", BigDecimal("0")))

            When("IssueComplimentaryTicketUseCase.execute 를 호출하면") {
                val command = IssueComplimentaryTicketCommand(
                    eventId = event.id,
                    seatId = seat.id,
                    operatorUserId = ownerUserId,
                )
                val result = issueComplimentaryTicketUseCase.execute(command)

                Then("[S-01] DB 에 ticketOrderId = null 인 Ticket 이 저장되고 응답의 status 가 ISSUED 이다") {
                    result.status shouldBe TicketStatus.ISSUED
                    result.ticketId shouldNotBe 0L

                    val saved = ticketJpaRepository.findById(result.ticketId).orElse(null)
                    saved shouldNotBe null
                    requireNotNull(saved).ticketOrderId shouldBe null
                    saved.status shouldBe TicketStatus.ISSUED
                }
            }
        }

        Given("[S-02] 같은 seat 에 complimentary Ticket 과 정상 주문 Ticket 이 모두 존재해야 하는 상황") {
            val event = eventJpaRepository.save(
                Event(0L, "Dual Ticket Concert", "Busan", baseTime.plusDays(1), EventStatus.OPEN, ownerUserId)
            )
            val seat1 = seatJpaRepository.save(Seat(0L, event.id, "B", "1", "1", BigDecimal("0")))
            val seat2 = seatJpaRepository.save(Seat(0L, event.id, "B", "1", "2", BigDecimal("30000")))

            When("complimentary Ticket 과 정상 Ticket 을 각각 저장하면") {
                val complimentaryCommand = IssueComplimentaryTicketCommand(
                    eventId = event.id,
                    seatId = seat1.id,
                    operatorUserId = ownerUserId,
                )
                val complimentaryResult = issueComplimentaryTicketUseCase.execute(complimentaryCommand)

                val normalTicket = ticketJpaRepository.save(Ticket.issue(ticketOrderId = 42L, seatId = seat2.id))

                Then("[S-02] 두 Ticket 이 동시에 존재하며 ticketOrderId 가 각각 null / 42 이다") {
                    val complimentarySaved = ticketJpaRepository.findById(complimentaryResult.ticketId).orElse(null)
                    requireNotNull(complimentarySaved).ticketOrderId shouldBe null

                    val normalSaved = ticketJpaRepository.findById(normalTicket.id).orElse(null)
                    requireNotNull(normalSaved).ticketOrderId shouldBe 42L
                }
            }
        }

        Given("[S-03] complimentary Ticket 이 JSON 직렬화될 때") {
            val event = eventJpaRepository.save(
                Event(0L, "JSON Serialization Test Concert", "Incheon", baseTime.plusDays(2), EventStatus.OPEN, ownerUserId)
            )
            val seat = seatJpaRepository.save(Seat(0L, event.id, "C", "2", "1", BigDecimal("0")))

            When("IssueComplimentaryTicketUseCase.execute 를 호출하면") {
                val command = IssueComplimentaryTicketCommand(
                    eventId = event.id,
                    seatId = seat.id,
                    operatorUserId = ownerUserId,
                )
                val result = issueComplimentaryTicketUseCase.execute(command)

                Then("[S-03] IssueComplimentaryTicketResponse 의 ticketId 필드가 반환되고 ticketOrderId 는 Response 에 노출되지 않는다") {
                    result.ticketId shouldNotBe 0L
                    result.seatId shouldBe seat.id
                    result.status shouldBe TicketStatus.ISSUED
                    result.code.length shouldBe 64
                }
            }
        }
    }
}
