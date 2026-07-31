package com.sportsapp.domain.common.payment

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime

/**
 * [OrderPaymentLiveness.allowsExpiry] 회귀 테스트 — 만료 스위퍼(W1-11a~d 공통)가 소비하는
 * 판정을 타입 메서드로 캡슐화한 뒤에도 4개 변이(Settled/Live/Attempting/None)의 판정이
 * 그대로 유지되는지 검증한다.
 *
 * 이 테스트가 존재하는 이유(리뷰 — 판정을 타입으로 강제): 이전에는 이 로직이
 * `BookingDomainService.isExpirable`의 `when` 분기에 있었고, 같은 로직을 goods·ticketing·
 * recruitment가 각자 재구현할 때 [Live]의 두 창(AND) 중 한 항을 빠뜨리는 결함이 3번
 * 재발했다(6차·7차·8차). 이제 판정은 이 공유 커널 안에 한 곳만 있으므로, 소비 도메인이
 * 아무리 재작성해도 이 결함이 재발할 수 없다 — 이 테스트는 그 유일한 판정 지점을 직접
 * 검증한다.
 */
class OrderPaymentLivenessTest : BehaviorSpec({

    val now = ZonedDateTime.now()
    val orderCreatedAt = now.minusHours(2)
    val readyThreshold = now.minusMinutes(60) // readyTtlMinutes=60 기준 임계
    val fastThreshold = now.minusMinutes(15) // ttlMinutes=15 기준 임계

    Given("Settled 판정일 때") {
        val liveness = OrderPaymentLiveness.Settled

        When("allowsExpiry를 호출하면") {
            val result = liveness.allowsExpiry(orderCreatedAt, readyThreshold, fastThreshold)

            Then("항상 false를 반환한다 — 절대 만료 금지") {
                result shouldBe false
            }
        }
    }

    Given("Live 판정에서 느린 TTL(since)이 아직 지나지 않았을 때") {
        val liveness = OrderPaymentLiveness.Live(since = now.minusMinutes(10), attemptSince = null)

        When("allowsExpiry를 호출하면") {
            val result = liveness.allowsExpiry(orderCreatedAt, readyThreshold, fastThreshold)

            Then("attemptSince와 무관하게 false를 반환한다 — 단락 평가") {
                result shouldBe false
            }
        }
    }

    Given("Live 판정에서 느린 TTL(since)은 지났고 attemptSince가 없을 때") {
        val liveness = OrderPaymentLiveness.Live(since = now.minusMinutes(70), attemptSince = null)

        When("allowsExpiry를 호출하면") {
            val result = liveness.allowsExpiry(orderCreatedAt, readyThreshold, fastThreshold)

            Then("true를 반환한다 — 그대로 만료 대상") {
                result shouldBe true
            }
        }
    }

    Given("Live 판정에서 느린 TTL(since)은 지났지만 attemptSince(빠른 TTL)가 아직 안 지났을 때") {
        val liveness = OrderPaymentLiveness.Live(since = now.minusMinutes(70), attemptSince = now.minusMinutes(5))

        When("allowsExpiry를 호출하면") {
            val result = liveness.allowsExpiry(orderCreatedAt, readyThreshold, fastThreshold)

            Then("false를 반환한다 — 두 창이 모두 닫혀야 한다(AND)") {
                result shouldBe false
            }
        }
    }

    Given("Live 판정에서 느린 TTL·빠른 TTL(attemptSince) 둘 다 지났을 때") {
        val liveness = OrderPaymentLiveness.Live(since = now.minusMinutes(70), attemptSince = now.minusMinutes(20))

        When("allowsExpiry를 호출하면") {
            val result = liveness.allowsExpiry(orderCreatedAt, readyThreshold, fastThreshold)

            Then("true를 반환한다") {
                result shouldBe true
            }
        }
    }

    Given("느린 TTL을 이미 지난 Live에 재결제 시도 증거가 추가될 때 (단조성 불변식)") {
        val staleSince = now.minusMinutes(70)
        val beforeEvidence = OrderPaymentLiveness.Live(since = staleSince, attemptSince = null)
        val afterEvidence = OrderPaymentLiveness.Live(since = staleSince, attemptSince = now.minusSeconds(5))

        When("증거 추가 전후로 allowsExpiry를 각각 호출하면") {
            val before = beforeEvidence.allowsExpiry(orderCreatedAt, readyThreshold, fastThreshold)
            val after = afterEvidence.allowsExpiry(orderCreatedAt, readyThreshold, fastThreshold)

            Then("증거 추가 전에는 만료 대상이었다가, 증거가 추가되면 보호로 전환된다 — 절대 반대 방향(보호 해제)은 없다") {
                before shouldBe true
                after shouldBe false
            }
        }
    }

    Given("Live 판정에서 payment 행 시각이 주문 생성 시각보다 이르다는 데이터 전제가 깨졌을 때 (p4 — maxOf 방어)") {
        // orderCreatedAt이 since보다 늦은(더 최근인) 비정상 데이터 — maxOf(orderCreatedAt, since)가
        // 없으면 오래된 since만 보고 readyThreshold를 지났다고 오판정해 조기 만료될 수 있다.
        val recentOrderCreatedAt = now.minusMinutes(30)
        val staleSince = now.minusMinutes(70)
        val liveness = OrderPaymentLiveness.Live(since = staleSince, attemptSince = null)

        When("allowsExpiry를 호출하면") {
            val result = liveness.allowsExpiry(recentOrderCreatedAt, readyThreshold, fastThreshold)

            Then("orderCreatedAt과 since의 최댓값 기준으로 아직 readyThreshold를 지나지 않아 false를 반환한다") {
                result shouldBe false
            }
        }
    }

    Given("Attempting 판정에서 시도 시각과 주문 생성 시각의 최댓값이 빠른 TTL을 지나지 않았을 때") {
        val liveness = OrderPaymentLiveness.Attempting(since = now.minusMinutes(5))

        When("allowsExpiry를 호출하면") {
            val result = liveness.allowsExpiry(orderCreatedAt, readyThreshold, fastThreshold)

            Then("false를 반환한다") {
                result shouldBe false
            }
        }
    }

    Given("Attempting 판정에서 시도 시각과 주문 생성 시각의 최댓값이 빠른 TTL을 지났을 때") {
        val liveness = OrderPaymentLiveness.Attempting(since = now.minusMinutes(20))

        When("allowsExpiry를 호출하면") {
            val result = liveness.allowsExpiry(orderCreatedAt, readyThreshold, fastThreshold)

            Then("true를 반환한다") {
                result shouldBe true
            }
        }
    }

    Given("None 판정에서 주문 생성 시각이 빠른 TTL을 지나지 않았을 때") {
        val liveness = OrderPaymentLiveness.None
        val recentOrderCreatedAt = now.minusMinutes(5)

        When("allowsExpiry를 호출하면") {
            val result = liveness.allowsExpiry(recentOrderCreatedAt, readyThreshold, fastThreshold)

            Then("false를 반환한다") {
                result shouldBe false
            }
        }
    }

    Given("None 판정에서 주문 생성 시각이 빠른 TTL을 지났을 때") {
        val liveness = OrderPaymentLiveness.None

        When("allowsExpiry를 호출하면") {
            val result = liveness.allowsExpiry(orderCreatedAt, readyThreshold, fastThreshold)

            Then("true를 반환한다") {
                result shouldBe true
            }
        }
    }
})
