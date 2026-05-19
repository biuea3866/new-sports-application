package com.sportsapp.infrastructure.persistence.ticketing

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.EventStatus
import com.sportsapp.domain.ticketing.Seat
import com.sportsapp.domain.ticketing.SeatSpec
import com.sportsapp.domain.ticketing.TicketingDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime

class TicketingScenarioTest(
    @Autowired private val ticketingDomainService: TicketingDomainService,
    @Autowired private val eventRepositoryImpl: EventRepositoryImpl,
    @Autowired private val seatJpaRepository: SeatJpaRepository,
) : BaseIntegrationTest() {

    private val startsAt = ZonedDateTime.of(2026, 12, 25, 19, 0, 0, 0, ZoneOffset.UTC)

    private fun buildSeatSpecs(count: Int): List<SeatSpec> =
        (1..count).map { index ->
            SeatSpec(
                section = "A",
                rowNo = ((index - 1) / 10 + 1).toString(),
                seatNo = ((index - 1) % 10 + 1).toString(),
                price = BigDecimal("50000"),
            )
        }

    init {
        Given("100개의 좌석 스펙") {
            val seatSpecs = buildSeatSpecs(100)

            When("createEvent를 호출하면") {
                val startMs = System.currentTimeMillis()
                val event = ticketingDomainService.createEvent(
                    title = "Bulk Insert Event",
                    venue = "Test Arena",
                    startsAt = startsAt,
                    seats = seatSpecs,
                )
                val elapsedMs = System.currentTimeMillis() - startMs

                Then("[S-01] 5초 내에 100좌석 일괄 INSERT가 완료되고 Event가 SCHEDULED 상태다") {
                    elapsedMs shouldBeLessThan 5_000L
                    event.status shouldBe EventStatus.SCHEDULED
                    seatJpaRepository
                        .findByEventIdAndDeletedAtIsNullOrderBySectionAscRowNoAscSeatNoAsc(event.id)
                        .size shouldBe 100
                }
            }
        }

        Given("이미 존재하는 좌석과 동일한 (event_id, section, row_no, seat_no)를 INSERT 시도할 때") {
            val event = eventRepositoryImpl.save(
                Event(
                    id = 0L,
                    title = "Rollback Test Event",
                    venue = "Test Venue",
                    startsAt = startsAt,
                    status = EventStatus.SCHEDULED,
                )
            )
            seatJpaRepository.save(
                Seat(
                    id = 0L,
                    eventId = event.id,
                    section = "Z",
                    rowNo = "1",
                    seatNo = "1",
                    price = BigDecimal("10000"),
                )
            )

            When("중복 Seat으로 saveAndFlush를 호출하면") {
                Then("[S-02] unique 제약 위반으로 DataIntegrityViolationException이 발생한다") {
                    shouldThrow<DataIntegrityViolationException> {
                        seatJpaRepository.saveAndFlush(
                            Seat(
                                id = 0L,
                                eventId = event.id,
                                section = "Z",
                                rowNo = "1",
                                seatNo = "1",
                                price = BigDecimal("20000"),
                            )
                        )
                    }
                }
            }
        }
    }
}
