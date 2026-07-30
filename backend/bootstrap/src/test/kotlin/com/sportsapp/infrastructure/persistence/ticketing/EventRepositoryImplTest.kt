package com.sportsapp.infrastructure.persistence.ticketing
import com.sportsapp.infrastructure.ticketing.mysql.SeatJpaRepository
import com.sportsapp.infrastructure.ticketing.mysql.SeatRepositoryImpl
import com.sportsapp.infrastructure.ticketing.mysql.EventRepositoryImpl

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.EventStatus
import com.sportsapp.domain.ticketing.entity.Seat
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
                    ownerId = 1L,
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
                    ownerId = 1L,
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
                    ownerId = 1L,
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
                    ownerId = 1L,
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

        Given("[R-01-owner] ownerId를 포함한 Event를 저장한 뒤") {
            val ownerUserId = 100L
            val saved = eventRepositoryImpl.save(
                Event(
                    id = 0L,
                    title = "Owner Persist Test",
                    venue = "Owner Venue",
                    startsAt = startsAt,
                    status = EventStatus.SCHEDULED,
                    ownerId = ownerUserId,
                )
            )

            When("findById로 조회하면") {
                val found = eventRepositoryImpl.findById(saved.id)

                Then("[R-01] owner_id 컬럼이 영속화된다") {
                    requireNotNull(found).ownerId shouldBe ownerUserId
                }
            }
        }

        Given("[R-02] ownerId가 다른 두 Event가 존재할 때") {
            val owner1Id = 201L
            val owner2Id = 202L
            eventRepositoryImpl.save(
                Event(0L, "Owner1 Event A", "Venue A", startsAt, EventStatus.SCHEDULED, owner1Id)
            )
            eventRepositoryImpl.save(
                Event(0L, "Owner1 Event B", "Venue B", startsAt.plusHours(1), EventStatus.SCHEDULED, owner1Id)
            )
            eventRepositoryImpl.save(
                Event(0L, "Owner2 Event", "Venue C", startsAt.plusHours(2), EventStatus.SCHEDULED, owner2Id)
            )

            When("owner1Id로 findByOwnerId를 호출하면") {
                val pageable = org.springframework.data.domain.PageRequest.of(0, 10)
                val result = eventRepositoryImpl.findByOwnerId(owner1Id, pageable)

                Then("[R-02] owner1의 Event 2건만 반환된다") {
                    result.totalElements shouldBe 2L
                    result.content.all { it.ownerId == owner1Id } shouldBe true
                }
            }
        }

        Given("[R-03] ownerId가 다른 Event가 존재할 때") {
            val ownerAId = 301L
            val ownerBId = 302L
            val eventOfOwnerA = eventRepositoryImpl.save(
                Event(0L, "OwnerA Event", "Venue A", startsAt.plusHours(3), EventStatus.SCHEDULED, ownerAId)
            )

            When("ownerBId로 findByIdAndOwnerId를 호출하면") {
                val result = eventRepositoryImpl.findByIdAndOwnerId(eventOfOwnerA.id, ownerBId)

                Then("[R-03] ownerId 불일치 시 null이 반환된다") {
                    result shouldBe null
                }
            }

            When("ownerAId로 findByIdAndOwnerId를 호출하면") {
                val result = eventRepositoryImpl.findByIdAndOwnerId(eventOfOwnerA.id, ownerAId)

                Then("[R-03b] ownerId 일치 시 Event가 반환된다") {
                    requireNotNull(result).id shouldBe eventOfOwnerA.id
                }
            }
        }
    }
}
