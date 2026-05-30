package com.sportsapp.infrastructure.persistence.ticketing

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.ticketing.Ticket
import com.sportsapp.domain.ticketing.TicketStatus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class TicketRepositoryImplTest(
    @Autowired private val ticketRepositoryImpl: TicketRepositoryImpl,
    @Autowired private val ticketJpaRepository: TicketJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM tickets")
        }

        Given("[R-01] complimentary Ticket (ticketOrderId = null) 을 save 한 뒤") {
            val ticket = Ticket.issueComplimentary(seatId = 1001L)
            val saved = ticketRepositoryImpl.save(ticket)

            When("findById 로 조회하면") {
                val found = ticketJpaRepository.findById(saved.id).orElse(null)

                Then("[R-01] ticketOrderId 가 null 로 정확히 복원된다") {
                    found shouldNotBe null
                    requireNotNull(found).ticketOrderId shouldBe null
                    found.seatId shouldBe 1001L
                    found.status shouldBe TicketStatus.ISSUED
                }
            }
        }

        Given("[R-02] 정상 Ticket (ticketOrderId = 42) 을 save 한 뒤") {
            val ticket = Ticket.issue(ticketOrderId = 42L, seatId = 2002L)
            val saved = ticketRepositoryImpl.save(ticket)

            When("findById 로 조회하면") {
                val found = ticketJpaRepository.findById(saved.id).orElse(null)

                Then("[R-02] ticketOrderId 가 42 로 유지된다") {
                    found shouldNotBe null
                    requireNotNull(found).ticketOrderId shouldBe 42L
                }
            }
        }

        Given("[R-03] ticketOrderId = 42 인 정상 Ticket 과 complimentary Ticket 이 모두 저장되어 있을 때") {
            val normal = ticketRepositoryImpl.save(Ticket.issue(ticketOrderId = 42L, seatId = 3001L))
            ticketRepositoryImpl.save(Ticket.issueComplimentary(seatId = 3002L))

            When("findByTicketOrderId(42) 를 호출하면") {
                val result = ticketRepositoryImpl.findByTicketOrderId(42L)

                Then("[R-03] 정상 Ticket 만 반환되고 complimentary (null row) 는 제외된다") {
                    result.size shouldBe 1
                    result.first().id shouldBe normal.id
                }
            }
        }

        Given("[R-04] V25 backfill 결과로 sentinel 0L 이 null 로 전환된 환경 — findByTicketOrderId(0) 는 0 row 를 반환한다") {
            ticketRepositoryImpl.save(Ticket.issue(ticketOrderId = 42L, seatId = 4001L))

            When("findByTicketOrderId(0) 를 호출하면") {
                val result = ticketRepositoryImpl.findByTicketOrderId(0L)

                Then("[R-04] sentinel 0 에 매칭되는 Ticket 이 없으므로 빈 목록이 반환된다") {
                    result.size shouldBe 0
                }
            }
        }
    }
}
