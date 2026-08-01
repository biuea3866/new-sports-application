package com.sportsapp.infrastructure.booking.mysql

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.repository.SlotRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZonedDateTime

class SlotRepositoryImplIntegrationTest(
    @Autowired private val slotRepository: SlotRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE slots")
        }

        // 예약 목록은 페이지의 Slot을 한 번에 조회해 라벨(title)을 만든다 — N+1 없이
        // 여러 id를 한 번에 되찾아오는지 실제 DB로 확인한다.
        // afterEach가 slots를 TRUNCATE하므로 Given마다 데이터를 새로 넣는다(When 1개씩).
        fun saveSlot(timeRange: String): Slot = slotRepository.save(
            Slot.create(
                facilityId = "FAC-BATCH-01",
                date = ZonedDateTime.now(),
                timeRange = timeRange,
                capacity = 5,
                ownerId = 1L,
            )
        )

        Given("여러 슬롯이 저장돼 있을 때") {
            val first = saveSlot("07:00-08:00")
            val second = saveSlot("10:00-11:00")

            When("두 슬롯 id로 한 번에 조회하면") {
                val result = slotRepository.findAllByIds(listOf(first.id, second.id))

                Then("두 슬롯이 모두 반환된다") {
                    result.map { it.id }.toSet() shouldBe setOf(first.id, second.id)
                }
            }
        }

        Given("조회 대상 id 중 하나가 존재하지 않을 때") {
            val existing = saveSlot("07:00-08:00")

            When("존재하지 않는 id를 섞어 조회하면") {
                val result = slotRepository.findAllByIds(listOf(existing.id, 999_999L))

                Then("존재하는 슬롯만 반환된다(호출부가 기본 라벨로 방어한다)") {
                    result shouldHaveSize 1
                    result[0].id shouldBe existing.id
                }
            }
        }

        Given("조회할 id가 하나도 없을 때") {
            When("빈 id 목록으로 조회하면") {
                val result = slotRepository.findAllByIds(emptyList())

                Then("빈 목록을 반환한다") {
                    result shouldHaveSize 0
                }
            }
        }

        Given("programId를 가진 슬롯과 일반 슬롯이 섞여 있을 때") {
            slotRepository.save(
                Slot.create(
                    facilityId = "FAC-FILTER-01",
                    date = ZonedDateTime.now(),
                    timeRange = "09:00-10:00",
                    capacity = 5,
                    ownerId = 1L,
                    programId = 10L,
                )
            )
            slotRepository.save(
                Slot.create(
                    facilityId = "FAC-FILTER-01",
                    date = ZonedDateTime.now(),
                    timeRange = "10:00-11:00",
                    capacity = 5,
                    ownerId = 1L,
                )
            )

            When("programId=10으로 조회하면") {
                val result = slotRepository.findByFacilityId("FAC-FILTER-01", 10L)

                Then("programId=10인 슬롯만 반환된다") {
                    result shouldHaveSize 1
                    result[0].programId shouldBe 10L
                }
            }
        }

        Given("programId를 가진 슬롯과 일반 슬롯이 섞여 있는 다른 시설") {
            slotRepository.save(
                Slot.create(
                    facilityId = "FAC-FILTER-02",
                    date = ZonedDateTime.now(),
                    timeRange = "09:00-10:00",
                    capacity = 5,
                    ownerId = 1L,
                    programId = 20L,
                )
            )
            slotRepository.save(
                Slot.create(
                    facilityId = "FAC-FILTER-02",
                    date = ZonedDateTime.now(),
                    timeRange = "10:00-11:00",
                    capacity = 5,
                    ownerId = 1L,
                )
            )

            When("programId 없이 조회하면") {
                val result = slotRepository.findByFacilityId("FAC-FILTER-02", null)

                Then("전체 2건이 반환된다") {
                    result shouldHaveSize 2
                }
            }
        }

        // existsActiveByFacilityId 는 SlotJpaRepository#existsByFacilityIdAndDeletedAtIsNull 로 내려간다.
        // 이 3케이스가 `AndDeletedAtIsNull` 의미론과 facilityId 필터를 실 DB 로 검증하는 유일한 지점이다 —
        // 상위 호출부(FacilityOwnerDomainService 의 시설 삭제 가드)는 전부 게이트웨이를 모킹하므로,
        // 여기가 비면 삭제 조건이 빠져도 전체 테스트가 GREEN 이 된다.
        Given("시설에 활성 슬롯이 존재할 때") {
            slotRepository.save(
                Slot.create(
                    facilityId = "FAC-ACTIVE",
                    date = ZonedDateTime.now(),
                    timeRange = "09:00-10:00",
                    capacity = 5,
                    ownerId = 1L,
                )
            )

            When("existsActiveByFacilityId를 호출하면") {
                val result = slotRepository.existsActiveByFacilityId("FAC-ACTIVE")

                Then("true를 반환한다") {
                    result shouldBe true
                }
            }
        }

        Given("다른 시설에만 슬롯이 존재할 때") {
            slotRepository.save(
                Slot.create(
                    facilityId = "FAC-OTHER",
                    date = ZonedDateTime.now(),
                    timeRange = "09:00-10:00",
                    capacity = 5,
                    ownerId = 1L,
                )
            )

            When("슬롯이 없는 시설로 existsActiveByFacilityId를 호출하면") {
                val result = slotRepository.existsActiveByFacilityId("FAC-EMPTY")

                Then("false를 반환한다 (facilityId 필터가 동작한다)") {
                    result shouldBe false
                }
            }
        }

        Given("시설의 슬롯이 전부 soft-delete 된 상태일 때") {
            val slot = slotRepository.save(
                Slot.create(
                    facilityId = "FAC-DELETED",
                    date = ZonedDateTime.now(),
                    timeRange = "09:00-10:00",
                    capacity = 5,
                    ownerId = 1L,
                )
            )
            slot.softDelete(1L)
            slotRepository.save(slot)

            When("existsActiveByFacilityId를 호출하면") {
                val result = slotRepository.existsActiveByFacilityId("FAC-DELETED")

                Then("false를 반환한다 (삭제된 슬롯은 활성으로 세지 않는다)") {
                    result shouldBe false
                }
            }
        }
    }
}
