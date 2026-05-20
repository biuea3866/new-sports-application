package com.sportsapp.infrastructure.persistence.ticketing

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.ticketing.EventCriteria
import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.EventStatus
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.time.ZoneOffset
import java.time.ZonedDateTime

class CustomEventRepositoryImplTest(
    @Autowired private val eventJpaRepository: EventJpaRepository,
    @Autowired private val customEventRepositoryImpl: CustomEventRepositoryImpl,
) : BaseJpaIntegrationTest() {

    private val baseTime = ZonedDateTime.of(2026, 12, 1, 18, 0, 0, 0, ZoneOffset.UTC)

    init {
        Given("날짜 범위 + 상태 필터 페이징 테스트를 위한 Event 픽스처") {
            val event1 = eventJpaRepository.save(
                Event(0L, "Concert Dec", "Seoul Arena", baseTime, EventStatus.OPEN, 1L)
            )
            eventJpaRepository.save(
                Event(0L, "Concert Jan", "Busan Arena", baseTime.plusMonths(1), EventStatus.SCHEDULED, 1L)
            )
            eventJpaRepository.save(
                Event(0L, "Concert Feb", "Daegu Arena", baseTime.plusMonths(2), EventStatus.OPEN, 2L)
            )

            When("[R-01] status=OPEN 필터만 적용하면") {
                val criteria = EventCriteria(status = EventStatus.OPEN, startsAtFrom = null, startsAtTo = null)
                val pageable = PageRequest.of(0, 10, Sort.by("startsAt").ascending())
                val result = customEventRepositoryImpl.findByCriteria(criteria, pageable)

                Then("OPEN 상태 2건만 반환된다") {
                    result.totalElements shouldBe 2L
                    result.content.all { it.status == EventStatus.OPEN } shouldBe true
                }
            }

            When("[R-02] startsAtFrom~startsAtTo 날짜 범위 필터를 적용하면") {
                val from = baseTime.minusDays(1)
                val to = baseTime.plusDays(1)
                val criteria = EventCriteria(status = null, startsAtFrom = from, startsAtTo = to)
                val pageable = PageRequest.of(0, 10, Sort.by("startsAt").ascending())
                val result = customEventRepositoryImpl.findByCriteria(criteria, pageable)

                Then("범위 내 이벤트 1건(event1)만 반환된다") {
                    result.totalElements shouldBe 1L
                    result.content.first().title shouldBe "Concert Dec"
                }
            }

            When("[R-03] 페이지 크기=1로 두 번째 페이지를 조회하면") {
                val criteria = EventCriteria(status = EventStatus.OPEN, startsAtFrom = null, startsAtTo = null)
                val pageable = PageRequest.of(1, 1, Sort.by("startsAt").ascending())
                val result = customEventRepositoryImpl.findByCriteria(criteria, pageable)

                Then("totalElements=2이고 두 번째 페이지 1건이 반환된다") {
                    result.totalElements shouldBe 2L
                    result.content.size shouldBe 1
                    result.content.first().title shouldBe "Concert Feb"
                }
            }

            When("[R-04] soft-delete된 Event는 조회에서 제외되어야 한다") {
                event1.softDelete(null)
                eventJpaRepository.save(event1)

                val criteria = EventCriteria(status = EventStatus.OPEN, startsAtFrom = null, startsAtTo = null)
                val pageable = PageRequest.of(0, 10)
                val result = customEventRepositoryImpl.findByCriteria(criteria, pageable)

                Then("soft-delete된 event1은 포함되지 않는다") {
                    result.content.none { it.id == event1.id } shouldBe true
                }
            }
        }
    }
}
