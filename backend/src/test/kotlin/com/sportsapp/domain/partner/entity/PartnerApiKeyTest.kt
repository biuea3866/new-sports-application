package com.sportsapp.domain.partner.entity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime

class PartnerApiKeyTest : BehaviorSpec({

    fun createActiveApiKey(): PartnerApiKey = PartnerApiKey.create(
        partnerId = 1L,
        keyHash = "hashed-key-value",
    )

    Given("새로 생성된 PartnerApiKey") {
        val apiKey = createActiveApiKey()

        Then("status=ACTIVE, revokedAt=null, lastUsedAt=null로 생성된다") {
            apiKey.status shouldBe ApiKeyStatus.ACTIVE
            apiKey.revokedAt.shouldBeNull()
            apiKey.lastUsedAt.shouldBeNull()
        }

        Then("isActive()는 true를 반환한다") {
            apiKey.isActive() shouldBe true
        }
    }

    Given("ACTIVE 상태의 PartnerApiKey") {
        val apiKey = createActiveApiKey()
        val before = ZonedDateTime.now()

        When("revoke()를 호출하면") {
            apiKey.revoke()

            Then("status=REVOKED로 전이되고 revokedAt이 채워진다") {
                apiKey.status shouldBe ApiKeyStatus.REVOKED
                val revokedAt = apiKey.revokedAt.shouldNotBeNull()
                revokedAt.isBefore(before) shouldBe false
            }

            Then("isActive()는 false를 반환한다") {
                apiKey.isActive() shouldBe false
            }
        }
    }

    Given("이미 REVOKED된 PartnerApiKey") {
        val apiKey = createActiveApiKey()
        apiKey.revoke()
        val firstRevokedAt = apiKey.revokedAt

        When("revoke()를 재호출하면") {
            apiKey.revoke()

            Then("상태와 revokedAt이 변하지 않는다(멱등)") {
                apiKey.status shouldBe ApiKeyStatus.REVOKED
                apiKey.revokedAt shouldBe firstRevokedAt
            }
        }
    }

    Given("PartnerApiKey에 recordUsage()를 두 차례 호출하면") {
        val apiKey = createActiveApiKey()
        apiKey.recordUsage()
        val firstUsedAt = apiKey.lastUsedAt.shouldNotBeNull()

        Thread.sleep(5)
        apiKey.recordUsage()
        val secondUsedAt = apiKey.lastUsedAt.shouldNotBeNull()

        Then("lastUsedAt이 이전 값보다 뒤 시각으로 갱신된다") {
            secondUsedAt.isAfter(firstUsedAt) shouldBe true
        }
    }

    Given("빈 문자열 keyHash로 PartnerApiKey를 생성하면") {
        Then("IllegalArgumentException이 발생한다") {
            shouldThrow<IllegalArgumentException> {
                PartnerApiKey.create(partnerId = 1L, keyHash = "")
            }
        }
    }
})
