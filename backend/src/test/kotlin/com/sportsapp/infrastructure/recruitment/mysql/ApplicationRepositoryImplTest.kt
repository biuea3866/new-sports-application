package com.sportsapp.infrastructure.recruitment.mysql

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.repository.ApplicationRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Given 블록마다 단일 When/Then만 둔다 — Kotest BehaviorSpec은 스펙 람다를 한 번만 순차 실행하므로,
 * 한 Given 아래 형제 When이 여럿이면 첫 형제의 리프 테스트 종료 후 실행되는 afterEach가 테이블을 비워
 * 이후 형제 리프가 참조하는(Given 본문에서 1회만 삽입된) 데이터를 잃는다(선례: PostQueryScenarioTest).
 */
class ApplicationRepositoryImplTest(
    @Autowired private val applicationRepository: ApplicationRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM applications")
        }

        Given("신규 Application을 저장할 때") {
            When("save를 호출하면") {
                val saved = applicationRepository.save(Application.create(recruitmentId = 1L, applicantUserId = 100L))

                Then("id가 채번되고 findById로 조회된다") {
                    val found = applicationRepository.findById(saved.id)
                    found.shouldNotBeNull()
                    found.applicantUserId shouldBe 100L
                }
            }
        }

        Given("존재하지 않는 id 조회") {
            When("findById를 호출하면") {
                Then("null을 반환한다") {
                    applicationRepository.findById(999_999L).shouldBeNull()
                }
            }
        }

        Given("특정 recruitment에 PENDING 1건, CONFIRMED 1건, CANCELLED 1건이 있는 상태에서 countActiveByRecruitmentId를 호출할 때") {
            val recruitmentId = 100L
            applicationRepository.save(Application.create(recruitmentId, 1L))
            val confirmed = applicationRepository.save(Application.create(recruitmentId, 2L))
            confirmed.confirm(paymentId = 10L)
            applicationRepository.save(confirmed)
            val cancelled = applicationRepository.save(Application.create(recruitmentId, 3L))
            cancelled.cancelPending()
            applicationRepository.save(cancelled)

            When("countActiveByRecruitmentId를 호출하면") {
                Then("PENDING·CONFIRMED 합계 2건만 카운트한다") {
                    applicationRepository.countActiveByRecruitmentId(recruitmentId) shouldBe 2
                }
            }
        }

        Given("특정 recruitment에 PENDING 1건, CONFIRMED 1건, CANCELLED 1건이 있는 상태에서 findByRecruitmentId를 호출할 때") {
            val recruitmentId = 101L
            applicationRepository.save(Application.create(recruitmentId, 1L))
            val confirmed = applicationRepository.save(Application.create(recruitmentId, 2L))
            confirmed.confirm(paymentId = 11L)
            applicationRepository.save(confirmed)
            val cancelled = applicationRepository.save(Application.create(recruitmentId, 3L))
            cancelled.cancelPending()
            applicationRepository.save(cancelled)

            When("findByRecruitmentId를 호출하면") {
                Then("3건 전체를 반환한다") {
                    applicationRepository.findByRecruitmentId(recruitmentId).size shouldBe 3
                }
            }
        }

        Given("특정 recruitment에 PENDING 1건, CONFIRMED 1건, CANCELLED 1건이 있는 상태에서 findConfirmedByRecruitmentId를 호출할 때") {
            val recruitmentId = 102L
            applicationRepository.save(Application.create(recruitmentId, 1L))
            val confirmed = applicationRepository.save(Application.create(recruitmentId, 2L))
            confirmed.confirm(paymentId = 12L)
            applicationRepository.save(confirmed)
            val cancelled = applicationRepository.save(Application.create(recruitmentId, 3L))
            cancelled.cancelPending()
            applicationRepository.save(cancelled)

            When("findConfirmedByRecruitmentId를 호출하면") {
                Then("CONFIRMED 1건만 반환한다") {
                    val result = applicationRepository.findConfirmedByRecruitmentId(recruitmentId)
                    result.size shouldBe 1
                    result[0].applicantUserId shouldBe 2L
                }
            }
        }

        Given("신청자 A의 신청 2건과 신청자 B의 신청 1건이 있을 때") {
            applicationRepository.save(Application.create(recruitmentId = 10L, applicantUserId = 500L))
            applicationRepository.save(Application.create(recruitmentId = 11L, applicantUserId = 500L))
            applicationRepository.save(Application.create(recruitmentId = 12L, applicantUserId = 600L))

            When("findByApplicantUserId(500)을 호출하면") {
                val result = applicationRepository.findByApplicantUserId(500L)

                Then("신청자 A의 신청 2건만 반환하고 신청자 B의 신청은 제외한다") {
                    result.size shouldBe 2
                    result.all { it.applicantUserId == 500L } shouldBe true
                }
            }
        }

        Given("신청 이력이 전혀 없는 사용자") {
            When("findByApplicantUserId를 호출하면") {
                Then("빈 목록을 정상 반환한다") {
                    applicationRepository.findByApplicantUserId(777_777L).shouldBeEmpty()
                }
            }
        }
    }
}
