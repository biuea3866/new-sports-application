package com.sportsapp.domain.payment.service

import com.sportsapp.domain.common.payment.OrderPaymentLiveness
import com.sportsapp.domain.payment.dto.PaymentLivenessRow
import com.sportsapp.domain.payment.entity.PaymentStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.ZonedDateTime

/**
 * [PaymentLivenessClassifier] — 6차 재설계(공유 커널 [OrderPaymentLiveness] sealed 타입) 판정
 * 순수 함수의 5개 payment 상태 전수 검증 + classify() 그룹핑·앵커 산출 검증.
 *
 * 핵심 회귀(F-A 타격): PENDING/FAILED는 live가 아니므로 빠른 TTL로 만료 허용돼야 한다.
 * PG prepare 실패 시 즉시 FAILED로 전이하므로([PaymentDomainService.applyPgResult]),
 * F-A(고립 예약) 케이스는 반드시 여기서 걸러져야 스위퍼가 정확히 타격한다.
 *
 * 핵심 회귀(6차 — p1): PENDING만 있는 주문은 더 이상 [OrderPaymentLiveness.None]이 아니라
 * [OrderPaymentLiveness.Attempting]으로 분류돼 시도 시작 시각을 앵커로 넘긴다 — `createPending`이
 * PENDING 행을 먼저 커밋하고 PG 왕복 동안 그 상태로 머무는 창에서, 방금 시작된 재결제 시도를
 * 앵커 없이(=즉시 만료 허용) 판정하면 오만료가 재발한다.
 *
 * 핵심 회귀(7차 → 8차 — p1): 7차는 live 행이 한 건이라도 있으면 `attempting` 갈래를 아예
 * 평가하지 않고 즉시 `Live`를 반환하던 카테고리 우선순위 결함을 "두 카테고리 최댓값의 교차
 * 비교"로 고쳤으나, 그 비교 자체가 "승자 하나 고르기"라 서로 다른 TTL로 소비되는 두 신호
 * 중 하나를 통째로 버리는 문제는 방향만 바뀌어 재발했다(8차). 이제 live 행이 있으면
 * `attempting` 최댓값을 버리지 않고 `Live.attemptSince`에 함께 담아 반환한다 — "승자 하나"가
 * 아니라 "두 앵커 모두 전달"로 검증한다.
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

    Given("결제가 PENDING 상태일 때 (6차 — Attempting 분류 대상)") {
        Then("live도 settled도 아니지만 attempting이다 — 시도 시각을 앵커로 재-앵커해야 한다") {
            PaymentLivenessClassifier.isLive(PaymentStatus.PENDING) shouldBe false
            PaymentLivenessClassifier.isSettled(PaymentStatus.PENDING) shouldBe false
            PaymentLivenessClassifier.isAttempting(PaymentStatus.PENDING) shouldBe true
        }
    }

    Given("결제가 FAILED 상태일 때 (핵심 회귀 — PG prepare 실패로 고립된 예약)") {
        Then("live도 settled도 attempting도 아니다 — 빠른 TTL(주문 생성 시각 기준)로 만료를 허용해야 한다") {
            PaymentLivenessClassifier.isLive(PaymentStatus.FAILED) shouldBe false
            PaymentLivenessClassifier.isSettled(PaymentStatus.FAILED) shouldBe false
            PaymentLivenessClassifier.isAttempting(PaymentStatus.FAILED) shouldBe false
        }
    }

    Given("결제가 CANCELLED 상태일 때") {
        Then("live도 settled도 attempting도 아니다 — 빠른 TTL로 만료를 허용한다") {
            PaymentLivenessClassifier.isLive(PaymentStatus.CANCELLED) shouldBe false
            PaymentLivenessClassifier.isSettled(PaymentStatus.CANCELLED) shouldBe false
            PaymentLivenessClassifier.isAttempting(PaymentStatus.CANCELLED) shouldBe false
        }
    }

    Given("결제가 REFUNDED 상태일 때") {
        Then("live도 settled도 attempting도 아니다 — 환불 완료로 방치된 예약 정리를 허용한다") {
            PaymentLivenessClassifier.isLive(PaymentStatus.REFUNDED) shouldBe false
            PaymentLivenessClassifier.isSettled(PaymentStatus.REFUNDED) shouldBe false
            PaymentLivenessClassifier.isAttempting(PaymentStatus.REFUNDED) shouldBe false
        }
    }

    Given("payment 행이 전혀 없을 때") {
        When("classify를 호출하면") {
            val result = PaymentLivenessClassifier.classify(emptyList())

            Then("livenessByOrderId가 비어있다") {
                result.livenessByOrderId shouldBe emptyMap()
            }
        }
    }

    Given("한 주문에 READY payment가 1건만 있을 때") {
        val readyAt = ZonedDateTime.now().minusMinutes(20)
        val rows = listOf(PaymentLivenessRow(orderId = 1L, status = PaymentStatus.READY, createdAt = readyAt))

        When("classify를 호출하면") {
            val result = PaymentLivenessClassifier.classify(rows)

            Then("Live(readyAt)로 분류된다") {
                val liveness = result.of(1L).shouldBeInstanceOf<OrderPaymentLiveness.Live>()
                liveness.since shouldBe readyAt
            }
        }
    }

    Given("한 주문에 오래된 READY payment와 방금 발급된 READY payment가 함께 있을 때 (최댓값 앵커 회귀)") {
        val oldReadyAt = ZonedDateTime.now().minusDays(3)
        val recentReadyAt = ZonedDateTime.now().minusMinutes(1)
        val rows = listOf(
            PaymentLivenessRow(orderId = 1L, status = PaymentStatus.READY, createdAt = oldReadyAt),
            PaymentLivenessRow(orderId = 1L, status = PaymentStatus.READY, createdAt = recentReadyAt),
        )

        When("classify를 호출하면 (순서를 바꿔도 최댓값이 앵커여야 한다)") {
            val result = PaymentLivenessClassifier.classify(rows)
            val resultReversed = PaymentLivenessClassifier.classify(rows.reversed())

            Then("최근(recent) createdAt이 Live의 since로 잡힌다 — 최솟값·임의 1건을 쓰면 오만료가 재발한다") {
                (result.of(1L) as OrderPaymentLiveness.Live).since shouldBe recentReadyAt
                (resultReversed.of(1L) as OrderPaymentLiveness.Live).since shouldBe recentReadyAt
            }
        }
    }

    Given("한 주문에 PENDING payment만 있을 때 (6차 핵심 회귀 — Attempting 분류)") {
        val attemptAt = ZonedDateTime.now().minusMinutes(1)
        val rows = listOf(PaymentLivenessRow(orderId = 5L, status = PaymentStatus.PENDING, createdAt = attemptAt))

        When("classify를 호출하면") {
            val result = PaymentLivenessClassifier.classify(rows)

            Then("Attempting(attemptAt)으로 분류된다 — None이 아니다") {
                val liveness = result.of(5L).shouldBeInstanceOf<OrderPaymentLiveness.Attempting>()
                liveness.since shouldBe attemptAt
            }
        }
    }

    Given("한 주문에 오래된 PENDING(원 시도)과 방금 삽입된 PENDING(재시도)이 함께 있을 때 (Attempting 최댓값 회귀)") {
        val originalAttemptAt = ZonedDateTime.now().minusMinutes(70)
        val retryAttemptAt = ZonedDateTime.now().minusSeconds(5)
        val rows = listOf(
            PaymentLivenessRow(orderId = 6L, status = PaymentStatus.PENDING, createdAt = originalAttemptAt),
            PaymentLivenessRow(orderId = 6L, status = PaymentStatus.PENDING, createdAt = retryAttemptAt),
        )

        When("classify를 호출하면") {
            val result = PaymentLivenessClassifier.classify(rows)

            Then("가장 최근 PENDING 삽입 시각이 Attempting의 since로 잡힌다") {
                (result.of(6L) as OrderPaymentLiveness.Attempting).since shouldBe retryAttemptAt
            }
        }
    }

    Given("PENDING과 FAILED가 함께 있을 때 (원 시도가 FAILED로 종결되고 재시도가 PENDING인 상황)") {
        val failedAt = ZonedDateTime.now().minusMinutes(70)
        val retryAttemptAt = ZonedDateTime.now().minusSeconds(5)
        val rows = listOf(
            PaymentLivenessRow(orderId = 7L, status = PaymentStatus.FAILED, createdAt = failedAt),
            PaymentLivenessRow(orderId = 7L, status = PaymentStatus.PENDING, createdAt = retryAttemptAt),
        )

        When("classify를 호출하면") {
            val result = PaymentLivenessClassifier.classify(rows)

            Then("FAILED는 무시되고 PENDING만 Attempting 앵커로 반영된다") {
                (result.of(7L) as OrderPaymentLiveness.Attempting).since shouldBe retryAttemptAt
            }
        }
    }

    Given("오래된 READY와 방금 삽입된 PENDING이 함께 있을 때 (8차 핵심 회귀 — 승자를 고르지 않고 양쪽 앵커를 모두 담는다)") {
        val staleReadyAt = ZonedDateTime.now().minusMinutes(70)
        val retryAttemptAt = ZonedDateTime.now().minusSeconds(5)
        val rows = listOf(
            PaymentLivenessRow(orderId = 9L, status = PaymentStatus.READY, createdAt = staleReadyAt),
            PaymentLivenessRow(orderId = 9L, status = PaymentStatus.PENDING, createdAt = retryAttemptAt),
        )

        When("classify를 호출하면 (READY와 더 최신인 PENDING이 함께 존재)") {
            val result = PaymentLivenessClassifier.classify(rows)

            Then("Live(since=staleReadyAt, attemptSince=retryAttemptAt)로 분류된다 — attempting이 통째로 버려지지 않는다") {
                val liveness = result.of(9L).shouldBeInstanceOf<OrderPaymentLiveness.Live>()
                liveness.since shouldBe staleReadyAt
                liveness.attemptSince shouldBe retryAttemptAt
            }
        }
    }

    Given("방금 발급된 READY와 오래된 PENDING(원 시도)이 함께 있을 때 (8차 회귀 — 반대 방향)") {
        val recentReadyAt = ZonedDateTime.now().minusMinutes(1)
        val originalAttemptAt = ZonedDateTime.now().minusMinutes(70)
        val rows = listOf(
            PaymentLivenessRow(orderId = 10L, status = PaymentStatus.PENDING, createdAt = originalAttemptAt),
            PaymentLivenessRow(orderId = 10L, status = PaymentStatus.READY, createdAt = recentReadyAt),
        )

        When("classify를 호출하면 (PENDING과 더 최신인 READY가 함께 존재)") {
            val result = PaymentLivenessClassifier.classify(rows)

            Then("Live(since=recentReadyAt, attemptSince=originalAttemptAt)로 분류된다") {
                val liveness = result.of(10L).shouldBeInstanceOf<OrderPaymentLiveness.Live>()
                liveness.since shouldBe recentReadyAt
                liveness.attemptSince shouldBe originalAttemptAt
            }
        }
    }

    Given("live payment가 없고 attempting만 있을 때 (Attempting은 live가 전혀 없을 때만 반환된다)") {
        val attemptAt = ZonedDateTime.now().minusSeconds(5)
        val rows = listOf(PaymentLivenessRow(orderId = 11L, status = PaymentStatus.PENDING, createdAt = attemptAt))

        When("classify를 호출하면") {
            val result = PaymentLivenessClassifier.classify(rows)

            Then("Attempting(attemptAt)으로 분류된다") {
                val liveness = result.of(11L).shouldBeInstanceOf<OrderPaymentLiveness.Attempting>()
                liveness.since shouldBe attemptAt
            }
        }
    }

    Given("FAILED/CANCELLED/REFUNDED payment만 있을 때 (F-A 타격 대상 — None)") {
        val now = ZonedDateTime.now()
        val rows = listOf(
            PaymentLivenessRow(orderId = 2L, status = PaymentStatus.FAILED, createdAt = now),
            PaymentLivenessRow(orderId = 3L, status = PaymentStatus.CANCELLED, createdAt = now),
            PaymentLivenessRow(orderId = 4L, status = PaymentStatus.REFUNDED, createdAt = now),
        )

        When("classify를 호출하면") {
            val result = PaymentLivenessClassifier.classify(rows)

            Then("모두 None으로 분류돼 호출 컨텍스트가 빠른 TTL(주문 생성 시각 기준)을 적용할 수 있다") {
                result.of(2L) shouldBe OrderPaymentLiveness.None
                result.of(3L) shouldBe OrderPaymentLiveness.None
                result.of(4L) shouldBe OrderPaymentLiveness.None
            }
        }
    }

    Given("조회 대상이 아닌 orderId를 조회할 때") {
        val result = PaymentLivenessClassifier.classify(emptyList())

        Then("None을 기본값으로 반환한다") {
            result.of(999L) shouldBe OrderPaymentLiveness.None
        }
    }

    Given("COMPLETED payment가 있을 때") {
        val paidAt = ZonedDateTime.now().minusMinutes(5)
        val rows = listOf(PaymentLivenessRow(orderId = 1L, status = PaymentStatus.COMPLETED, createdAt = paidAt))

        When("classify를 호출하면") {
            val result = PaymentLivenessClassifier.classify(rows)

            Then("Settled로 분류된다") {
                result.of(1L) shouldBe OrderPaymentLiveness.Settled
            }
        }
    }

    Given("한 주문에 COMPLETED와 시점상 더 오래된 READY가 함께 있을 때 (settled 우선 판정 — 핵심 회귀, p4)") {
        val staleReadyAt = ZonedDateTime.now().minusDays(3)
        val completedAt = ZonedDateTime.now().minusMinutes(2)
        val rows = listOf(
            PaymentLivenessRow(orderId = 8L, status = PaymentStatus.READY, createdAt = staleReadyAt),
            PaymentLivenessRow(orderId = 8L, status = PaymentStatus.COMPLETED, createdAt = completedAt),
        )

        When("classify를 호출하면 (READY가 먼저 나열돼도, COMPLETED가 나중에 나열돼도)") {
            val result = PaymentLivenessClassifier.classify(rows)
            val resultReversed = PaymentLivenessClassifier.classify(rows.reversed())

            Then("Settled 하나로만 분류된다 — Live로는 도달하지 않는다(타입이 settled 우선을 강제)") {
                result.of(8L) shouldBe OrderPaymentLiveness.Settled
                resultReversed.of(8L) shouldBe OrderPaymentLiveness.Settled
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

            Then("각 orderId가 자신의 판정으로만 독립적으로 분류된다") {
                (result.of(1L) as OrderPaymentLiveness.Live).since shouldBe order1ReadyAt
                result.of(2L) shouldBe OrderPaymentLiveness.Settled
                result.of(3L) shouldBe OrderPaymentLiveness.None
            }
        }
    }
})
