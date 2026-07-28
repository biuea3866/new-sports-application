package com.sportsapp.domain.mcp.service
import com.sportsapp.domain.mcp.exception.McpTokenNotOwnedException
import com.sportsapp.domain.mcp.exception.McpScopeNotFoundException
import com.sportsapp.domain.mcp.dto.IssueMcpTokenCommand
import com.sportsapp.domain.mcp.entity.McpTokenStatus
import com.sportsapp.domain.mcp.entity.McpToken
import com.sportsapp.domain.mcp.entity.McpTokenScope
import com.sportsapp.domain.mcp.gateway.McpPermissionGateway
import com.sportsapp.domain.mcp.repository.McpTokenCustomRepository
import com.sportsapp.domain.mcp.repository.McpTokenScopeRepository
import com.sportsapp.domain.mcp.repository.McpTokenRepository

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.string.shouldHaveMinLength
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.ZonedDateTime

class McpTokenDomainServiceTest : BehaviorSpec({

    val mcpTokenRepository = mockk<McpTokenRepository>()
    val mcpTokenScopeRepository = mockk<McpTokenScopeRepository>()
    val mcpTokenCustomRepository = mockk<McpTokenCustomRepository>()
    val mcpPermissionGateway = mockk<McpPermissionGateway>()
    val passwordEncoder = mockk<PasswordEncoder>()

    val domainService = McpTokenDomainService(
        mcpTokenRepository = mcpTokenRepository,
        mcpTokenScopeRepository = mcpTokenScopeRepository,
        mcpTokenCustomRepository = mcpTokenCustomRepository,
        mcpPermissionGateway = mcpPermissionGateway,
        passwordEncoder = passwordEncoder,
    )

    fun makeToken(userId: Long = 1L, id: Long = 1L): McpToken {
        val token = McpToken.create(
            userId = userId,
            name = "test-token",
            tokenHash = "hashed",
            expiresAt = null,
        )
        val idField = McpToken::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(token, id)
        val superclass = token.javaClass.superclass
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            val field = superclass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(token, ZonedDateTime.now())
        }
        return token
    }

    Given("[U-01] 유효한 scope 목록으로 토큰 발급을 요청하면") {
        val command = IssueMcpTokenCommand(
            userId = 1L,
            name = "my-token",
            scopes = listOf("read:facility"),
            expiresAt = null,
        )
        val savedToken = makeToken(userId = 1L, id = 1L)

        every { mcpPermissionGateway.findPermissionIdBy("mcp.facility.read.own") } returns 10L
        every { passwordEncoder.encode(any()) } returns "bcrypt-hashed"
        every { mcpTokenRepository.save(any()) } returns savedToken
        every { mcpTokenScopeRepository.save(any()) } answers { firstArg() }

        When("issueToken을 호출하면") {
            val result = domainService.issueToken(command)

            Then("[U-01] 평문 토큰이 포함된 결과가 반환되고 저장은 해시로 이루어진다") {
                result.plainToken.shouldHaveMinLength(10)
                result.token shouldBe savedToken
                // issueToken은 placeholder해시 + 최종 토큰해시로 2회 encode 호출
                verify(exactly = 2) { passwordEncoder.encode(any()) }
            }
        }
    }

    Given("[U-02] 존재하지 않는 scope 문자열로 토큰 발급을 요청하면") {
        val command = IssueMcpTokenCommand(
            userId = 1L,
            name = "bad-token",
            scopes = listOf("read:nonexistent"),
            expiresAt = null,
        )

        every { mcpPermissionGateway.findPermissionIdBy("mcp.nonexistent.read.own") } returns null

        When("issueToken을 호출하면") {
            Then("[U-02] McpScopeNotFoundException이 발생한다") {
                shouldThrow<McpScopeNotFoundException> {
                    domainService.issueToken(command)
                }
            }
        }
    }

    Given("[U-10] 여러 scope 목록으로 토큰 발급을 요청하면") {
        val command = IssueMcpTokenCommand(
            userId = 1L,
            name = "multi-scope-token",
            scopes = listOf("read:facility", "write:booking"),
            expiresAt = null,
        )
        val savedToken = makeToken(userId = 1L, id = 1L)
        val savedScopes = mutableListOf<McpTokenScope>()

        every { mcpPermissionGateway.findPermissionIdBy("mcp.facility.read.own") } returns 10L
        every { mcpPermissionGateway.findPermissionIdBy("mcp.booking.write.own") } returns 20L
        every { passwordEncoder.encode(any()) } returns "bcrypt-hashed"
        every { mcpTokenRepository.save(any()) } returns savedToken
        every { mcpTokenScopeRepository.save(capture(savedScopes)) } answers { firstArg() }

        When("issueToken을 호출하면") {
            domainService.issueToken(command)

            Then("[U-10] 요청 순서대로 권한 id 목록이 저장된다") {
                savedScopes.map { it.permissionId }.shouldContainExactly(listOf(10L, 20L))
            }
        }
    }

    Given("[U-11] 빈 scope 목록으로 토큰 발급을 요청하면") {
        val command = IssueMcpTokenCommand(
            userId = 1L,
            name = "no-scope-token",
            scopes = emptyList(),
            expiresAt = null,
        )
        val savedToken = makeToken(userId = 1L, id = 1L)
        // 이 테스트의 issueToken 호출로 인한 save만 캡처한다 — mockk 는 전 Given 블록이 spec 트리
        // 구성 시점에 함께 실행되는 공유 mock 이므로, 전체 호출 횟수(verify exactly=0)는 다른
        // Given 블록의 호출과 뒤섞여 신뢰할 수 없다. 이 stub 등록 이후의 호출만 로컬 리스트로 격리한다.
        val savedScopesForThisToken = mutableListOf<McpTokenScope>()

        every { passwordEncoder.encode(any()) } returns "bcrypt-hashed"
        every { mcpTokenRepository.save(any()) } returns savedToken
        every { mcpTokenScopeRepository.save(capture(savedScopesForThisToken)) } answers { firstArg() }

        When("issueToken을 호출하면") {
            val result = domainService.issueToken(command)

            Then("[U-11] 스코프 없이 정상 발급된다 (엣지)") {
                result.token shouldBe savedToken
                savedScopesForThisToken.shouldBeEmpty()
            }
        }
    }

    Given("[U-03] 유효한 userId로 토큰 목록을 조회하면") {
        val tokens = listOf(makeToken(userId = 1L, id = 1L), makeToken(userId = 1L, id = 2L))
        every { mcpTokenCustomRepository.findActiveByUserId(1L) } returns tokens

        When("listMyTokens를 호출하면") {
            val result = domainService.listMyTokens(1L)

            Then("[U-03] 해당 유저의 활성 토큰 목록이 반환된다") {
                result.size shouldBe 2
            }
        }
    }

    Given("[U-04] 자신의 토큰 ID로 폐기를 요청하면") {
        val token = makeToken(userId = 1L, id = 10L)
        every { mcpTokenRepository.findById(10L) } returns token
        every { mcpTokenRepository.save(any()) } answers { firstArg() }

        When("revokeToken을 호출하면") {
            domainService.revokeToken(tokenId = 10L, requesterId = 1L)

            Then("[U-04] 토큰 상태가 REVOKED로 변경되고 저장된다") {
                token.status shouldBe McpTokenStatus.REVOKED
                verify(exactly = 1) { mcpTokenRepository.save(token) }
            }
        }
    }

    Given("[U-05] 타인 소유 토큰 ID로 폐기를 요청하면") {
        val token = makeToken(userId = 1L, id = 10L)
        every { mcpTokenRepository.findById(10L) } returns token

        When("revokeToken을 호출하면") {
            Then("[U-05] McpTokenNotOwnedException이 발생한다") {
                shouldThrow<McpTokenNotOwnedException> {
                    domainService.revokeToken(tokenId = 10L, requesterId = 99L)
                }
            }
        }
    }

    Given("[U-06] 이미 REVOKED된 토큰을 폐기 요청하면") {
        val token = makeToken(userId = 1L, id = 10L)
        token.revoke()
        every { mcpTokenRepository.findById(10L) } returns token

        When("revokeToken을 호출하면") {
            Then("[U-06] 상태 전이 실패로 IllegalStateException이 발생한다") {
                shouldThrow<IllegalStateException> {
                    domainService.revokeToken(tokenId = 10L, requesterId = 1L)
                }
            }
        }
    }

    Given("[U-07] 존재하지 않는 tokenId로 폐기를 요청하면") {
        every { mcpTokenRepository.findById(999L) } returns null

        When("revokeToken을 호출하면") {
            Then("[U-07] ResourceNotFoundException이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    domainService.revokeToken(tokenId = 999L, requesterId = 1L)
                }
            }
        }
    }

    Given("[U-08] 존재하는 tokenId로 recordUsage를 호출하면") {
        val token = makeToken(userId = 1L, id = 5L)
        every { mcpTokenRepository.findById(5L) } returns token
        every { mcpTokenRepository.save(any()) } answers { firstArg() }

        When("recordUsage를 호출하면") {
            domainService.recordUsage(5L)

            Then("[U-08] lastUsedAt이 설정되고 save가 호출된다") {
                token.lastUsedAt shouldNotBe null
                verify(exactly = 1) { mcpTokenRepository.save(token) }
            }
        }
    }

    Given("[U-09] 존재하지 않는 tokenId로 recordUsage를 호출하면") {
        every { mcpTokenRepository.findById(999L) } returns null

        When("recordUsage를 호출하면") {
            Then("[U-09] ResourceNotFoundException이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    domainService.recordUsage(999L)
                }
            }
        }
    }
})
