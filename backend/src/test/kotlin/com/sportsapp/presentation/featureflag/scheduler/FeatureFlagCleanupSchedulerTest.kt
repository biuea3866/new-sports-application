package com.sportsapp.presentation.featureflag.scheduler

import com.sportsapp.application.featureflag.dto.FeatureFlagResponse
import com.sportsapp.application.featureflag.testFeatureFlag
import com.sportsapp.application.featureflag.usecase.DetectStaleFeatureFlagsUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.scheduling.annotation.Scheduled
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions

class FeatureFlagCleanupSchedulerTest : BehaviorSpec({

    Given("90일 이전 변경된 정리 후보가 존재하는 상황") {
        val detectStaleFeatureFlagsUseCase = mockk<DetectStaleFeatureFlagsUseCase>()
        val meterRegistry = SimpleMeterRegistry()
        val scheduler = FeatureFlagCleanupScheduler(detectStaleFeatureFlagsUseCase, meterRegistry)
        val candidates = listOf(
            FeatureFlagResponse.of(testFeatureFlag(flagKey = "demo.feature.stale-a")),
            FeatureFlagResponse.of(testFeatureFlag(flagKey = "demo.feature.stale-b")),
        )
        every { detectStaleFeatureFlagsUseCase.execute() } returns candidates

        When("detectStaleCandidates를 호출하면") {
            scheduler.detectStaleCandidates()

            Then("DetectStaleFeatureFlagsUseCase를 1회 위임 호출한다") {
                verify(exactly = 1) { detectStaleFeatureFlagsUseCase.execute() }
            }

            Then("feature_flag_stale_candidates_total 카운터가 후보 수만큼 증가한다") {
                meterRegistry.counter("feature_flag_stale_candidates_total").count() shouldBe 2.0
            }
        }
    }

    Given("정리 후보가 0건인 상황") {
        val detectStaleFeatureFlagsUseCase = mockk<DetectStaleFeatureFlagsUseCase>()
        val meterRegistry = SimpleMeterRegistry()
        val scheduler = FeatureFlagCleanupScheduler(detectStaleFeatureFlagsUseCase, meterRegistry)
        every { detectStaleFeatureFlagsUseCase.execute() } returns emptyList()

        When("detectStaleCandidates를 호출하면") {
            scheduler.detectStaleCandidates()

            Then("통지(카운터 등록)를 발생시키지 않는다") {
                meterRegistry.find("feature_flag_stale_candidates_total").counter().shouldBeNull()
            }
        }
    }

    Given("FeatureFlagCleanupScheduler의 스케줄 설정") {
        When("detectStaleCandidates 메서드의 @Scheduled 어노테이션을 조회하면") {
            val scheduled = FeatureFlagCleanupScheduler::class.memberFunctions
                .first { it.name == "detectStaleCandidates" }
                .findAnnotation<Scheduled>()

            Then("cron 표현식이 설정되어 일 1회 주기로 발화한다") {
                scheduled.shouldNotBeNull()
                scheduled.cron.isNotBlank() shouldBe true
            }
        }
    }
})
