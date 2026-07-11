package com.sportsapp.domain.virtualqueue.vo

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * `QueueTargetType.fromSlug` — `queue:active` member(`{slug}:{targetId}`) 역파싱에 쓰이는
 * slug → enum 역변환(BE-02 `VirtualQueueStoreImpl.activeTargets`).
 */
class QueueTargetTypeTest : BehaviorSpec({

    Given("slug \"limited-drop\"이 주어지면") {
        When("fromSlug를 호출하면") {
            val type = QueueTargetType.fromSlug("limited-drop")

            Then("LIMITED_DROP을 반환한다") {
                type shouldBe QueueTargetType.LIMITED_DROP
            }
        }
    }

    Given("slug \"ticketing-event\"가 주어지면") {
        When("fromSlug를 호출하면") {
            val type = QueueTargetType.fromSlug("ticketing-event")

            Then("TICKETING_EVENT를 반환한다") {
                type shouldBe QueueTargetType.TICKETING_EVENT
            }
        }
    }

    Given("알 수 없는 slug가 주어지면") {
        When("fromSlug를 호출하면") {
            Then("IllegalArgumentException을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    QueueTargetType.fromSlug("unknown-slug")
                }
            }
        }
    }
})
