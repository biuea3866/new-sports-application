package com.sportsapp.domain.payment.service

import com.sportsapp.domain.payment.entity.PaymentStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime

/**
 * 만료 스위퍼(W1-11a~d) 공통 만료 금지 가드 판정 순수 함수 검증.
 *
 * 핵심 회귀 케이스: 결제 개시(initiatePg) 시점에 이미 PENDING/READY 행이 생성되므로,
 * status만으로 판단하면(구 findCompletedOrderIds -> findUnexpirableOrderIds 1차 확장)
 * 모든 PENDING/READY 주문이 "만료 금지"로 걸려 스위퍼가 완전히 무력화된다. 이 테스트는
 * updatedAt(활동 시각)이 activeSince(활동 창 시작 시각)보다 오래된 PENDING/READY는
 * 만료를 허용해야 함을 검증한다.
 */
class PaymentExpiryGuardTest : BehaviorSpec({

    val activeSince = ZonedDateTime.now().minusMinutes(5)

    Given("결제가 COMPLETED 상태일 때") {
        When("updatedAt이 activeSince보다 훨씬 이전이어도") {
            val result = PaymentExpiryGuard.isUnexpirable(
                status = PaymentStatus.COMPLETED,
                updatedAt = activeSince.minusDays(30),
                activeSince = activeSince,
            )

            Then("돈을 받았으므로 만료 금지 대상이다") {
                result shouldBe true
            }
        }
    }

    Given("결제가 READY 상태이고 updatedAt이 활동 창 이내(최근 활동)일 때") {
        When("만료 금지 여부를 판정하면") {
            val result = PaymentExpiryGuard.isUnexpirable(
                status = PaymentStatus.READY,
                updatedAt = activeSince.plusSeconds(1),
                activeSince = activeSince,
            )

            Then("사용자가 지금 PG 결제창에 있는 것으로 보아 만료 금지 대상이다") {
                result shouldBe true
            }
        }
    }

    Given("결제가 READY 상태이고 updatedAt이 활동 창보다 오래됐을 때 (핵심 회귀 케이스)") {
        When("만료 금지 여부를 판정하면") {
            val result = PaymentExpiryGuard.isUnexpirable(
                status = PaymentStatus.READY,
                updatedAt = activeSince.minusSeconds(1),
                activeSince = activeSince,
            )

            Then("방치된 결제로 보아 만료를 허용한다 — status만 보던 구 로직에서는 이 케이스가 항상 만료 금지로 걸려 스위퍼가 무력화됐다") {
                result shouldBe false
            }
        }
    }

    Given("결제가 PENDING 상태이고 updatedAt이 활동 창보다 오래됐을 때 (핵심 회귀 케이스)") {
        When("만료 금지 여부를 판정하면") {
            val result = PaymentExpiryGuard.isUnexpirable(
                status = PaymentStatus.PENDING,
                updatedAt = activeSince.minusMinutes(10),
                activeSince = activeSince,
            )

            Then("주문 생성 시 만들어진 뒤 방치된 결제로 보아 만료를 허용한다") {
                result shouldBe false
            }
        }
    }

    Given("결제가 PENDING 상태이고 updatedAt이 활동 창 이내일 때") {
        When("만료 금지 여부를 판정하면") {
            val result = PaymentExpiryGuard.isUnexpirable(
                status = PaymentStatus.PENDING,
                updatedAt = activeSince.plusSeconds(1),
                activeSince = activeSince,
            )

            Then("결제 개시 직후 진행 중인 것으로 보아 만료 금지 대상이다") {
                result shouldBe true
            }
        }
    }

    Given("updatedAt이 activeSince와 정확히 같을 때 (경계값)") {
        When("만료 금지 여부를 판정하면") {
            val result = PaymentExpiryGuard.isUnexpirable(
                status = PaymentStatus.PENDING,
                updatedAt = activeSince,
                activeSince = activeSince,
            )

            Then("활동 창에 포함되어 만료 금지 대상이다 (경계 포함, goe)") {
                result shouldBe true
            }
        }
    }

    Given("결제가 CANCELLED 상태일 때") {
        When("updatedAt이 활동 창 이내(방금 갱신)여도") {
            val result = PaymentExpiryGuard.isUnexpirable(
                status = PaymentStatus.CANCELLED,
                updatedAt = activeSince.plusSeconds(1),
                activeSince = activeSince,
            )

            Then("명확히 종료된 실패이므로 만료를 허용한다") {
                result shouldBe false
            }
        }
    }

    Given("결제가 FAILED 상태일 때") {
        When("updatedAt이 활동 창 이내(방금 갱신)여도") {
            val result = PaymentExpiryGuard.isUnexpirable(
                status = PaymentStatus.FAILED,
                updatedAt = activeSince.plusSeconds(1),
                activeSince = activeSince,
            )

            Then("명확히 종료된 실패이므로 만료를 허용한다") {
                result shouldBe false
            }
        }
    }

    Given("결제가 REFUNDED 상태일 때") {
        When("updatedAt이 활동 창 이내(방금 갱신)여도") {
            val result = PaymentExpiryGuard.isUnexpirable(
                status = PaymentStatus.REFUNDED,
                updatedAt = activeSince.plusSeconds(1),
                activeSince = activeSince,
            )

            Then("환불 완료로 돈이 이미 돌아갔으므로 방치된 PENDING 주문 정리를 허용한다") {
                result shouldBe false
            }
        }
    }
})
