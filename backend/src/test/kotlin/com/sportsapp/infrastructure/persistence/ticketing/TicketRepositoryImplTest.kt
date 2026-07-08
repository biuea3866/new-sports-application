package com.sportsapp.infrastructure.persistence.ticketing
import com.sportsapp.infrastructure.ticketing.mysql.TicketRepositoryImpl
import com.sportsapp.infrastructure.ticketing.mysql.TicketJpaRepository
import com.sportsapp.infrastructure.ticketing.mysql.TicketOrderRepositoryImpl

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.entity.Ticket
import com.sportsapp.domain.ticketing.entity.TicketOrder
import com.sportsapp.domain.ticketing.entity.TicketStatus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class TicketRepositoryImplTest(
    @Autowired private val ticketRepositoryImpl: TicketRepositoryImpl,
    @Autowired private val ticketOrderRepositoryImpl: TicketOrderRepositoryImpl,
    @Autowired private val ticketJpaRepository: TicketJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM tickets")
            jdbcTemplate.execute("DELETE FROM ticket_orders")
        }

        Given("[R-01] complimentary Ticket (ticketOrder = null) 을 save 한 뒤") {
            val ticket = Ticket.issueComplimentary(seatId = 1001L)
            val saved = ticketRepositoryImpl.save(ticket)

            When("findById 로 조회하면") {
                val found = ticketJpaRepository.findById(saved.id).orElse(null)

                Then("[R-01] ticketOrder 가 null 로 정확히 복원된다") {
                    found shouldNotBe null
                    requireNotNull(found).ticketOrder shouldBe null
                    found.seatId shouldBe 1001L
                    found.status shouldBe TicketStatus.ISSUED
                }
            }
        }

        Given("[R-02] 정상 Ticket (ticketOrder 참조) 을 save 한 뒤") {
            val order = ticketOrderRepositoryImpl.save(
                TicketOrder(userId = 10L, status = OrderStatus.PENDING, paymentId = null, lockedEventId = 1L, lockedSeatIds = listOf(2002L))
            )
            val ticket = Ticket.issue(ticketOrder = order, seatId = 2002L)
            val saved = ticketRepositoryImpl.save(ticket)

            When("findById 로 조회하면") {
                val found = ticketJpaRepository.findById(saved.id).orElse(null)

                Then("[R-02] ticketOrder.id 가 저장한 order.id 와 일치한다") {
                    found shouldNotBe null
                    requireNotNull(found).ticketOrder?.id shouldBe order.id
                }
            }
        }

        Given("[R-03] 특정 TicketOrder 에 연결된 Ticket 과 complimentary Ticket 이 모두 저장되어 있을 때") {
            val order = ticketOrderRepositoryImpl.save(
                TicketOrder(userId = 11L, status = OrderStatus.PENDING, paymentId = null, lockedEventId = 2L, lockedSeatIds = listOf(3001L))
            )
            val normal = ticketRepositoryImpl.save(Ticket.issue(ticketOrder = order, seatId = 3001L))
            ticketRepositoryImpl.save(Ticket.issueComplimentary(seatId = 3002L))

            When("findByTicketOrderId(order.id) 를 호출하면") {
                val result = ticketRepositoryImpl.findByTicketOrderId(order.id)

                Then("[R-03] 정상 Ticket 만 반환되고 complimentary (null row) 는 제외된다") {
                    result.size shouldBe 1
                    result.first().id shouldBe normal.id
                }
            }
        }

        Given("[R-04] 존재하지 않는 ticketOrderId 로 조회하면") {
            val order = ticketOrderRepositoryImpl.save(
                TicketOrder(userId = 12L, status = OrderStatus.PENDING, paymentId = null, lockedEventId = 3L, lockedSeatIds = listOf(4001L))
            )
            ticketRepositoryImpl.save(Ticket.issue(ticketOrder = order, seatId = 4001L))

            When("findByTicketOrderId(0) 를 호출하면") {
                val result = ticketRepositoryImpl.findByTicketOrderId(0L)

                Then("[R-04] 매칭되는 Ticket 이 없으므로 빈 목록이 반환된다") {
                    result.size shouldBe 0
                }
            }
        }
    }
}
