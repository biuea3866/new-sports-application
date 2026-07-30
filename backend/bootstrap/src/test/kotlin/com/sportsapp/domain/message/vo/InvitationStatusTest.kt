package com.sportsapp.domain.message.vo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class InvitationStatusTest : BehaviorSpec({

    Given("InvitationStatus 값 집합") {
        When("전체 값을 조회하면") {
            val values = InvitationStatus.entries.map { it.name }

            Then("TDD 상태 전이 표의 5개 값과 정확히 일치한다") {
                values shouldContainExactlyInAnyOrder listOf(
                    "PENDING",
                    "ACCEPTED",
                    "REJECTED",
                    "REVOKED",
                    "EXPIRED",
                )
            }
        }
    }

    Given("PENDING 상태의 초대") {
        When("ACCEPTED로 전이 가능 여부를 물으면") {
            Then("true를 반환한다") {
                InvitationStatus.PENDING.canTransitTo(InvitationStatus.ACCEPTED) shouldBe true
            }
        }

        When("REJECTED로 전이 가능 여부를 물으면") {
            Then("true를 반환한다") {
                InvitationStatus.PENDING.canTransitTo(InvitationStatus.REJECTED) shouldBe true
            }
        }

        When("REVOKED로 전이 가능 여부를 물으면") {
            Then("true를 반환한다") {
                InvitationStatus.PENDING.canTransitTo(InvitationStatus.REVOKED) shouldBe true
            }
        }

        When("EXPIRED로 전이 가능 여부를 물으면") {
            Then("true를 반환한다") {
                InvitationStatus.PENDING.canTransitTo(InvitationStatus.EXPIRED) shouldBe true
            }
        }

        When("PENDING 자신으로 전이 가능 여부를 물으면") {
            Then("false를 반환한다 (무의미한 자기 전이 거부)") {
                InvitationStatus.PENDING.canTransitTo(InvitationStatus.PENDING) shouldBe false
            }
        }
    }

    Given("이미 종료된(terminal) 상태의 초대") {
        When("ACCEPTED에서 REJECTED로 전이 가능 여부를 물으면") {
            Then("false를 반환한다") {
                InvitationStatus.ACCEPTED.canTransitTo(InvitationStatus.REJECTED) shouldBe false
            }
        }

        When("REJECTED에서 ACCEPTED로 전이 가능 여부를 물으면") {
            Then("false를 반환한다") {
                InvitationStatus.REJECTED.canTransitTo(InvitationStatus.ACCEPTED) shouldBe false
            }
        }

        When("REVOKED에서 ACCEPTED로 전이 가능 여부를 물으면") {
            Then("false를 반환한다") {
                InvitationStatus.REVOKED.canTransitTo(InvitationStatus.ACCEPTED) shouldBe false
            }
        }

        When("EXPIRED에서 ACCEPTED로 전이 가능 여부를 물으면") {
            Then("false를 반환한다") {
                InvitationStatus.EXPIRED.canTransitTo(InvitationStatus.ACCEPTED) shouldBe false
            }
        }
    }
})
