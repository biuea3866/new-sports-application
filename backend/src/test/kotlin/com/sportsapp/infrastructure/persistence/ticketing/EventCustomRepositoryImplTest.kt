package com.sportsapp.infrastructure.persistence.ticketing
import com.sportsapp.infrastructure.ticketing.mysql.EventCustomRepositoryImpl
import com.sportsapp.infrastructure.ticketing.mysql.EventJpaRepository

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.ticketing.dto.EventCriteria
import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.EventStatus
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.time.ZoneOffset
import java.time.ZonedDateTime

class EventCustomRepositoryImplTest(
    @Autowired private val eventJpaRepository: EventJpaRepository,
    @Autowired private val eventCustomRepositoryImpl: EventCustomRepositoryImpl,
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
                val result = eventCustomRepositoryImpl.findByCriteria(criteria, pageable)

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
                val result = eventCustomRepositoryImpl.findByCriteria(criteria, pageable)

                Then("범위 내 이벤트 1건(event1)만 반환된다") {
                    result.totalElements shouldBe 1L
                    result.content.first().title shouldBe "Concert Dec"
                }
            }

            When("[R-03] 페이지 크기=1로 두 번째 페이지를 조회하면") {
                val criteria = EventCriteria(status = EventStatus.OPEN, startsAtFrom = null, startsAtTo = null)
                val pageable = PageRequest.of(1, 1, Sort.by("startsAt").ascending())
                val result = eventCustomRepositoryImpl.findByCriteria(criteria, pageable)

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
                val result = eventCustomRepositoryImpl.findByCriteria(criteria, pageable)

                Then("soft-delete된 event1은 포함되지 않는다") {
                    result.content.none { it.id == event1.id } shouldBe true
                }
            }
        }

        Given("keyword 검색을 위한 Event 픽스처") {
            // 같은 클래스의 앞선 Given 픽스처와 테이블을 공유하므로(SharedTestContainers, 클래스 전체 SingleInstance)
            // 날짜 범위를 이 Given 전용으로 분리해 다른 Given의 잔여 데이터와 섞이지 않게 한다.
            val catalogTime = baseTime.plusYears(1)
            val rock = eventJpaRepository.save(
                Event(0L, "Rock Festival", "Seoul Arena", catalogTime, EventStatus.OPEN, 1L)
            )
            eventJpaRepository.save(
                Event(0L, "Jazz Night", "Busan Arena", catalogTime, EventStatus.OPEN, 1L)
            )
            val closedRock = eventJpaRepository.save(
                Event(0L, "Rock Legends", "Daegu Arena", catalogTime, EventStatus.CLOSED, 1L)
            )

            When("keyword=\"Rock\" + status=OPEN으로 조회하면") {
                val criteria = EventCriteria(status = EventStatus.OPEN, startsAtFrom = null, startsAtTo = null, keyword = "Rock")
                val pageable = PageRequest.of(0, 10)
                val result = eventCustomRepositoryImpl.findByCriteria(criteria, pageable)

                Then("제목에 Rock을 포함하는 OPEN 이벤트만 반환된다") {
                    result.totalElements shouldBe 1L
                    result.content.first().id shouldBe rock.id
                }
            }

            When("keyword가 null이면") {
                val criteria = EventCriteria(
                    status = EventStatus.OPEN,
                    startsAtFrom = catalogTime.minusDays(1),
                    startsAtTo = catalogTime.plusDays(1),
                    keyword = null,
                )
                val pageable = PageRequest.of(0, 10)
                val result = eventCustomRepositoryImpl.findByCriteria(criteria, pageable)

                Then("OPEN 이벤트 전체가 페이지네이션으로 조회된다") {
                    result.totalElements shouldBe 2L
                }
            }

            When("keyword=\"Rock\" + status=CLOSED로 조회하면") {
                val criteria = EventCriteria(status = EventStatus.CLOSED, startsAtFrom = null, startsAtTo = null, keyword = "Rock")
                val pageable = PageRequest.of(0, 10)
                val result = eventCustomRepositoryImpl.findByCriteria(criteria, pageable)

                Then("CLOSED 상태의 Rock Legends만 반환된다 (OPEN 카탈로그에서는 제외 대상)") {
                    result.totalElements shouldBe 1L
                    result.content.first().id shouldBe closedRock.id
                }
            }

            When("keyword와 일치하는 이벤트가 없으면") {
                val criteria = EventCriteria(status = EventStatus.OPEN, startsAtFrom = null, startsAtTo = null, keyword = "Nonexistent")
                val pageable = PageRequest.of(0, 10)
                val result = eventCustomRepositoryImpl.findByCriteria(criteria, pageable)

                Then("빈 페이지가 반환된다") {
                    result.totalElements shouldBe 0L
                    result.content shouldBe emptyList()
                }
            }
        }
    }
}
