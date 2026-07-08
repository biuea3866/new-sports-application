package com.sportsapp.application.featureflag.dto

import com.sportsapp.domain.featureflag.entity.FeatureFlagAuditLog
import com.sportsapp.domain.featureflag.entity.FeatureFlagChangeType
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy
import com.sportsapp.domain.featureflag.vo.FeatureFlagSnapshot
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class ListFeatureFlagAuditLogsResponseTest : BehaviorSpec({

    fun auditLog() = FeatureFlagAuditLog.create(
        changeType = FeatureFlagChangeType.CREATED,
        actorUserId = 1L,
        before = null,
        after = FeatureFlagSnapshot(
            key = "demo.feature.hello",
            type = FeatureFlagType.RELEASE,
            status = FeatureFlagStatus.ACTIVE,
            strategy = EvaluationStrategy.GlobalToggle(enabled = true),
            description = null,
        ),
    )

    Given("총 25건 중 size 10인 첫 페이지를 Page로 전달하면") {
        val pageable = PageRequest.of(0, 10)
        val content = List(10) { auditLog() }
        val page = PageImpl(content, pageable, 25)

        val response = ListFeatureFlagAuditLogsResponse.of(page)

        Then("totalElements 25·totalPages 3·pageNumber 0·pageSize 10으로 매핑된다") {
            response.content.size shouldBe 10
            response.totalElements shouldBe 25L
            response.totalPages shouldBe 3
            response.pageNumber shouldBe 0
            response.pageSize shouldBe 10
        }
    }

    Given("빈 Page를 전달하면") {
        val pageable = PageRequest.of(0, 20)
        val page = PageImpl(emptyList<FeatureFlagAuditLog>(), pageable, 0)

        val response = ListFeatureFlagAuditLogsResponse.of(page)

        Then("content는 빈 리스트이고 totalElements·totalPages는 0이다") {
            response.content.shouldBeEmpty()
            response.totalElements shouldBe 0L
            response.totalPages shouldBe 0
        }
    }
})
