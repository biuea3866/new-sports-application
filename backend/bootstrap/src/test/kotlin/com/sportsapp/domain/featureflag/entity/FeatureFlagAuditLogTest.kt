package com.sportsapp.domain.featureflag.entity

import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy
import com.sportsapp.domain.featureflag.vo.FeatureFlagSnapshot
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class FeatureFlagAuditLogTest : BehaviorSpec({

    fun snapshot(key: String = "demo.feature.hello"): FeatureFlagSnapshot = FeatureFlagSnapshot(
        key = key,
        type = FeatureFlagType.RELEASE,
        status = FeatureFlagStatus.ACTIVE,
        strategy = EvaluationStrategy.GlobalToggle(enabled = true),
        description = "demo",
    )

    Given("before 없이(CREATED) FeatureFlagAuditLog를 생성하면") {
        val log = FeatureFlagAuditLog.create(
            changeType = FeatureFlagChangeType.CREATED,
            actorUserId = 1L,
            before = null,
            after = snapshot(),
        )

        Then("flagKey는 after 스냅샷의 key로 채워진다") {
            log.flagKey shouldBe "demo.feature.hello"
        }

        Then("beforeSnapshot은 null이다") {
            log.beforeSnapshot shouldBe null
        }

        Then("changeType·actorUserId·afterSnapshot이 그대로 저장된다") {
            log.changeType shouldBe FeatureFlagChangeType.CREATED
            log.actorUserId shouldBe 1L
            log.afterSnapshot shouldBe snapshot()
        }
    }

    Given("before가 있는(UPDATED) FeatureFlagAuditLog를 생성하면") {
        val before = snapshot()
        val after = snapshot().copy(description = "updated description")
        val log = FeatureFlagAuditLog.create(
            changeType = FeatureFlagChangeType.UPDATED,
            actorUserId = 2L,
            before = before,
            after = after,
        )

        Then("beforeSnapshot·afterSnapshot이 각각 저장된다") {
            log.beforeSnapshot shouldBe before
            log.afterSnapshot shouldBe after
        }
    }
})
