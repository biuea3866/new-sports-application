package com.sportsapp.infrastructure.recruitment.mysql

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.repository.RecruitmentRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate

/**
 * 소프트 삭제된 모집이 조회 경로로 새지 않는지 검증한다.
 *
 * [com.sportsapp.domain.common.JpaAuditingBase]의 정책은 "모든 Repository 조회는 기본으로
 * `WHERE deleted_at IS NULL` 필터"인데, 모집 조회 3경로(목록·단건·비관적 락)가 이 필터를
 * 빠뜨려 삭제된 모집이 목록에 노출되고 신청까지 가능했다.
 * (post 도메인은 `PostJpaRepository#findByIdAndDeletedAtIsNull`로 정상 적용 중 — 대조군)
 *
 * Given 블록마다 단일 When/Then만 둔다 — 형제 When이 여럿이면 afterEach가 테이블을 비워
 * 이후 형제가 Given 본문 데이터를 잃는다(ApplicationCustomRepositoryImplTest 주석 참조).
 * findForUpdateById(@Lock PESSIMISTIC_WRITE)는 활성 트랜잭션이 필요해 transactionTemplate로 감싼다
 * (선례: RecruitmentRepositoryImplTest).
 */
class RecruitmentRepositoryImplSoftDeleteTest(
    @Autowired private val recruitmentRepository: RecruitmentRepository,
    @Autowired private val recruitmentJpaRepository: RecruitmentJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val transactionTemplate: TransactionTemplate,
) : BaseJpaIntegrationTest() {

    private val activityAt = ZonedDateTime.now().plusDays(30)

    private fun saveRecruitment(title: String, communityId: Long? = null): Recruitment =
        recruitmentJpaRepository.save(
            Recruitment.create(
                title = title,
                capacity = 10,
                feeAmount = BigDecimal("10000"),
                activityAt = activityAt,
                applicationDeadline = activityAt.minusDays(1),
                communityId = communityId,
                recruiterUserId = 1L,
            ),
        )

    /** 운영에서 실제로 발생한 상태(코드 경로 없이 DB에 deleted_at이 찍힌 행)를 그대로 재현한다. */
    private fun softDelete(recruitmentId: Long) {
        jdbcTemplate.update("UPDATE recruitments SET deleted_at = NOW(6) WHERE id = ?", recruitmentId)
    }

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM recruitments")
        }

        Given("삭제된 모집과 살아 있는 모집이 섞여 있을 때") {
            val live = saveRecruitment("성수 볼더링 초보 클래스")
            val deleted = saveRecruitment("QA재검증 유료모집")
            softDelete(deleted.id)

            When("전체 목록을 조회하면") {
                val result = recruitmentRepository.findAll(null)

                Then("삭제된 모집은 목록에서 제외된다") {
                    result shouldHaveSize 1
                    result[0].id shouldBe live.id
                }
            }
        }

        Given("특정 모임에 삭제된 모집과 살아 있는 모집이 섞여 있을 때") {
            val live = saveRecruitment("화요일 저녁 배드민턴 복식 모집", communityId = 7L)
            val deleted = saveRecruitment("테스트 모집", communityId = 7L)
            softDelete(deleted.id)

            When("모임 ID로 목록을 조회하면") {
                val result = recruitmentRepository.findAll(7L)

                Then("삭제된 모집은 목록에서 제외된다") {
                    result shouldHaveSize 1
                    result[0].id shouldBe live.id
                }
            }
        }

        Given("삭제된 모집") {
            val deleted = saveRecruitment("QA 취소검증 모집")
            softDelete(deleted.id)

            When("단건으로 조회하면") {
                val result = recruitmentRepository.findById(deleted.id)

                Then("없는 것으로 취급한다") {
                    result shouldBe null
                }
            }
        }

        Given("삭제된 모집에 신청 시도") {
            val deleted = saveRecruitment("QA 유료 풋살 모집")
            softDelete(deleted.id)

            When("신청 경로가 쓰는 비관적 락 조회를 하면") {
                val result = transactionTemplate.execute {
                    recruitmentRepository.findForUpdateById(deleted.id)
                }

                Then("없는 것으로 취급해 신청을 막는다") {
                    result shouldBe null
                }
            }
        }

        Given("살아 있는 모집") {
            val live = saveRecruitment("새벽 한강 10K 페이스 러닝")

            When("단건으로 조회하면") {
                val result = recruitmentRepository.findById(live.id)

                Then("정상 조회된다") {
                    result?.id shouldBe live.id
                }
            }
        }
    }
}
