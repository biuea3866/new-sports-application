package com.sportsapp.infrastructure.recruitment.mysql

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.repository.ApplicationCustomRepository
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Given 블록마다 단일 When/Then만 둔다 — Kotest BehaviorSpec은 스펙 람다를 한 번만 순차 실행하므로,
 * 한 Given 아래 형제 When이 여럿이면 첫 형제의 리프 테스트 종료 후 실행되는 afterEach가 테이블을 비워
 * 이후 형제 리프가 참조하는(Given 본문에서 1회만 삽입된) 데이터를 잃는다(선례: PostQueryScenarioTest).
 */
class ApplicationCustomRepositoryImplTest(
    @Autowired private val applicationCustomRepository: ApplicationCustomRepository,
    @Autowired private val applicationJpaRepository: ApplicationJpaRepository,
    @Autowired private val recruitmentJpaRepository: RecruitmentJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    private val activityAt = ZonedDateTime.now().plusDays(30)

    private fun saveRecruitment(title: String): Recruitment = recruitmentJpaRepository.save(
        Recruitment.create(
            title = title,
            capacity = 10,
            feeAmount = BigDecimal("10000"),
            activityAt = activityAt,
            applicationDeadline = activityAt.minusDays(1),
            communityId = null,
            recruiterUserId = 1L,
        ),
    )

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM applications")
            jdbcTemplate.execute("DELETE FROM recruitments")
        }

        Given("모집이 존재하는 Application이 있을 때") {
            val recruitment = saveRecruitment("주말 축구 모임")
            val application = applicationJpaRepository.save(
                Application.create(recruitmentId = recruitment.id, applicantUserId = 9L),
            )

            When("사용자 ID로 findBy를 호출하면") {
                val result = applicationCustomRepository.findBy(9L)

                Then("모집명(title)이 포함된 신청이 반환된다") {
                    result.size shouldBe 1
                    result.first().applicationId shouldBe application.id
                    result.first().status shouldBe ApplicationStatus.PENDING
                    result.first().recruitmentTitle shouldBe "주말 축구 모임"
                }
            }
        }

        Given("참조 Recruitment가 존재하지 않는(recruitmentId가 부재한) Application이 있을 때") {
            val application = applicationJpaRepository.save(
                Application.create(recruitmentId = 999_999L, applicantUserId = 11L),
            )

            When("사용자 ID로 findBy를 호출하면") {
                val result = applicationCustomRepository.findBy(11L)

                Then("빈 title로 방어 반환된다") {
                    result.size shouldBe 1
                    result.first().applicationId shouldBe application.id
                    result.first().recruitmentTitle shouldBe ""
                }
            }
        }

        Given("참조 Recruitment가 soft-delete된 Application이 있을 때") {
            val recruitment = saveRecruitment("삭제된 모집")
            recruitment.softDelete(null)
            recruitmentJpaRepository.save(recruitment)
            val application = applicationJpaRepository.save(
                Application.create(recruitmentId = recruitment.id, applicantUserId = 12L),
            )

            When("사용자 ID로 findBy를 호출하면") {
                val result = applicationCustomRepository.findBy(12L)

                Then("삭제된 모집의 title 대신 빈 title로 방어 반환된다") {
                    result.size shouldBe 1
                    result.first().applicationId shouldBe application.id
                    result.first().recruitmentTitle shouldBe ""
                }
            }
        }

        Given("다른 사용자의 Application이 섞여 있을 때") {
            val recruitment = saveRecruitment("공유 모집")
            applicationJpaRepository.save(Application.create(recruitmentId = recruitment.id, applicantUserId = 13L))
            applicationJpaRepository.save(Application.create(recruitmentId = recruitment.id, applicantUserId = 14L))

            When("applicantUserId=13으로 findBy를 호출하면") {
                val result = applicationCustomRepository.findBy(13L)

                Then("신청자 13의 신청만 반환된다") {
                    result.size shouldBe 1
                    result.first().recruitmentTitle shouldBe "공유 모집"
                }
            }
        }

        Given("신청 이력이 전혀 없는 사용자") {
            When("findBy를 호출하면") {
                Then("빈 목록을 정상 반환한다") {
                    applicationCustomRepository.findBy(777_777L).size shouldBe 0
                }
            }
        }
    }
}
