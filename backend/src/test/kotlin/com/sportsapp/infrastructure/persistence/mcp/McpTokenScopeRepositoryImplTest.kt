package com.sportsapp.infrastructure.persistence.mcp

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.mcp.McpToken
import com.sportsapp.domain.mcp.McpTokenScope
import com.sportsapp.domain.mcp.McpTokenScopeRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class McpTokenScopeRepositoryImplTest(
    @Autowired private val mcpTokenScopeRepository: McpTokenScopeRepository,
    @Autowired private val mcpTokenJpaRepository: McpTokenJpaRepository,
    @Autowired private val mcpTokenScopeJpaRepository: McpTokenScopeJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    private fun createToken(userId: Long = 1L): McpToken =
        mcpTokenJpaRepository.save(
            McpToken.create(
                userId = userId,
                name = "scope-test-token",
                tokenHash = "hash-${System.nanoTime()}",
                expiresAt = null,
            )
        )

    private fun createScope(tokenId: Long, permissionId: Long): McpTokenScope =
        mcpTokenScopeRepository.save(McpTokenScope.create(tokenId = tokenId, permissionId = permissionId))

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM mcp_token_scopes")
            jdbcTemplate.execute("DELETE FROM mcp_tokens")
        }

        Given("[R-01] McpTokenScope save 후 findByTokenId 라운드트립") {
            val token = createToken()
            val scope = createScope(tokenId = token.id, permissionId = 1L)

            When("findByTokenId로 조회하면") {
                val result = mcpTokenScopeRepository.findByTokenId(token.id)

                Then("[R-01] audit 컬럼이 자동으로 채워지고 원본 필드와 일치한다") {
                    result shouldHaveSize 1
                    result[0].id shouldBe scope.id
                    result[0].tokenId shouldBe token.id
                    result[0].permissionId shouldBe 1L
                    result[0].createdAt.shouldNotBeNull()
                    result[0].updatedAt.shouldNotBeNull()
                    result[0].deletedAt shouldBe null
                }
            }
        }

        Given("[R-02] softDelete 후 findByTokenId 조회 — deletedAt IS NULL 필터") {
            val token = createToken()
            val scope = createScope(tokenId = token.id, permissionId = 2L)
            scope.softDelete(userId = null)
            mcpTokenScopeJpaRepository.save(scope)

            When("softDelete 후 findByTokenId로 조회하면") {
                val result = mcpTokenScopeRepository.findByTokenId(token.id)

                Then("[R-02] 소프트 삭제된 scope는 반환되지 않는다") {
                    result.shouldBeEmpty()
                }
            }
        }

        Given("[R-03] 한 토큰에 서로 다른 permissionId 2개를 save하면") {
            val token = createToken()
            createScope(tokenId = token.id, permissionId = 10L)
            createScope(tokenId = token.id, permissionId = 11L)

            When("findByTokenId로 조회하면") {
                val result = mcpTokenScopeRepository.findByTokenId(token.id)

                Then("[R-03] deletedAt IS NULL인 scope 2건이 반환된다") {
                    result shouldHaveSize 2
                    result.map { it.permissionId }.containsAll(listOf(10L, 11L)) shouldBe true
                }
            }
        }

        Given("[R-04] softDelete 후 동일 (token_id, permission_id)로 재부여 — deleted_at 포함 unique 정책") {
            val token = createToken()
            val scope = createScope(tokenId = token.id, permissionId = 4L)
            scope.softDelete(userId = null)
            mcpTokenScopeJpaRepository.save(scope)

            When("소프트 삭제 후 동일 permissionId로 새 scope를 save하면") {
                val reGranted = createScope(tokenId = token.id, permissionId = 4L)

                Then("[R-04] 재부여가 성공하고 신규 scope가 저장된다") {
                    val result = mcpTokenScopeRepository.findByTokenId(token.id)
                    result shouldHaveSize 1
                    result[0].id shouldBe reGranted.id
                }
            }
        }
    }
}
