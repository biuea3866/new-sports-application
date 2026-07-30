package com.sportsapp.domain.partner.entity

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.partner.gateway.ApiKeyGenerator
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
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

    Given("저장된 해시를 가진 PartnerApiKey에 평문 키 검증을 요청하면") {
        val apiKey = PartnerApiKey.reconstitute(
            id = 5L,
            partnerId = 1L,
            keyHash = "stored-hash",
            status = ApiKeyStatus.ACTIVE,
            revokedAt = null,
            lastUsedAt = null,
        )
        val apiKeyGenerator = mockk<ApiKeyGenerator>()

        When("평문이 저장된 해시와 일치하면") {
            every { apiKeyGenerator.matches("plain-key", "stored-hash") } returns true

            Then("verify는 true를 반환하고 keyHash를 외부로 노출하지 않는다") {
                apiKey.verify("plain-key", apiKeyGenerator) shouldBe true
            }
        }

        When("평문이 저장된 해시와 일치하지 않으면") {
            every { apiKeyGenerator.matches("wrong-key", "stored-hash") } returns false

            Then("verify는 false를 반환한다") {
                apiKey.verify("wrong-key", apiKeyGenerator) shouldBe false
            }
        }
    }

    Given("특정 partner 소유의 PartnerApiKey") {
        val apiKey = PartnerApiKey.reconstitute(
            id = 5L,
            partnerId = 1L,
            keyHash = "stored-hash",
            status = ApiKeyStatus.ACTIVE,
            revokedAt = null,
            lastUsedAt = null,
        )

        Then("자신의 소유자로 requireOwnedBy를 호출하면 예외가 없다") {
            shouldNotThrow<ResourceNotFoundException> {
                apiKey.requireOwnedBy(1L)
            }
        }

        Then("타 partner로 requireOwnedBy를 호출하면 ResourceNotFoundException이 발생한다") {
            shouldThrow<ResourceNotFoundException> {
                apiKey.requireOwnedBy(2L)
            }
        }
    }
})
