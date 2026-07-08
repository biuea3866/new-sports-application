package com.sportsapp.domain.community.entity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class CommunityBookingTest : BehaviorSpec({

    Given("유효한 communityId·slotId·linkedByUserId") {
        When("create 를 호출하면") {
            val booking = CommunityBooking.create(
                communityId = 1L,
                slotId = 10L,
                linkedByUserId = 100L,
            )

            Then("필드가 그대로 설정된 CommunityBooking 이 생성된다") {
                booking.communityId shouldBe 1L
                booking.slotId shouldBe 10L
                booking.linkedByUserId shouldBe 100L
            }
        }
    }

    Given("communityId 가 0 이하인 경우") {
        When("create 를 호출하면") {
            Then("IllegalArgumentException 이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    CommunityBooking.create(communityId = 0L, slotId = 10L, linkedByUserId = 100L)
                }
            }
        }
    }

    Given("slotId 가 0 이하인 경우") {
        When("create 를 호출하면") {
            Then("IllegalArgumentException 이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    CommunityBooking.create(communityId = 1L, slotId = 0L, linkedByUserId = 100L)
                }
            }
        }
    }

    Given("영속화 계층 복원") {
        When("reconstitute 를 호출하면") {
            val booking = CommunityBooking.reconstitute(
                communityId = 2L,
                slotId = 20L,
                linkedByUserId = 200L,
            )

            Then("검증 없이 필드가 그대로 복구된다") {
                booking.communityId shouldBe 2L
                booking.slotId shouldBe 20L
                booking.linkedByUserId shouldBe 200L
            }
        }
    }
})
