package com.sportsapp.domain.virtualqueue.vo

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * `QueuePosition.of` — admission 판정(고정 seq 기준)과 표시용 aheadCount·etaSeconds 계산 검증.
 *
 * redis-contract.md §0-1 회귀: 앞선 사용자가 leave로 빠져 rank가 붕괴해도(0으로),
 * 고정 시퀀스(seq)로 판정하므로 연쇄 admission이 발생하지 않아야 한다.
 */
class QueuePositionTest : BehaviorSpec({

    Given("rank=99, seq=100, admittedCount=100인 상태에서") {
        When("QueuePosition.of를 호출하면") {
            val position = QueuePosition.of(rank = 99, seq = 100, admittedCount = 100, batchSize = 100, tickSeconds = 2)

            Then("admitted는 true다 (seq<=admittedCount)") {
                position.admitted shouldBe true
            }
        }
    }

    Given("앞선 사용자가 leave로 빠져 rank는 0이지만 seq=4, admittedCount=3인 상태에서 (§0-1 회귀)") {
        When("QueuePosition.of를 호출하면") {
            val position = QueuePosition.of(rank = 0, seq = 4, admittedCount = 3, batchSize = 100, tickSeconds = 2)

            Then("admitted는 false다 — rank가 아니라 고정 seq로 판정한다") {
                position.admitted shouldBe false
            }
        }
    }

    Given("rank=150, seq=250, admittedCount=100, batchSize=100, tickSeconds=2인 상태에서") {
        When("QueuePosition.of를 호출하면") {
            val position = QueuePosition.of(rank = 150, seq = 250, admittedCount = 100, batchSize = 100, tickSeconds = 2)

            Then("aheadCount는 150, etaSeconds는 4다") {
                position.aheadCount shouldBe 150L
                position.etaSeconds shouldBe 4L
            }
        }
    }

    Given("이미 admission된 상태(seq<=admittedCount)에서") {
        When("QueuePosition.of를 호출하면") {
            val position = QueuePosition.of(rank = 0, seq = 50, admittedCount = 100, batchSize = 100, tickSeconds = 2)

            Then("etaSeconds는 0이다 — 더 이상 대기하지 않는다") {
                position.etaSeconds shouldBe 0L
            }
        }
    }

    Given("batchSize가 0 이하로 주어지면") {
        When("QueuePosition.of를 호출하면") {
            Then("IllegalArgumentException을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    QueuePosition.of(rank = 0, seq = 1, admittedCount = 0, batchSize = 0, tickSeconds = 2)
                }
            }
        }
    }
})
