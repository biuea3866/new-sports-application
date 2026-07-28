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
