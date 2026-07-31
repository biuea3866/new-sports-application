package com.sportsapp.application.recruitment.usecase

import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

/**
 * recruitment.expiry.enabled 운영 킬 스위치 판정 — [ApplicationExpiryScheduler]가 매 주기
 * 호출해 스위퍼 실행 여부를 결정한다. 부팅 시 고정되는 `@ConfigurationProperties`가 아니라
 * [RecruitmentDomainService.isExpiryEnabled]를 거쳐 `FeatureFlagEvaluator`로 매 주기 런타임
 * 조회한다(no-conditional-on-property).
 */
class IsRecruitmentExpiryEnabledUseCaseTest : BehaviorSpec({

    Given("플래그가 ON일 때") {
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val useCase = IsRecruitmentExpiryEnabledUseCase(recruitmentDomainService)
        every { recruitmentDomainService.isExpiryEnabled() } returns true

        When("execute를 호출하면") {
            Then("true를 반환한다") {
                useCase.execute() shouldBe true
            }
        }
    }

    Given("플래그가 OFF일 때") {
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val useCase = IsRecruitmentExpiryEnabledUseCase(recruitmentDomainService)
        every { recruitmentDomainService.isExpiryEnabled() } returns false

        When("execute를 호출하면") {
            Then("false를 반환한다") {
                useCase.execute() shouldBe false
            }
        }
    }
})
