package com.sportsapp.infrastructure.featureflag.mysql

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.featureflag.entity.FeatureFlagChangeType
import com.sportsapp.domain.featureflag.entity.FeatureFlagAuditLog
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.repository.FeatureFlagAuditLogRepository
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy
import com.sportsapp.domain.featureflag.vo.FeatureFlagSnapshot
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

class FeatureFlagAuditLogRepositoryImplTest(
    @Autowired private val featureFlagAuditLogRepository: FeatureFlagAuditLogRepository,
) : BaseJpaIntegrationTest() {

    private fun snapshotOf(key: String, enabled: Boolean): FeatureFlagSnapshot = FeatureFlagSnapshot(
        key = key,
        type = FeatureFlagType.RELEASE,
        status = FeatureFlagStatus.ACTIVE,
        strategy = EvaluationStrategy.GlobalToggle(enabled = enabled),
        description = null,
    )

    init {
        Given("동일 flagKey의 감사 로그 3건이 서로 다른 occurred_at으로 저장된 상황") {
            val flagKey = "demo.feature.audit-${System.nanoTime()}"

            val oldest = FeatureFlagAuditLog.create(
                changeType = FeatureFlagChangeType.CREATED,
                actorUserId = 1L,
                before = null,
                after = snapshotOf(flagKey, enabled = true),
            )
            val middle = FeatureFlagAuditLog.create(
                changeType = FeatureFlagChangeType.UPDATED,
                actorUserId = 1L,
                before = snapshotOf(flagKey, enabled = true),
                after = snapshotOf(flagKey, enabled = false),
            )
            val newest = FeatureFlagAuditLog.create(
                changeType = FeatureFlagChangeType.ARCHIVED,
                actorUserId = 1L,
                before = snapshotOf(flagKey, enabled = false),
                after = snapshotOf(flagKey, enabled = false),
            )
            featureFlagAuditLogRepository.save(oldest)
            Thread.sleep(5)
            featureFlagAuditLogRepository.save(middle)
            Thread.sleep(5)
            featureFlagAuditLogRepository.save(newest)

            When("findByFlagKey를 occurred_at 내림차순 페이징으로 조회하면") {
                val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "occurredAt"))
                val page = featureFlagAuditLogRepository.findByFlagKey(flagKey, pageable)

                Then("3건이 occurred_at 내림차순으로 반환되고 totalElements가 정확하다") {
                    page.totalElements shouldBe 3L
                    page.content shouldHaveSize 3
                    val occurredAts = page.content.map { it.occurredAt }
                    occurredAts shouldBe occurredAts.sortedDescending()
                    page.content.first().changeType shouldBe FeatureFlagChangeType.ARCHIVED
                    page.content.last().changeType shouldBe FeatureFlagChangeType.CREATED
                }
            }
        }
    }
}
