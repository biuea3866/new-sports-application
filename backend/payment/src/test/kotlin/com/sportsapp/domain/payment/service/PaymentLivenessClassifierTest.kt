package com.sportsapp.domain.payment.service

import com.sportsapp.domain.payment.entity.PaymentStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * [PaymentLivenessClassifier] — 4차 재설계 판정 순수 함수의 5개 payment 상태 전수 검증.
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
})
