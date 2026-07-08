package com.sportsapp.domain.community.vo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class MembershipStatusTest : BehaviorSpec({

    Given("MembershipStatus 값 집합") {
        When("전체 값을 조회하면") {
            val values = MembershipStatus.entries.map { it.name }

            Then("TDD 상태 전이 표의 4개 값과 정확히 일치한다") {
                values shouldContainExactlyInAnyOrder listOf(
                    "ACTIVE",
                    "PENDING_APPROVAL",
                    "LEFT",
                    "KICKED",
                )
            }
        }
    }

    Given("PENDING_APPROVAL 상태의 멤버십") {
        When("ACTIVE로 전이 가능 여부를 물으면") {
            Then("true를 반환한다 (방장 승인)") {
                MembershipStatus.PENDING_APPROVAL.canTransitTo(MembershipStatus.ACTIVE) shouldBe true
            }
        }

        When("KICKED로 전이 가능 여부를 물으면") {
            Then("false를 반환한다 (승인 전 추방은 정의되지 않음)") {
                MembershipStatus.PENDING_APPROVAL.canTransitTo(MembershipStatus.KICKED) shouldBe false
            }
        }
    }

    Given("ACTIVE 상태의 멤버십") {
        When("LEFT로 전이 가능 여부를 물으면") {
            Then("true를 반환한다 (자진 탈퇴)") {
                MembershipStatus.ACTIVE.canTransitTo(MembershipStatus.LEFT) shouldBe true
            }
        }

        When("KICKED로 전이 가능 여부를 물으면") {
            Then("true를 반환한다 (방장 추방)") {
                MembershipStatus.ACTIVE.canTransitTo(MembershipStatus.KICKED) shouldBe true
            }
        }

        When("PENDING_APPROVAL로 전이 가능 여부를 물으면") {
            Then("false를 반환한다 (역행 금지)") {
                MembershipStatus.ACTIVE.canTransitTo(MembershipStatus.PENDING_APPROVAL) shouldBe false
            }
        }
    }

    Given("LEFT(탈퇴)한 멤버십") {
        When("ACTIVE로 전이 가능 여부를 물으면") {
            Then("true를 반환한다 (공개 커뮤니티 재가입 즉시 재활성화, 리뷰 p2-①)") {
                MembershipStatus.LEFT.canTransitTo(MembershipStatus.ACTIVE) shouldBe true
            }
        }

        When("PENDING_APPROVAL로 전이 가능 여부를 물으면") {
            Then("true를 반환한다 (비공개 커뮤니티 재가입은 다시 승인 대기)") {
                MembershipStatus.LEFT.canTransitTo(MembershipStatus.PENDING_APPROVAL) shouldBe true
            }
        }

        When("KICKED로 전이 가능 여부를 물으면") {
            Then("false를 반환한다 (탈퇴 상태에서 직접 강퇴로는 전이하지 않음)") {
                MembershipStatus.LEFT.canTransitTo(MembershipStatus.KICKED) shouldBe false
            }
        }
    }

    Given("KICKED(강퇴)된 멤버십") {
        When("ACTIVE로 전이 가능 여부를 물으면") {
            Then("true를 반환한다 (공개 커뮤니티 재가입 즉시 재활성화, 리뷰 p2-①)") {
                MembershipStatus.KICKED.canTransitTo(MembershipStatus.ACTIVE) shouldBe true
            }
        }

        When("PENDING_APPROVAL로 전이 가능 여부를 물으면") {
            Then("true를 반환한다 (비공개 커뮤니티 재가입은 다시 승인 대기)") {
                MembershipStatus.KICKED.canTransitTo(MembershipStatus.PENDING_APPROVAL) shouldBe true
            }
        }

        When("LEFT로 전이 가능 여부를 물으면") {
            Then("false를 반환한다 (강퇴 상태에서 직접 자진탈퇴로는 전이하지 않음)") {
                MembershipStatus.KICKED.canTransitTo(MembershipStatus.LEFT) shouldBe false
            }
        }
    }
})
