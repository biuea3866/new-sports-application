package com.sportsapp.domain.payment.service

import com.sportsapp.domain.payment.dto.PaymentLivenessRow
import com.sportsapp.domain.payment.entity.PaymentStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime

/**
 * [PaymentLivenessClassifier] — 4차 재설계 판정 순수 함수의 5개 payment 상태 전수 검증 +
 * 5차 재설계(liveSince 최댓값 앵커) [classify] 검증.
 *
 * 핵심 회귀(F-A 타격): PENDING/FAILED는 live가 아니므로 빠른 TTL로 만료 허용돼야 한다.
 * PG prepare 실패 시 즉시 FAILED로 전이하므로([PaymentDomainService.applyPgResult]),
 * F-A(고립 예약) 케이스는 반드시 여기서 걸러져야 스위퍼가 정확히 타격한다.
 */
class PaymentLivenessClassifierTest : BehaviorSpec({

    Given("결제가 COMPLETED 상태일 때") {
        Then("live이고 settled이다 — 절대 만료 금지") {
            PaymentLivenessClassifier.isLive(PaymentStatus.COMPLETED) shouldBe true
            PaymentLivenessClassifier.isSettled(PaymentStatus.COMPLETED) shouldBe true
        }
    }

    Given("결제가 READY 상태일 때") {
        Then("live이지만 settled은 아니다 — 느린 TTL로만 만료 허용") {
            PaymentLivenessClassifier.isLive(PaymentStatus.READY) shouldBe true
            PaymentLivenessClassifier.isSettled(PaymentStatus.READY) shouldBe false
        }
    }

    Given("결제가 PENDING 상태일 때 (핵심 회귀 — F-A 타격 대상)") {
        Then("live도 settled도 아니다 — 빠른 TTL로 만료를 허용해야 한다") {
            PaymentLivenessClassifier.isLive(PaymentStatus.PENDING) shouldBe false
            PaymentLivenessClassifier.isSettled(PaymentStatus.PENDING) shouldBe false
        }
    }

    Given("결제가 FAILED 상태일 때 (핵심 회귀 — PG prepare 실패로 고립된 예약)") {
        Then("live도 settled도 아니다 — 빠른 TTL로 만료를 허용해야 한다") {
            PaymentLivenessClassifier.isLive(PaymentStatus.FAILED) shouldBe false
            PaymentLivenessClassifier.isSettled(PaymentStatus.FAILED) shouldBe false
        }
    }

    Given("결제가 CANCELLED 상태일 때") {
        Then("live도 settled도 아니다 — 빠른 TTL로 만료를 허용한다") {
            PaymentLivenessClassifier.isLive(PaymentStatus.CANCELLED) shouldBe false
            PaymentLivenessClassifier.isSettled(PaymentStatus.CANCELLED) shouldBe false
        }
    }

    Given("결제가 REFUNDED 상태일 때") {
        Then("live도 settled도 아니다 — 환불 완료로 방치된 예약 정리를 허용한다") {
            PaymentLivenessClassifier.isLive(PaymentStatus.REFUNDED) shouldBe false
            PaymentLivenessClassifier.isSettled(PaymentStatus.REFUNDED) shouldBe false
        }
    }

    Given("payment 행이 전혀 없을 때") {
        When("classify를 호출하면") {
            val result = PaymentLivenessClassifier.classify(emptyList())

            Then("liveSince·settledOrderIds 모두 비어있다") {
                result.liveSince shouldBe emptyMap()
                result.settledOrderIds shouldBe emptySet()
            }
        }
    }

    Given("한 주문에 READY payment가 1건만 있을 때") {
        val readyAt = ZonedDateTime.now().minusMinutes(20)
        val rows = listOf(PaymentLivenessRow(orderId = 1L, status = PaymentStatus.READY, createdAt = readyAt))

        When("classify를 호출하면") {
            val result = PaymentLivenessClassifier.classify(rows)

            Then("그 payment의 createdAt이 liveSince 앵커로 잡힌다") {
                result.liveSince shouldContainExactly mapOf(1L to readyAt)
                result.settledOrderIds shouldBe emptySet()
            }
        }
    }

    Given("한 주문에 오래된 READY payment와 방금 발급된 READY payment가 함께 있을 때 (5차 재설계 핵심 회귀 — 최댓값 앵커)") {
        val oldReadyAt = ZonedDateTime.now().minusDays(3)
        val recentReadyAt = ZonedDateTime.now().minusMinutes(1)
        val rows = listOf(
            PaymentLivenessRow(orderId = 1L, status = PaymentStatus.READY, createdAt = oldReadyAt),
            PaymentLivenessRow(orderId = 1L, status = PaymentStatus.READY, createdAt = recentReadyAt),
        )

        When("classify를 호출하면 (순서를 바꿔도 최댓값이 앵커여야 한다)") {
            val result = PaymentLivenessClassifier.classify(rows)
            val resultReversed = PaymentLivenessClassifier.classify(rows.reversed())

            Then("최근(recent) createdAt이 liveSince 앵커로 잡힌다 — 최솟값·임의 1건을 쓰면 오만료가 재발한다") {
                result.liveSince shouldContainExactly mapOf(1L to recentReadyAt)
                resultReversed.liveSince shouldContainExactly mapOf(1L to recentReadyAt)
            }
        }
    }

    Given("PENDING/FAILED/CANCELLED/REFUNDED payment만 있을 때 (F-A 타격 대상 — liveSince 없음)") {
        val now = ZonedDateTime.now()
        val rows = listOf(
            PaymentLivenessRow(orderId = 1L, status = PaymentStatus.PENDING, createdAt = now),
            PaymentLivenessRow(orderId = 2L, status = PaymentStatus.FAILED, createdAt = now),
            PaymentLivenessRow(orderId = 3L, status = PaymentStatus.CANCELLED, createdAt = now),
            PaymentLivenessRow(orderId = 4L, status = PaymentStatus.REFUNDED, createdAt = now),
        )

        When("classify를 호출하면") {
            val result = PaymentLivenessClassifier.classify(rows)

            Then("liveSince에 아무 orderId도 포함되지 않아 호출 컨텍스트가 빠른 TTL을 적용할 수 있다") {
                result.liveSince shouldBe emptyMap()
                result.settledOrderIds shouldBe emptySet()
            }
        }
    }

    Given("COMPLETED payment가 있을 때") {
        val paidAt = ZonedDateTime.now().minusMinutes(5)
        val rows = listOf(PaymentLivenessRow(orderId = 1L, status = PaymentStatus.COMPLETED, createdAt = paidAt))

        When("classify를 호출하면") {
            val result = PaymentLivenessClassifier.classify(rows)

            Then("liveSince·settledOrderIds 모두에 포함된다 (COMPLETED는 live이기도 하다)") {
                result.liveSince shouldContainExactly mapOf(1L to paidAt)
                result.settledOrderIds shouldBe setOf(1L)
            }
        }
    }

    Given("서로 다른 orderId의 payment가 섞여 있을 때 (그룹핑 정확성)") {
        val order1ReadyAt = ZonedDateTime.now().minusMinutes(30)
        val order2CompletedAt = ZonedDateTime.now().minusMinutes(10)
        val rows = listOf(
            PaymentLivenessRow(orderId = 1L, status = PaymentStatus.READY, createdAt = order1ReadyAt),
            PaymentLivenessRow(orderId = 2L, status = PaymentStatus.COMPLETED, createdAt = order2CompletedAt),
            PaymentLivenessRow(orderId = 3L, status = PaymentStatus.FAILED, createdAt = ZonedDateTime.now()),
        )

        When("classify를 호출하면") {
            val result = PaymentLivenessClassifier.classify(rows)

            Then("각 orderId가 자신의 앵커·settled 여부로만 독립적으로 판정된다") {
                result.liveSince shouldContainExactly mapOf(1L to order1ReadyAt, 2L to order2CompletedAt)
                result.settledOrderIds shouldBe setOf(2L)
            }
        }
    }
})
