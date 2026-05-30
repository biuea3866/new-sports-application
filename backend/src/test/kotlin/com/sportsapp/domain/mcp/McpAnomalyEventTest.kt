package com.sportsapp.domain.mcp

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.ZonedDateTime

class McpAnomalyEventTest : BehaviorSpec({

    fun createOpenEvent(ownerUserId: Long = 10L): McpAnomalyEvent = McpAnomalyEvent(
        sourceEventId = "evt-test-${System.nanoTime()}",
        tokenId = 1L,
        ownerUserId = ownerUserId,
        detectedAt = ZonedDateTime.now(),
        currentHourCount = 200L,
        baselineAverage = 50.0,
    )

    Given("신규 McpAnomalyEvent") {
        val event = createOpenEvent()

        Then("[U-01] status=OPEN, falsePositive=false 로 생성된다") {
            event.status shouldBe McpAnomalyEventStatus.OPEN
            event.falsePositive shouldBe false
        }
    }

    Given("OPEN 상태의 McpAnomalyEvent") {
        val event = createOpenEvent()

        When("markFalsePositive()를 호출하면") {
            event.markFalsePositive(userId = 10L, noteText = "정상 배치 실행")

            Then("[U-02] status=FALSE_POSITIVE, falsePositive=true, resolvedAt이 채워진다") {
                event.status shouldBe McpAnomalyEventStatus.FALSE_POSITIVE
                event.falsePositive shouldBe true
                event.resolvedAt shouldNotBe null
                event.resolvedBy shouldBe 10L
                event.note shouldBe "정상 배치 실행"
            }
        }
    }

    Given("OPEN 상태의 McpAnomalyEvent") {
        val event = createOpenEvent()

        When("resolve()를 호출하면") {
            event.resolve(userId = 10L, noteText = "확인 후 조치 완료")

            Then("[U-03] status=RESOLVED로 전이된다") {
                event.status shouldBe McpAnomalyEventStatus.RESOLVED
                event.resolvedAt shouldNotBe null
                event.note shouldBe "확인 후 조치 완료"
            }
        }
    }

    Given("RESOLVED 상태의 McpAnomalyEvent") {
        val event = createOpenEvent()
        event.resolve(userId = 10L, noteText = null)

        When("다시 markFalsePositive()를 호출하면") {
            Then("[U-04] 이미 RESOLVED이므로 IllegalStateException이 발생한다") {
                shouldThrow<IllegalStateException> {
                    event.markFalsePositive(userId = 10L, noteText = null)
                }
            }
        }
    }

    Given("FALSE_POSITIVE 상태의 McpAnomalyEvent") {
        val event = createOpenEvent()
        event.markFalsePositive(userId = 10L, noteText = null)

        When("다시 resolve()를 호출하면") {
            Then("[U-04] 이미 FALSE_POSITIVE이므로 IllegalStateException이 발생한다") {
                shouldThrow<IllegalStateException> {
                    event.resolve(userId = 10L, noteText = null)
                }
            }
        }
    }

    Given("ownerUserId=10인 McpAnomalyEvent") {
        val event = createOpenEvent(ownerUserId = 10L)

        When("requireOwnedBy(userId=99)를 호출하면") {
            Then("[U-05] McpAnomalyEventNotOwnedException이 발생한다 (IDOR 차단)") {
                shouldThrow<McpAnomalyEventNotOwnedException> {
                    event.requireOwnedBy(99L)
                }
            }
        }

        When("requireOwnedBy(userId=10)를 호출하면") {
            Then("[U-05] 예외 없이 통과한다") {
                event.requireOwnedBy(10L)
            }
        }
    }

    Given("McpAnomalyDetectedEvent로 McpAnomalyEvent.of() 팩토리 호출") {
        val domainEvent = McpAnomalyDetectedEvent(
            tokenId = 5L,
            userId = 20L,
            currentHourCount = 300L,
            baselineAverage = 80.0,
        )

        val anomalyEvent = McpAnomalyEvent.of(domainEvent)

        Then("[U-06] 이벤트 필드가 정확히 매핑된다") {
            anomalyEvent.sourceEventId shouldBe domainEvent.eventId
            anomalyEvent.tokenId shouldBe 5L
            anomalyEvent.ownerUserId shouldBe 20L
            anomalyEvent.currentHourCount shouldBe 300L
            anomalyEvent.baselineAverage shouldBe 80.0
            anomalyEvent.status shouldBe McpAnomalyEventStatus.OPEN
        }
    }
})
