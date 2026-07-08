package com.sportsapp.infrastructure.community.gateway

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.repository.SlotRepository
import com.sportsapp.domain.community.gateway.SlotInfoGateway
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class SlotInfoGatewayImplTest(
    @Autowired private val slotInfoGateway: SlotInfoGateway,
    @Autowired private val slotRepository: SlotRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE slots")
        }

        Given("실제 존재하는 slot") {
            val slot = slotRepository.save(
                Slot.create(
                    facilityId = "FAC-GW-01",
                    date = ZonedDateTime.now(),
                    timeRange = "09:00-10:00",
                    capacity = 8,
                    ownerId = 1L,
                ),
            )

            When("findBy(slotId) 를 호출하면") {
                val slotInfo = slotInfoGateway.findBy(slot.id)

                Then("시설·일시·정원 표시정보가 반환된다") {
                    slotInfo.shouldNotBeNull()
                    slotInfo.facilityId shouldBe "FAC-GW-01"
                    slotInfo.timeRange shouldBe "09:00-10:00"
                    slotInfo.capacity shouldBe 8
                }
            }
        }

        Given("존재하지 않는 slotId") {
            When("findBy(slotId) 를 호출하면") {
                val slotInfo = slotInfoGateway.findBy(999_999L)

                Then("null 을 반환한다") {
                    slotInfo.shouldBeNull()
                }
            }
        }
    }
}
