package com.sportsapp.infrastructure.facility.mysql

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.facility.entity.Program
import com.sportsapp.domain.facility.repository.ProgramCustomRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal

class ProgramCustomRepositoryImplTest(
    @Autowired private val programCustomRepository: ProgramCustomRepository,
    @Autowired private val programJpaRepository: ProgramJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    private fun resetData() {
        jdbcTemplate.execute("TRUNCATE TABLE programs")
    }

    private fun saveProgram(name: String, facilityId: String = "FAC-01"): Program =
        programJpaRepository.save(
            Program.create(
                facilityId = facilityId,
                ownerUserId = 1L,
                name = name,
                description = null,
                price = BigDecimal("30000"),
                capacity = 5,
                durationMinutes = 50,
            ),
        )

    init {
        afterEach { resetData() }

        Given("keyword와 부분 일치하는 program이 여러 시설에 등록되어 있을 때") {
            resetData()
            saveProgram("성인 요가 클래스")
            saveProgram("주니어 요가 클래스", facilityId = "FAC-02")
            saveProgram("필라테스 그룹반")

            When("keyword=요가로 catalog 검색을 실행하면") {
                val result = programCustomRepository.searchForCatalog(
                    keyword = "요가",
                    pageable = PageRequest.of(0, 20),
                )

                Then("이름에 요가가 포함된 program 2건이 페이지로 반환된다") {
                    result.totalElements shouldBe 2
                    result.content.all { it.name.contains("요가") } shouldBe true
                }
            }
        }

        Given("program이 여러 건 등록되어 있을 때") {
            resetData()
            saveProgram("성인 요가 클래스")
            saveProgram("필라테스 그룹반")

            When("keyword 없이 catalog 검색을 실행하면") {
                val result = programCustomRepository.searchForCatalog(
                    keyword = null,
                    pageable = PageRequest.of(0, 20),
                )

                Then("미삭제 program 전체가 반환된다") {
                    result.totalElements shouldBe 2
                }
            }
        }

        Given("soft delete된 program이 포함되어 있을 때") {
            resetData()
            val deletedProgram = saveProgram("폐강된 요가 클래스")
            deletedProgram.softDelete(1L)
            programJpaRepository.save(deletedProgram)
            saveProgram("운영중인 요가 클래스")

            When("keyword=요가로 catalog 검색을 실행하면") {
                val result = programCustomRepository.searchForCatalog(
                    keyword = "요가",
                    pageable = PageRequest.of(0, 20),
                )

                Then("soft delete된 program은 결과에서 제외된다") {
                    result.totalElements shouldBe 1
                    result.content[0].name shouldBe "운영중인 요가 클래스"
                }
            }
        }

        Given("keyword와 일치하는 program이 없을 때") {
            resetData()
            saveProgram("필라테스 그룹반")

            When("keyword=클라이밍으로 catalog 검색을 실행하면") {
                val result = programCustomRepository.searchForCatalog(
                    keyword = "클라이밍",
                    pageable = PageRequest.of(0, 20),
                )

                Then("빈 페이지가 반환된다") {
                    result.totalElements shouldBe 0
                    result.content.isEmpty() shouldBe true
                }
            }
        }
    }
}
