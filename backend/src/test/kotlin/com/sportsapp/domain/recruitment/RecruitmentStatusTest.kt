package com.sportsapp.domain.recruitment

import com.sportsapp.domain.recruitment.vo.RecruitmentStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class RecruitmentStatusTest : BehaviorSpec({

    Given("RecruitmentStatus 전이 규칙") {
        Then("OPEN은 CLOSED와 CANCELLED로 전이 가능하다") {
            RecruitmentStatus.OPEN.canTransitTo(RecruitmentStatus.CLOSED) shouldBe true
            RecruitmentStatus.OPEN.canTransitTo(RecruitmentStatus.CANCELLED) shouldBe true
        }

        Then("CLOSED는 CANCELLED로만 전이 가능하다") {
            RecruitmentStatus.CLOSED.canTransitTo(RecruitmentStatus.CANCELLED) shouldBe true
            RecruitmentStatus.CLOSED.canTransitTo(RecruitmentStatus.OPEN) shouldBe false
        }

        Then("CANCELLED는 어떤 상태로도 전이할 수 없다") {
            RecruitmentStatus.CANCELLED.canTransitTo(RecruitmentStatus.OPEN) shouldBe false
            RecruitmentStatus.CANCELLED.canTransitTo(RecruitmentStatus.CLOSED) shouldBe false
            RecruitmentStatus.CANCELLED.canTransitTo(RecruitmentStatus.CANCELLED) shouldBe false
        }
    }
})
