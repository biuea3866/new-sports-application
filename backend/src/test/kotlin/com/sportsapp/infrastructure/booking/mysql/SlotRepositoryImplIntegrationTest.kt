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
    }
}
