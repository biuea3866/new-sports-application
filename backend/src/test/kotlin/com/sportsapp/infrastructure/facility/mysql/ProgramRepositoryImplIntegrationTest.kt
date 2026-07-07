package com.sportsapp.infrastructure.facility.mysql

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.facility.entity.Program
import com.sportsapp.domain.facility.repository.ProgramRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class ProgramRepositoryImplIntegrationTest(
    @Autowired private val programRepository: ProgramRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE programs")
        }

        Given("시설상품을 저장하면") {
            val program = Program.create(
                facilityId = "FAC-SAVE-01",
                ownerUserId = 1L,
                name = "1:1 PT",
                description = "개인 트레이닝",
                price = BigDecimal("50000"),
                capacity = 1,
                durationMinutes = 60,
            )

            When("save 를 호출하면") {
                val saved = programRepository.save(program)

                Then("id 가 채번되고 findById 로 재조회된다") {
                    saved.id shouldBe saved.id
                    val found = programRepository.findById(saved.id)
                    found?.name shouldBe "1:1 PT"
                    found?.facilityId shouldBe "FAC-SAVE-01"
                }
            }
        }

        Given("같은 시설에 program 이 2건 등록되어 있을 때") {
            programRepository.save(
                Program.create(
                    facilityId = "FAC-LIST-01",
                    ownerUserId = 1L,
                    name = "1:1 PT",
                    description = null,
                    price = BigDecimal.ZERO,
                    capacity = 1,
                    durationMinutes = 60,
                ),
            )
            programRepository.save(
                Program.create(
                    facilityId = "FAC-LIST-01",
                    ownerUserId = 1L,
                    name = "그룹 클래스",
                    description = null,
                    price = BigDecimal.ZERO,
                    capacity = 10,
                    durationMinutes = 50,
                ),
            )
            programRepository.save(
                Program.create(
                    facilityId = "FAC-LIST-02",
                    ownerUserId = 1L,
                    name = "다른 시설 상품",
                    description = null,
                    price = BigDecimal.ZERO,
                    capacity = 5,
                    durationMinutes = 40,
                ),
            )

            When("findByFacilityId 로 조회하면") {
                val result = programRepository.findByFacilityId("FAC-LIST-01")

                Then("해당 시설의 program 2건만 반환된다") {
                    result shouldHaveSize 2
                }
            }
        }

        Given("존재하지 않는 program id 를 조회하면") {
            When("findById 를 호출하면") {
                val result = programRepository.findById(999_999L)

                Then("null 을 반환한다") {
                    result shouldBe null
                }
            }
        }
    }
}
