package com.sportsapp.contract

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * 하위 호환 판정 규칙 자체를 고정한다 (W1-10 / §9-2).
 *
 * 이 규칙이 느슨하면 스냅샷 대조는 통과하면서 "두 서비스 동시 배포가 필요한 변경"(§3-2 금지 ⑥)이
 * 새어 나간다. 그래서 판정 함수를 먼저 테스트로 못박는다.
 */
class ContractCompatibilityTest : BehaviorSpec({

    val baseline: ContractFields = mapOf(
        "paymentId" to ContractField(type = "INTEGER", nullable = false),
        "memo" to ContractField(type = "STRING", nullable = true),
    )

    Given("계약이 그대로일 때") {
        When("호환성을 판정하면") {
            val violations = ContractCompatibility.violationsOf(baseline, baseline)

            Then("위반이 없다") {
                violations.shouldBeEmpty()
            }
        }
    }

    Given("필드를 제거했을 때") {
        val current = baseline - "memo"

        When("호환성을 판정하면") {
            val violations = ContractCompatibility.violationsOf(baseline, current)

            Then("파괴적 변경으로 잡힌다 — 구 소비자가 읽던 필드가 사라진다") {
                violations.size shouldBe 1
                violations.first().field shouldBe "memo"
                violations.first().reason shouldContain "필드 제거"
            }
        }
    }

    Given("필드 타입을 바꿨을 때") {
        val current = baseline + ("paymentId" to ContractField(type = "STRING", nullable = false))

        When("호환성을 판정하면") {
            val violations = ContractCompatibility.violationsOf(baseline, current)

            Then("파괴적 변경으로 잡힌다") {
                violations.size shouldBe 1
                violations.first().reason shouldContain "타입 변경 INTEGER → STRING"
            }
        }
    }

    Given("기존 optional 필드를 필수화했을 때") {
        val current = baseline + ("memo" to ContractField(type = "STRING", nullable = false))

        When("호환성을 판정하면") {
            val violations = ContractCompatibility.violationsOf(baseline, current)

            Then("파괴적 변경으로 잡힌다 — 구 소비자가 보내던 null 이 거부된다") {
                violations.size shouldBe 1
                violations.first().reason shouldContain "필수화"
            }
        }
    }

    Given("optional 필드를 추가했을 때") {
        val current = baseline + ("couponCode" to ContractField(type = "STRING", nullable = true))

        When("호환성을 판정하면") {
            val violations = ContractCompatibility.violationsOf(baseline, current)

            Then("통과한다 — §9-2 가 허용하는 유일한 확장이다") {
                violations.shouldBeEmpty()
            }
        }
    }

    Given("필수 필드를 추가했을 때") {
        val current = baseline + ("settlementId" to ContractField(type = "INTEGER", nullable = false))

        When("호환성을 판정하면") {
            val violations = ContractCompatibility.violationsOf(baseline, current)

            Then("파괴적 변경으로 잡힌다 — 구 공급자가 그 값을 채울 수 없다") {
                violations.size shouldBe 1
                violations.first().reason shouldContain "필수 필드 추가"
            }
        }
    }

    Given("여러 위반이 동시에 있을 때") {
        val current = (baseline - "memo") + ("paymentId" to ContractField(type = "STRING", nullable = false))

        When("호환성을 판정하면") {
            val violations = ContractCompatibility.violationsOf(baseline, current)

            Then("모두 보고한다 — 하나 고치고 다시 돌리는 왕복을 줄인다") {
                violations.map { it.field }.sorted() shouldBe listOf("memo", "paymentId")
            }
        }
    }
})
