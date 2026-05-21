package com.sportsapp.infrastructure.persistence.mcp

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.mcp.McpToken
import com.sportsapp.domain.mcp.McpTokenStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.ZonedDateTime

class McpTokenRepositoryImplTest(
    @Autowired private val mcpTokenJpaRepository: McpTokenJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val transactionManager: PlatformTransactionManager,
) : BaseJpaIntegrationTest() {

    private fun createToken(
        userId: Long = 1L,
        tokenHash: String = "hash-${System.nanoTime()}",
        expiresAt: ZonedDateTime? = null,
    ): McpToken = mcpTokenJpaRepository.save(
        McpToken.create(
            userId = userId,
            name = "test-token",
            tokenHash = tokenHash,
            expiresAt = expiresAt,
        )
    )

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM mcp_token_scopes")
            jdbcTemplate.execute("DELETE FROM mcp_tokens")
        }

        Given("[R-01] McpToken save 후 findByTokenHash 라운드트립") {
            val token = createToken(userId = 42L, tokenHash = "unique-hash-r01")

            When("findByTokenHash로 조회하면") {
                val found = mcpTokenJpaRepository.findByTokenHashAndDeletedAtIsNull("unique-hash-r01")

                Then("[R-01] audit 컬럼이 자동으로 채워지고 원본 필드와 일치한다") {
                    found.shouldNotBeNull()
                    found.id shouldBe token.id
                    found.userId shouldBe 42L
                    found.tokenHash shouldBe "unique-hash-r01"
                    found.status shouldBe McpTokenStatus.ACTIVE
                    found.createdAt.shouldNotBeNull()
                    found.updatedAt.shouldNotBeNull()
                    found.deletedAt.shouldBeNull()
                }
            }
        }

        Given("[R-02] softDelete 후 findByTokenHash 조회") {
            createToken(tokenHash = "hash-r02-soft-delete")
            val saved = mcpTokenJpaRepository.findByTokenHashAndDeletedAtIsNull("hash-r02-soft-delete")
            saved.shouldNotBeNull()
            saved.softDelete(userId = null)
            mcpTokenJpaRepository.save(saved)

            When("softDelete 후 findByTokenHashAndDeletedAtIsNull로 조회하면") {
                val found = mcpTokenJpaRepository.findByTokenHashAndDeletedAtIsNull("hash-r02-soft-delete")

                Then("[R-02] null이 반환된다") {
                    found.shouldBeNull()
                }
            }
        }

        Given("[R-03] findActiveByUserId — 활성 토큰만 반환") {
            val userId = 99L
            createToken(userId = userId, tokenHash = "hash-r03-active")

            val suspendedToken = createToken(userId = userId, tokenHash = "hash-r03-suspended")
            suspendedToken.suspend()
            mcpTokenJpaRepository.save(suspendedToken)

            createToken(userId = userId + 1, tokenHash = "hash-r03-other-user")

            When("findActiveByUserId로 조회하면") {
                val results = mcpTokenJpaRepository.findActiveByUserId(userId)

                Then("[R-03] deleted_at IS NULL + status=ACTIVE인 토큰만 반환된다") {
                    results shouldHaveSize 1
                    results[0].tokenHash shouldBe "hash-r03-active"
                    results[0].status shouldBe McpTokenStatus.ACTIVE
                }
            }
        }

        Given("[R-04] 동일 token_hash로 두 번 insert 시도") {
            createToken(tokenHash = "duplicate-hash-r04")

            Then("[R-04] unique 제약 위반이 발생한다") {
                shouldThrow<DataIntegrityViolationException> {
                    createToken(tokenHash = "duplicate-hash-r04")
                }
            }
        }

        Given("[R-05] 동일 McpToken을 두 번 softDelete 시도 — 상태 충돌 검증") {
            val transactionTemplate = TransactionTemplate(transactionManager)

            val tokenId = transactionTemplate.execute {
                createToken(tokenHash = "hash-r05-state-conflict").id
            }
            tokenId.shouldNotBeNull()

            Then("[R-05] 첫 번째 softDelete 성공 후 두 번째 softDelete는 IllegalStateException을 발생시킨다") {
                shouldThrow<IllegalStateException> {
                    val token = mcpTokenJpaRepository.findById(tokenId).get()
                    token.softDelete(userId = null)
                    mcpTokenJpaRepository.save(token)

                    val alreadyDeleted = mcpTokenJpaRepository.findById(tokenId).get()
                    alreadyDeleted.softDelete(userId = null)
                }
            }
        }
    }
}
