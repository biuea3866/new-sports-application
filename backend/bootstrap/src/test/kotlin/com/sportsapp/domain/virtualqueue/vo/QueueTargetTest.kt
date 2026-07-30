package com.sportsapp.domain.virtualqueue.vo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * `QueueTarget` — type+targetId로 Redis 키 접두를 캡슐화한다(no-getter-chain-behavior).
 * 키 계약 SSOT: `backend/docs/redis/virtual-queue-keys.md`.
 */
class QueueTargetTest : BehaviorSpec({

    Given("LIMITED_DROP 타입, targetId=501인 QueueTarget") {
        val target = QueueTarget(type = QueueTargetType.LIMITED_DROP, targetId = 501L)

        When("keyPrefix를 호출하면") {
            val prefix = target.keyPrefix()

            Then("queue:limited-drop:501을 반환한다") {
                prefix shouldBe "queue:limited-drop:501"
            }
        }

        When("역할별 세부 키를 조회하면") {
            Then("접두에 역할별 접미가 붙는다") {
                target.waitingKey() shouldBe "queue:limited-drop:501:waiting"
                target.heartbeatKey() shouldBe "queue:limited-drop:501:heartbeat"
                target.seqKey() shouldBe "queue:limited-drop:501:seq"
                target.admittedCountKey() shouldBe "queue:limited-drop:501:admitted_count"
                target.tokenKey(8842L) shouldBe "queue:limited-drop:501:token:8842"
                target.admissionLockKey() shouldBe "queue:admission:limited-drop:501"
                target.activeMember() shouldBe "limited-drop:501"
            }
        }
    }

    Given("TICKETING_EVENT 타입, targetId=77인 QueueTarget") {
        val target = QueueTarget(type = QueueTargetType.TICKETING_EVENT, targetId = 77L)

        When("keyPrefix를 호출하면") {
            val prefix = target.keyPrefix()

            Then("queue:ticketing-event:77을 반환한다") {
                prefix shouldBe "queue:ticketing-event:77"
            }
        }
    }

    Given("activeMember 문자열 \"limited-drop:501\"이 주어지면 (BE-02 activeTargets 파싱)") {
        When("fromActiveMember를 호출하면") {
            val target = QueueTarget.fromActiveMember("limited-drop:501")

            Then("동일한 type·targetId의 QueueTarget으로 역변환된다") {
                target shouldBe QueueTarget(type = QueueTargetType.LIMITED_DROP, targetId = 501L)
            }
        }
    }

    Given("activeMember 문자열 \"ticketing-event:77\"이 주어지면") {
        When("fromActiveMember를 호출하면") {
            val target = QueueTarget.fromActiveMember("ticketing-event:77")

            Then("동일한 type·targetId의 QueueTarget으로 역변환된다") {
                target shouldBe QueueTarget(type = QueueTargetType.TICKETING_EVENT, targetId = 77L)
            }
        }
    }
})
