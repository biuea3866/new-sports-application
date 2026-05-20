package com.sportsapp.infrastructure.persistence.ticketing

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.ticketing.OrderStatus
import com.sportsapp.domain.ticketing.TicketOrder
import com.sportsapp.domain.ticketing.TicketStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class TicketOrderRepositoryImplTest(
    @Autowired private val ticketOrderRepositoryImpl: TicketOrderRepositoryImpl,
    @Autowired private val ticketRepositoryImpl: TicketRepositoryImpl,
    @Autowired private val ticketJpaRepository: TicketJpaRepository,
    @Autowired private val transactionManager: PlatformTransactionManager,
) : BaseIntegrationTest() {

    init {
        Given("TicketOrder를 저장한 뒤") {
            val saved = ticketOrderRepositoryImpl.save(
                TicketOrder(
                    userId = 1L,
                    status = OrderStatus.PENDING,
                    paymentId = null,
                    lockedEventId = 10L,
                    lockedSeatIds = listOf(101L, 102L),
                )
            )

            When("findById로 조회하면") {
                val found = ticketOrderRepositoryImpl.findById(saved.id)

                Then("[R-01] ZonedDateTime(createdAt)이 UTC instant로 정확히 복원된다") {
                    found shouldNotBe null
                    requireNotNull(found).let {
                        it.id shouldBe saved.id
                        it.userId shouldBe 1L
                        it.status shouldBe OrderStatus.PENDING
                        it.lockedEventId shouldBe 10L
                        it.lockedSeatIds shouldBe listOf(101L, 102L)
                        it.createdAt.toInstant() shouldBe saved.createdAt.toInstant()
                    }
                }
            }
        }

        Given("PENDING TicketOrder에 confirm을 호출한 뒤") {
            val order = ticketOrderRepositoryImpl.save(
                TicketOrder(
                    userId = 2L,
                    status = OrderStatus.PENDING,
                    paymentId = null,
                    lockedEventId = 20L,
                    lockedSeatIds = listOf(201L, 202L),
                )
            )

            val tickets = TransactionTemplate(transactionManager).execute {
                val confirmedTickets = order.confirm(paymentId = 999L, seatIds = listOf(201L, 202L))
                ticketOrderRepositoryImpl.save(order)
                ticketRepositoryImpl.saveAll(confirmedTickets)
            }

            When("ticketOrderId로 tickets를 조회하면") {
                val foundTickets = ticketRepositoryImpl.findByTicketOrderId(order.id)

                Then("[R-03] N개 Ticket이 단일 트랜잭션 내에서 처리된다") {
                    requireNotNull(tickets)
                    foundTickets.size shouldBe 2
                    foundTickets.all { it.status == TicketStatus.ISSUED } shouldBe true
                    foundTickets.all { it.ticketOrderId == order.id } shouldBe true
                }
            }
        }

        Given("같은 seatId에 ISSUED Ticket이 이미 존재할 때") {
            val order1 = ticketOrderRepositoryImpl.save(
                TicketOrder(
                    userId = 3L,
                    status = OrderStatus.PENDING,
                    paymentId = null,
                    lockedEventId = 30L,
                    lockedSeatIds = listOf(301L),
                )
            )

            TransactionTemplate(transactionManager).execute {
                val confirmedTickets = order1.confirm(paymentId = 111L, seatIds = listOf(301L))
                ticketOrderRepositoryImpl.save(order1)
                ticketRepositoryImpl.saveAll(confirmedTickets)
            }

            val order2 = ticketOrderRepositoryImpl.save(
                TicketOrder(
                    userId = 4L,
                    status = OrderStatus.PENDING,
                    paymentId = null,
                    lockedEventId = 30L,
                    lockedSeatIds = listOf(301L),
                )
            )

            When("같은 seatId로 다시 ISSUED 발권을 시도하면") {
                Then("[R-01] active_seat_id unique 제약 위반이 발생한다") {
                    shouldThrow<DataIntegrityViolationException> {
                        TransactionTemplate(transactionManager).execute {
                            val duplicateTickets = order2.confirm(paymentId = 222L, seatIds = listOf(301L))
                            ticketOrderRepositoryImpl.save(order2)
                            ticketJpaRepository.saveAllAndFlush(duplicateTickets)
                        }
                    }
                }
            }
        }

        Given("REVOKED 티켓이 있는 seatId에") {
            val order1 = ticketOrderRepositoryImpl.save(
                TicketOrder(
                    userId = 5L,
                    status = OrderStatus.PENDING,
                    paymentId = null,
                    lockedEventId = 40L,
                    lockedSeatIds = listOf(401L),
                )
            )

            TransactionTemplate(transactionManager).execute {
                val tickets = order1.confirm(paymentId = 333L, seatIds = listOf(401L))
                ticketOrderRepositoryImpl.save(order1)
                val savedTickets = ticketRepositoryImpl.saveAll(tickets)
                savedTickets.forEach { it.revoke() }
                ticketRepositoryImpl.saveAll(savedTickets)
            }

            val order2 = ticketOrderRepositoryImpl.save(
                TicketOrder(
                    userId = 6L,
                    status = OrderStatus.PENDING,
                    paymentId = null,
                    lockedEventId = 40L,
                    lockedSeatIds = listOf(401L),
                )
            )

            When("새 ISSUED 발권을 시도하면") {
                Then("[R-02] 성공한다") {
                    var savedCount = 0
                    TransactionTemplate(transactionManager).execute {
                        val tickets = order2.confirm(paymentId = 444L, seatIds = listOf(401L))
                        ticketOrderRepositoryImpl.save(order2)
                        val saved = ticketRepositoryImpl.saveAll(tickets)
                        savedCount = saved.size
                    }
                    savedCount shouldBe 1
                }
            }
        }
    }
}
