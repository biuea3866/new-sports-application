package com.sportsapp.infrastructure.persistence.mcp

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.mcp.McpToken
import com.sportsapp.domain.mcp.McpTokenStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.OptimisticLockException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.ZonedDateTime

class McpTokenRepositoryImplTest(
    @Autowired private val mcpTokenJpaRepository: McpTokenJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val transactionManager: PlatformTransactionManager,
    @Autowired private val entityManagerFactory: EntityManagerFactory,
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

        Given("[R-02b] softDelete 후 findByIdAndDeletedAtIsNull 조회") {
            val token = createToken(tokenHash = "hash-r02b-findbyid")
            token.softDelete(userId = null)
            mcpTokenJpaRepository.save(token)

            When("softDelete 후 findByIdAndDeletedAtIsNull로 조회하면") {
                val found = mcpTokenJpaRepository.findByIdAndDeletedAtIsNull(token.id)

                Then("[R-02b] null이 반환된다") {
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

        Given("[R-05] 두 트랜잭션이 같은 McpToken row를 동시 수정") {
            val transactionTemplate = TransactionTemplate(transactionManager)

            val tokenId = transactionTemplate.execute {
                createToken(tokenHash = "hash-r05-optimistic-lock").id
            }
            tokenId.shouldNotBeNull()

            Then("[R-05] 후행 커밋이 OptimisticLockException을 발생시킨다") {
                val entityManager1 = entityManagerFactory.createEntityManager()
                val entityManager2 = entityManagerFactory.createEntityManager()

                try {
                    entityManager1.transaction.begin()
                    entityManager2.transaction.begin()

                    val token1 = entityManager1.find(McpToken::class.java, tokenId)
                    val token2 = entityManager2.find(McpToken::class.java, tokenId)

                    token1.suspend()
                    entityManager1.flush()
                    entityManager1.transaction.commit()

                    token2.suspend()

                    shouldThrow<Exception> {
                        entityManager2.flush()
                        entityManager2.transaction.commit()
                    }.also { exception ->
                        val isOptimisticLock = exception is OptimisticLockException ||
                            exception is ObjectOptimisticLockingFailureException ||
                            exception.cause is OptimisticLockException
                        isOptimisticLock shouldBe true
                    }
                } finally {
                    if (entityManager1.transaction.isActive) entityManager1.transaction.rollback()
                    if (entityManager2.transaction.isActive) entityManager2.transaction.rollback()
                    entityManager1.close()
                    entityManager2.close()
                }
            }
        }

        Given("[S-01] McpToken 저장 → suspend → reactivate 상태 전이 E2E") {
            val token = createToken(tokenHash = "hash-s01-state-transition")

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
            createToken(
                tokenHash = "hash-s02-expired",
                expiresAt = expiredAt,
            )

            When("findByTokenHashAndDeletedAtIsNull로 조회하면") {
                val found = mcpTokenJpaRepository.findByTokenHashAndDeletedAtIsNull("hash-s02-expired")

                Then("[S-02] status=ACTIVE인 Entity가 조회된다") {
                    found.shouldNotBeNull()
                    found.status shouldBe McpTokenStatus.ACTIVE
                }

                Then("[S-02] requireNotExpired() 호출 시 McpTokenExpiredException이 발생한다") {
                    shouldThrow<com.sportsapp.domain.mcp.McpTokenExpiredException> {
                        found.shouldNotBeNull()
                        found.requireNotExpired()
                    }
                }
            }
        }
    }
}
