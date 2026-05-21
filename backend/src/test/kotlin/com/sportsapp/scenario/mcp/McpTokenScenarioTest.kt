package com.sportsapp.scenario.mcp

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.mcp.McpToken
import com.sportsapp.domain.mcp.McpTokenExpiredException
import com.sportsapp.domain.mcp.McpTokenStatus
import com.sportsapp.infrastructure.persistence.mcp.McpTokenJpaRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZonedDateTime

class McpTokenScenarioTest(
    @Autowired private val mcpTokenJpaRepository: McpTokenJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM mcp_token_scopes")
            jdbcTemplate.execute("DELETE FROM mcp_tokens")
        }

        Given("[S-01] McpToken 저장 → suspend → reactivate 상태 전이 E2E") {
            val token = mcpTokenJpaRepository.save(
                McpToken.create(
                    userId = 1L,
                    name = "scenario-token",
                    tokenHash = "scenario-hash-s01",
                    expiresAt = null,
                )
            )

            When("suspend() 후 저장하면") {
                token.suspend()
                val suspended = mcpTokenJpaRepository.save(token)

                Then("[S-01] DB 상태가 SUSPENDED로 반영된다") {
                    val found = mcpTokenJpaRepository.findById(suspended.id).get()
                    found.status shouldBe McpTokenStatus.SUSPENDED
                }

                When("reactivate() 후 저장하면") {
                    suspended.reactivate()
                    val reactivated = mcpTokenJpaRepository.save(suspended)

                    Then("[S-01] DB 상태가 ACTIVE로 복원된다") {
                        val found = mcpTokenJpaRepository.findById(reactivated.id).get()
                        found.status shouldBe McpTokenStatus.ACTIVE
                    }
                }
            }
        }

        Given("[S-02] expiresAt이 과거인 토큰을 findByTokenHash로 조회 후 requireNotExpired 호출") {
            val expiredAt = ZonedDateTime.now().minusDays(1)
            mcpTokenJpaRepository.save(
                McpToken.create(
                    userId = 2L,
                    name = "expired-scenario-token",
                    tokenHash = "scenario-hash-s02-expired",
                    expiresAt = expiredAt,
                )
            )

            When("findByTokenHashAndDeletedAtIsNull로 조회하면") {
                val found = mcpTokenJpaRepository.findByTokenHashAndDeletedAtIsNull("scenario-hash-s02-expired")

                Then("[S-02] status=ACTIVE인 Entity가 조회된다") {
                    found.shouldNotBeNull()
                    found.status shouldBe McpTokenStatus.ACTIVE
                }

                Then("[S-02] requireNotExpired() 호출 시 McpTokenExpiredException이 발생한다") {
                    shouldThrow<McpTokenExpiredException> {
                        found.shouldNotBeNull()
                        found.requireNotExpired()
                    }
                }
            }
        }
    }
}
