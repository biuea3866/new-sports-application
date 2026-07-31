package com.sportsapp.domain.mcp.entity
import com.sportsapp.domain.mcp.exception.McpTokenExpiredException
import com.sportsapp.domain.mcp.exception.McpTokenInactiveException

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

    Given("updateTokenHash에 빈 문자열을 전달하면") {
        val token = createActiveToken()

        Then("[U-07] IllegalArgumentException이 발생한다") {
            shouldThrow<IllegalArgumentException> {
                token.updateTokenHash("")
            }
        }
    }

    Given("updateTokenHash에 공백만 있는 문자열을 전달하면") {
        val token = createActiveToken()

        Then("[U-07] IllegalArgumentException이 발생한다") {
            shouldThrow<IllegalArgumentException> {
                token.updateTokenHash("   ")
            }
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

    Given("ACTIVE + 만료 없음(expiresAt=null)인 McpToken") {
        val token = createActiveToken()

        Then("isUsable()은 true를 반환한다") {
            token.isUsable() shouldBe true
        }

        Then("isExpired()는 false를 반환한다") {
            token.isExpired() shouldBe false
        }
    }

    Given("ACTIVE + 미래 만료인 McpToken") {
        val token = McpToken.create(
            userId = 1L,
            name = "valid-token",
            tokenHash = "hashed",
            expiresAt = ZonedDateTime.now().plusDays(1),
        )

        Then("isUsable()은 true를 반환한다") {
            token.isUsable() shouldBe true
        }
    }

    Given("ACTIVE + 과거 만료인 McpToken") {
        val token = McpToken.create(
            userId = 1L,
            name = "expired-token",
            tokenHash = "hashed",
            expiresAt = ZonedDateTime.now().minusDays(1),
        )

        Then("isExpired()는 true를 반환한다") {
            token.isExpired() shouldBe true
        }

        Then("isUsable()은 false를 반환한다 (만료)") {
            token.isUsable() shouldBe false
        }
    }

    Given("SUSPENDED 상태의 McpToken") {
        val token = createActiveToken()
        token.suspend()

        Then("isUsable()은 false를 반환한다 (비활성)") {
            token.isUsable() shouldBe false
        }
    }

    Given("mcp_<id>_<random> 형식의 평문 토큰") {
        Then("parseTokenId는 id를 추출한다") {
            McpToken.parseTokenId("mcp_42_randomPart") shouldBe 42L
        }
    }

    Given("mcp_ prefix가 없는 문자열") {
        Then("parseTokenId는 null을 반환한다") {
            McpToken.parseTokenId("bearer_42_randomPart") shouldBe null
        }
    }

    Given("mcp_ 다음에 구분자가 없는 문자열") {
        Then("parseTokenId는 null을 반환한다") {
            McpToken.parseTokenId("mcp_onlyonepart") shouldBe null
        }
    }

    Given("id 부분이 숫자가 아닌 토큰") {
        Then("parseTokenId는 null을 반환한다") {
            McpToken.parseTokenId("mcp_notanumber_randomPart") shouldBe null
        }
    }
})
