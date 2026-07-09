package com.sportsapp.domain.virtualqueue.vo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime

/** `QueueStatus` — WAITING/ADMITTED/DIRECT_ADMITTED 조합을 캡슐화하는 도메인 표현. */
class QueueStatusTest : BehaviorSpec({

    Given("대기 중인 사용자의 QueuePosition이 주어지면") {
        val position = QueuePosition.of(rank = 10, seq = 20, admittedCount = 5, batchSize = 5, tickSeconds = 2)

        When("QueueStatus.waiting을 호출하면") {
            val status = QueueStatus.waiting(position)

            Then("state는 WAITING이고 position을 보유하며 entryToken은 없다") {
                status.state shouldBe QueueEntryState.WAITING
                status.position shouldBe position
                status.entryToken.shouldBeNull()
            }
        }
    }

    Given("admission된 사용자의 EntryToken이 주어지면") {
        val token = EntryToken(raw = "raw", expiresAt = ZonedDateTime.now().plusMinutes(5))

        When("QueueStatus.admitted를 호출하면") {
            val status = QueueStatus.admitted(token)

            Then("state는 ADMITTED이고 entryToken을 보유하며 position은 없다") {
                status.state shouldBe QueueEntryState.ADMITTED
                status.entryToken shouldBe token
                status.position.shouldBeNull()
            }
        }
    }

    Given("피처 플래그 OFF로 즉시 통과하는 사용자의 EntryToken이 주어지면") {
        val token = EntryToken(raw = "raw", expiresAt = ZonedDateTime.now().plusMinutes(5))

        When("QueueStatus.directEntry를 호출하면") {
            val status = QueueStatus.directEntry(token)

            Then("state는 DIRECT_ADMITTED이고 entryToken을 보유한다") {
                status.state shouldBe QueueEntryState.DIRECT_ADMITTED
                status.entryToken shouldBe token
                status.position.shouldBeNull()
            }
        }
    }
})
