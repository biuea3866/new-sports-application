package com.sportsapp.infrastructure.facility.gateway

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.facility.gateway.SlotQueryGateway
import com.sportsapp.infrastructure.booking.mysql.SlotJpaRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZonedDateTime

class SlotQueryGatewayImplTest(
    @Autowired private val slotQueryGateway: SlotQueryGateway,
    @Autowired private val slotJpaRepository: SlotJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    private fun createSlot(facilityId: String): Slot = slotJpaRepository.save(
        Slot.create(
            facilityId = facilityId,
            date = ZonedDateTime.now(),
            timeRange = "09:00-10:00",
            capacity = 5,
            ownerId = 1L,
        )
    )

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE bookings")
            jdbcTemplate.execute("TRUNCATE TABLE slots")
        }

        Given("시설에 활성 슬롯이 존재할 때") {
            createSlot("FAC-ACTIVE")

            When("hasActiveSlots를 호출하면") {
                val result = slotQueryGateway.hasActiveSlots("FAC-ACTIVE")

                Then("true를 반환한다") {
                    result shouldBe true
                }
            }
        }

        Given("시설에 슬롯이 하나도 없을 때") {
            createSlot("FAC-OTHER")

            When("다른 시설로 hasActiveSlots를 호출하면") {
                val result = slotQueryGateway.hasActiveSlots("FAC-EMPTY")

                Then("false를 반환한다") {
                    result shouldBe false
                }
            }
        }

        Given("시설의 슬롯이 soft-delete 된 상태일 때") {
            val slot = createSlot("FAC-DELETED")
            slot.softDelete(1L)
            slotJpaRepository.save(slot)

            When("hasActiveSlots를 호출하면") {
                val result = slotQueryGateway.hasActiveSlots("FAC-DELETED")

                Then("false를 반환한다") {
                    result shouldBe false
                }
            }
        }
    }
}
