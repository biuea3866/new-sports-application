package com.sportsapp.domain.mcp

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime

class McpTokenTest : BehaviorSpec({

    fun createActiveToken(): McpToken = McpToken.create(
        userId = 1L,
        name = "test-token",
        tokenHash = "hashed-token-value",
        expiresAt = null,
    )

    Given("새로 생성된 McpToken") {
        val token = createActiveToken()

        Then("[U-01] status=ACTIVE로 생성된다") {
            token.status shouldBe McpTokenStatus.ACTIVE
        }
    }

    Given("ACTIVE 상태의 McpToken") {
        val token = createActiveToken()

        When("suspend()를 호출하면") {
            token.suspend()

            Then("[U-02] status=SUSPENDED로 전이된다") {
                token.status shouldBe McpTokenStatus.SUSPENDED
            }
        }
    }

    Given("SUSPENDED 상태의 McpToken") {
        val token = createActiveToken()
        token.suspend()

        When("suspend()를 재호출하면") {
            Then("[U-02] IllegalStateException이 발생한다") {
                shouldThrow<IllegalStateException> {
                    token.suspend()
                }
            }
        }

        When("reactivate()를 호출하면") {
            token.reactivate()

            Then("[U-04] SUSPENDED → ACTIVE로 전이된다") {
                token.status shouldBe McpTokenStatus.ACTIVE
            }
        }
    }

    Given("REVOKED 상태의 McpToken") {
        val token = createActiveToken()
        token.revoke()

        When("reactivate()를 시도하면") {
            Then("[U-03] IllegalStateException이 발생한다") {
                shouldThrow<IllegalStateException> {
                    token.reactivate()
                }
            }
        }
    }

    Given("SUSPENDED 상태의 McpToken에 requireActive() 호출") {
        val token = createActiveToken()
        token.suspend()

        Then("[U-05] 도메인 예외가 발생한다") {
            shouldThrow<McpTokenInactiveException> {
                token.requireActive()
            }
        }
    }

    Given("expiresAt이 과거인 McpToken에 requireNotExpired() 호출") {
        val token = McpToken.create(
            userId = 1L,
            name = "expired-token",
            tokenHash = "hashed-expired-value",
            expiresAt = ZonedDateTime.now().minusDays(1),
        )

        Then("[U-06] 도메인 예외가 발생한다") {
            shouldThrow<McpTokenExpiredException> {
                token.requireNotExpired()
            }
        }
    }

    Given("expiresAt이 미래인 McpToken에 requireNotExpired() 호출") {
        val token = McpToken.create(
            userId = 1L,
            name = "valid-token",
            tokenHash = "hashed-valid-value",
            expiresAt = ZonedDateTime.now().plusDays(30),
        )

        Then("[U-06] 예외 없이 통과한다") {
            token.requireNotExpired()
        }
    }

    Given("expiresAt=null인 McpToken에 requireNotExpired() 호출") {
        val token = createActiveToken()

        Then("[U-06] 만료 없음으로 예외가 발생하지 않는다") {
            token.requireNotExpired()
        }
    }

    Given("ACTIVE 상태의 McpToken에 recordUsage() 호출") {
        val token = createActiveToken()
        val before = ZonedDateTime.now()

        When("recordUsage()를 호출하면") {
            token.recordUsage()

            Then("lastUsedAt이 현재 시각으로 갱신된다") {
                val lastUsedAt = token.lastUsedAt
                lastUsedAt != null && !lastUsedAt.isBefore(before) shouldBe true
            }
        }
    }
})
