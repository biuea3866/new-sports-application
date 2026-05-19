package com.sportsapp.infrastructure.persistence.ticketing

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.EventStatus
import com.sportsapp.domain.ticketing.Seat
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime

class EventRepositoryImplTest(
    @Autowired private val eventRepositoryImpl: EventRepositoryImpl,
    @Autowired private val seatRepositoryImpl: SeatRepositoryImpl,
    @Autowired private val seatJpaRepository: SeatJpaRepository,
    @Autowired private val transactionManager: PlatformTransactionManager,
) : BaseIntegrationTest() {

    private val startsAt = ZonedDateTime.of(2026, 12, 1, 18, 0, 0, 0, ZoneOffset.UTC)

    init {
        Given("Event를 저장한 뒤") {
            val saved = eventRepositoryImpl.save(
                Event(
                    id = 0L,
                    title = "Concert 2026",
                    venue = "Seoul Arena",
                    startsAt = startsAt,
                    status = EventStatus.SCHEDULED,
                )
            )

            When("findById로 조회하면") {
                val found = eventRepositoryImpl.findById(saved.id)

                Then("[R-01] startsAt이 UTC instant로 정확히 복원된다") {
                    found shouldNotBe null
                    requireNotNull(found).let {
                        it.id shouldBe saved.id
                        it.title shouldBe "Concert 2026"
                        it.startsAt.toInstant() shouldBe startsAt.toInstant()
                        it.status shouldBe EventStatus.SCHEDULED
                    }
                }
            }
        }

        Given("Seat unique 제약 위반 시나리오") {
            val event = eventRepositoryImpl.save(
                Event(
                    id = 0L,
                    title = "Unique Test Event",
                    venue = "Test Venue",
                    startsAt = startsAt,
                    status = EventStatus.SCHEDULED,
                )
            )

            val seat = Seat(
                id = 0L,
                eventId = event.id,
                section = "A",
                rowNo = "1",
                seatNo = "1",
                price = BigDecimal("50000"),
            )
            seatJpaRepository.save(seat)

            When("동일한 (event_id, section, row_no, seat_no)로 INSERT하면") {
                Then("[R-02] unique 제약 위반이 발생한다") {
                    val duplicate = Seat(
                        id = 0L,
                        eventId = event.id,
                        section = "A",
                        rowNo = "1",
                        seatNo = "1",
                        price = BigDecimal("60000"),
                    )
                    shouldThrow<DataIntegrityViolationException> {
                        seatJpaRepository.saveAndFlush(duplicate)
                    }
                }
            }
        }

        Given("Event와 연관된 Seat들이 존재할 때") {
            val event = eventRepositoryImpl.save(
                Event(
                    id = 0L,
                    title = "Cascade Test Event",
                    venue = "Test Venue",
                    startsAt = startsAt,
                    status = EventStatus.SCHEDULED,
                )
            )
            seatJpaRepository.save(
                Seat(
                    id = 0L,
                    eventId = event.id,
                    section = "B",
                    rowNo = "1",
                    seatNo = "1",
                    price = BigDecimal("30000"),
                )
            )

            When("softDeleteByEventId로 소프트 삭제하면") {
                TransactionTemplate(transactionManager).execute {
                    seatRepositoryImpl.softDeleteByEventId(event.id, null)
                }

                Then("[R-03] 연관 Seat가 soft delete되어 active 조회에서 사라진다") {
                    seatJpaRepository.findByEventIdAndDeletedAtIsNullOrderBySectionAscRowNoAscSeatNoAsc(event.id)
                        .size shouldBe 0
                }
            }
        }

        Given("Event에 Seat를 순서 없이 저장한 뒤") {
            val event = eventRepositoryImpl.save(
                Event(
                    id = 0L,
                    title = "Sort Test Event",
                    venue = "Test Venue",
                    startsAt = startsAt,
                    status = EventStatus.SCHEDULED,
                )
            )
            seatJpaRepository.saveAll(
                listOf(
                    Seat(0L, event.id, "B", "2", "3", BigDecimal("10000")),
                    Seat(0L, event.id, "A", "1", "2", BigDecimal("10000")),
                    Seat(0L, event.id, "A", "1", "1", BigDecimal("10000")),
                )
            )

            When("findByEventId로 조회하면") {
                val seats = seatJpaRepository
                    .findByEventIdAndDeletedAtIsNullOrderBySectionAscRowNoAscSeatNoAsc(event.id)

                Then("[R-04] section → rowNo → seatNo asc 정렬로 반환된다") {
                    seats[0].section shouldBe "A"
                    seats[0].seatNo shouldBe "1"
                    seats[1].seatNo shouldBe "2"
                    seats[2].section shouldBe "B"
                }
            }
        }
    }
}
