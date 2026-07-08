package com.sportsapp.application.featureflag.dto

import com.sportsapp.domain.featureflag.entity.FeatureFlagAuditLog
import com.sportsapp.domain.featureflag.entity.FeatureFlagChangeType
import com.sportsapp.domain.featureflag.vo.FeatureFlagSnapshot
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class FeatureFlagAuditLogResponseTest : BehaviorSpec({

    fun snapshot(enabled: Boolean) = FeatureFlagSnapshot(
        key = "demo.feature.hello",
        type = FeatureFlagType.RELEASE,
        status = FeatureFlagStatus.ACTIVE,
        strategy = EvaluationStrategy.GlobalToggle(enabled = enabled),
        description = "demo flag",
    )

    Given("before가 없는 CREATED 감사 로그로 응답을 생성하면") {
        val auditLog = FeatureFlagAuditLog.create(
            changeType = FeatureFlagChangeType.CREATED,
            actorUserId = 1L,
            before = null,
            after = snapshot(enabled = true),
        )
        val response = FeatureFlagAuditLogResponse.of(auditLog)

        Then("before는 null, after는 스냅샷 값 그대로 매핑된다") {
            response.changeType shouldBe FeatureFlagChangeType.CREATED
            response.actorUserId shouldBe 1L
            response.before.shouldBeNull()
            response.after shouldBe snapshot(enabled = true)
        }
    }

    Given("before·after가 모두 있는 UPDATED 감사 로그로 응답을 생성하면") {
        val auditLog = FeatureFlagAuditLog.create(
            changeType = FeatureFlagChangeType.UPDATED,
            actorUserId = 2L,
            before = snapshot(enabled = true),
            after = snapshot(enabled = false),
        )
        val response = FeatureFlagAuditLogResponse.of(auditLog)

        Then("before·after가 각각 수정 전·후 스냅샷으로 매핑된다") {
            response.before shouldBe snapshot(enabled = true)
            response.after shouldBe snapshot(enabled = false)
        }
    }
})
