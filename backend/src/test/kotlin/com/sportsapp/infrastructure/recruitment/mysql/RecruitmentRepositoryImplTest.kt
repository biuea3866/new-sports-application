package com.sportsapp.infrastructure.recruitment.mysql

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.entity.RecruitmentStatus
import com.sportsapp.domain.recruitment.repository.RecruitmentRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate

private fun newRecruitment(communityId: Long? = null): Recruitment = Recruitment.create(
    title = "주말 축구 모임",
    capacity = 10,
    feeAmount = BigDecimal("10000"),
    activityAt = ZonedDateTime.now().plusDays(10),
    applicationDeadline = ZonedDateTime.now().plusDays(5),
    communityId = communityId,
    recruiterUserId = 1L,
)

/**
 * Given 블록마다 단일 When/Then만 둔다 — Kotest BehaviorSpec은 스펙 람다를 한 번만 순차 실행하므로,
 * 한 Given 아래 형제 When이 여럿이면 첫 형제의 리프 테스트 종료 후 실행되는 afterEach가 테이블을 비워
 * 이후 형제 리프가 참조하는(Given 본문에서 1회만 삽입된) 데이터를 잃는다(선례: PostQueryScenarioTest).
 * findForUpdateById(@Lock PESSIMISTIC_WRITE)는 활성 트랜잭션이 필요하므로 transactionTemplate로 감싼다
 * (선례: BookingConfirmRepositoryIntegrationTest).
 */
class RecruitmentRepositoryImplTest(
    @Autowired private val recruitmentRepository: RecruitmentRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val transactionTemplate: TransactionTemplate,
) : BaseJpaIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM applications")
            jdbcTemplate.execute("DELETE FROM recruitments")
        }

        Given("신규 Recruitment를 저장할 때") {
            When("save를 호출하면") {
                val saved = recruitmentRepository.save(newRecruitment())

                Then("id가 채번되고 findById로 동일 엔티티를 조회할 수 있다") {
                    (saved.id > 0) shouldBe true
                    val found = recruitmentRepository.findById(saved.id)
                    found.shouldNotBeNull()
                    found.title shouldBe "주말 축구 모임"
                }
            }
        }

        Given("존재하지 않는 id 조회") {
            When("findById를 호출하면") {
                Then("null을 반환한다") {
                    recruitmentRepository.findById(999_999L).shouldBeNull()
                }
            }
        }

        Given("저장된 Recruitment") {
            val saved = recruitmentRepository.save(newRecruitment())

            When("findForUpdateById를 호출하면") {
                Then("비관적 락으로 동일 엔티티를 조회한다") {
                    val found = transactionTemplate.execute {
                        recruitmentRepository.findForUpdateById(saved.id)
                    }
                    found.shouldNotBeNull()
                    found.id shouldBe saved.id
                }
            }
        }

        Given("특정 커뮤니티 소속 모집 2건과 다른 커뮤니티 소속 모집 1건이 있는 상태에서 communityId로 필터링할 때") {
            recruitmentRepository.save(newRecruitment(communityId = 10L))
            recruitmentRepository.save(newRecruitment(communityId = 10L))
            recruitmentRepository.save(newRecruitment(communityId = 20L))

            When("findAll(communityId=10)을 호출하면") {
                val result = recruitmentRepository.findAll(10L)

                Then("커뮤니티 10에 소속된 2건만 반환한다") {
                    result.size shouldBe 2
                    result.all { it.communityId == 10L } shouldBe true
                }
            }
        }

        Given("특정 커뮤니티 소속 모집 2건과 다른 커뮤니티 소속 모집 1건이 있는 상태에서 communityId 없이 조회할 때") {
            recruitmentRepository.save(newRecruitment(communityId = 10L))
            recruitmentRepository.save(newRecruitment(communityId = 10L))
            recruitmentRepository.save(newRecruitment(communityId = 20L))

            When("findAll(communityId=null)을 호출하면") {
                val result = recruitmentRepository.findAll(null)

                Then("전체 모집을 반환한다") {
                    result.size shouldBe 3
                }
            }
        }

        Given("모집이 하나도 없는 상태") {
            When("findAll(communityId=999)을 호출하면") {
                Then("빈 목록을 반환한다") {
                    recruitmentRepository.findAll(999L).shouldBeEmpty()
                }
            }
        }

        Given("OPEN 상태의 Recruitment를 저장 후 재조회할 때") {
            val saved = recruitmentRepository.save(newRecruitment())

            When("findById로 재조회하면") {
                val found = recruitmentRepository.findById(saved.id)

                Then("status가 OPEN으로 영속화되어 있다") {
                    found.shouldNotBeNull()
                    found.status shouldBe RecruitmentStatus.OPEN
                }
            }
        }
    }
}
