package com.sportsapp.domain.common

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class CommonEnumTest : BehaviorSpec({

    Given("UserRoleName enum") {
        When("entries 를 확인하면") {
            Then("USER, ADMIN, FACILITY_OWNER, EVENT_HOST, GOODS_SELLER, OPERATIONS_MANAGER 6개를 포함한다") {
                val entries = UserRoleName.entries
                entries.size shouldBe 6
                entries.map { it.name } shouldBe listOf(
                    "USER",
                    "ADMIN",
                    "FACILITY_OWNER",
                    "EVENT_HOST",
                    "GOODS_SELLER",
                    "OPERATIONS_MANAGER",
                )
            }
        }
    }

    Given("Currency enum") {
        When("KRW 의 code 프로퍼티를 확인하면") {
            Then("\"KRW\" 를 반환한다") {
                Currency.KRW.code shouldBe "KRW"
            }
        }
    }

    Given("PgEventType enum") {
        When("PAYMENT_APPROVED.value 를 확인하면") {
            Then("\"PAYMENT_APPROVED\" 를 반환한다") {
                PgEventType.PAYMENT_APPROVED.value shouldBe "PAYMENT_APPROVED"
            }
        }

        When("PAYMENT_CANCELED.value 를 확인하면") {
            Then("\"PAYMENT_CANCELED\" 를 반환한다") {
                PgEventType.PAYMENT_CANCELED.value shouldBe "PAYMENT_CANCELED"
            }
        }

        When("\"PAYMENT_APPROVED\" 로 역방향 lookup 하면") {
            Then("PgEventType.PAYMENT_APPROVED 가 반환된다") {
                PgEventType.fromValue("PAYMENT_APPROVED") shouldBe PgEventType.PAYMENT_APPROVED
            }
        }

        When("\"PAYMENT_CANCELED\" 로 역방향 lookup 하면") {
            Then("PgEventType.PAYMENT_CANCELED 가 반환된다") {
                PgEventType.fromValue("PAYMENT_CANCELED") shouldBe PgEventType.PAYMENT_CANCELED
            }
        }

        When("알 수 없는 value 로 역방향 lookup 하면") {
            Then("IllegalArgumentException 을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    PgEventType.fromValue("UNKNOWN_EVENT")
                }
            }
        }
    }
})
