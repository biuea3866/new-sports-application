package com.sportsapp.infrastructure.recruitment.mysql

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.repository.RecruitmentCustomRepository
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Given 블록마다 단일 When/Then만 둔다 — Kotest BehaviorSpec은 스펙 람다를 한 번만 순차 실행하므로,
 * 한 Given 아래 형제 When이 여럿이면 첫 형제의 리프 테스트 종료 후 실행되는 afterEach가 테이블을 비워
 * 이후 형제 리프가 참조하는(Given 본문에서 1회만 삽입된) 데이터를 잃는다(선례: PostQueryScenarioTest).
 */
class RecruitmentCustomRepositoryImplTest(
    @Autowired private val recruitmentCustomRepository: RecruitmentCustomRepository,
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

        Given("keyword와 부분 일치하는 OPEN 모집이 여럿 있을 때") {
            saveRecruitment("주말 축구 모임")
            saveRecruitment("주중 축구 소모임")
            saveRecruitment("등산 동호회")

            When("keyword=\"축구\"로 searchOpen을 호출하면") {
                val result = recruitmentCustomRepository.searchOpen(keyword = "축구", pageable = PageRequest.of(0, 20))

                Then("제목에 축구를 포함하는 모집 2건만 반환된다") {
                    result.totalElements shouldBe 2L
                    result.content.all { it.title.contains("축구") } shouldBe true
                }
            }
        }

        Given("keyword 없이 OPEN 모집 여럿이 있을 때") {
            saveRecruitment("주말 축구 모임")
            saveRecruitment("등산 동호회")

            When("searchOpen(keyword=null)을 호출하면") {
                val result = recruitmentCustomRepository.searchOpen(keyword = null, pageable = PageRequest.of(0, 20))

                Then("OPEN 모집 전체가 페이지네이션으로 조회된다") {
                    result.totalElements shouldBe 2L
                }
            }
        }

        Given("CLOSED·CANCELLED 모집이 섞여 있을 때") {
            val closed = saveRecruitment("정원 마감된 모집").apply { closeWhenFull(currentApplicantCount = 10) }
            recruitmentJpaRepository.save(closed)
            val cancelled = saveRecruitment("취소된 모집").apply { cancelByHost(userId = 1L) }
            recruitmentJpaRepository.save(cancelled)
            saveRecruitment("진행중인 모집")

            When("searchOpen(keyword=null)을 호출하면") {
                val result = recruitmentCustomRepository.searchOpen(keyword = null, pageable = PageRequest.of(0, 20))

                Then("OPEN 모집만 반환되고 CLOSED/CANCELLED는 제외된다 (상태 보호)") {
                    result.totalElements shouldBe 1L
                    result.content.first().title shouldBe "진행중인 모집"
                }
            }
        }

        Given("무료(feeAmount=0) 모집이 있을 때") {
            recruitmentJpaRepository.save(
                Recruitment.create(
                    title = "무료 봉사 모임",
                    capacity = 5,
                    feeAmount = BigDecimal.ZERO,
                    activityAt = activityAt,
                    applicationDeadline = activityAt.minusDays(1),
                    communityId = null,
                    recruiterUserId = 1L,
                ),
            )

            When("searchOpen(keyword=null)을 호출하면") {
                val result = recruitmentCustomRepository.searchOpen(keyword = null, pageable = PageRequest.of(0, 20))

                Then("무료 모집도 catalog 조회 대상에 포함된다 (엣지)") {
                    result.totalElements shouldBe 1L
                    result.content.first().title shouldBe "무료 봉사 모임"
                }
            }
        }

        Given("keyword와 일치하는 모집이 없을 때") {
            saveRecruitment("등산 동호회")

            When("keyword=\"존재하지않음\"으로 searchOpen을 호출하면") {
                val result = recruitmentCustomRepository.searchOpen(keyword = "존재하지않음", pageable = PageRequest.of(0, 20))

                Then("빈 페이지가 반환된다") {
                    result.totalElements shouldBe 0L
                    result.content.isEmpty() shouldBe true
                }
            }
        }
    }
}
