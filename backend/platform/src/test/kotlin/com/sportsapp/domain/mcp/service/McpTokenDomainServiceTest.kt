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
import io.kotest.matchers.string.shouldNotContain
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
        // 이 테스트의 issueToken 호출로 인한 save만 캡처한다 — Kotest 기본 IsolationMode.SingleInstance
        // 는 spec 인스턴스 1개를 모든 Given 이 공유하므로 mock 호출 이력이 Given 간에 누적된다.
        // 따라서 전체 호출 횟수(verify exactly=0)는 신뢰할 수 없고, 이 stub 등록 이후의 호출만
        // 로컬 리스트로 격리한다.
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

    Given("유효한 평문 토큰으로 verifyToken을 호출하면") {
        val token = makeToken(userId = 7L, id = 42L)
        every { mcpTokenRepository.findById(42L) } returns token
        every { passwordEncoder.matches("mcp_42_random", token.tokenHash) } returns true
        every { mcpTokenScopeRepository.findByTokenId(42L) } returns
            listOf(McpTokenScope.create(tokenId = 42L, permissionId = 10L))
        every { mcpPermissionGateway.findPermissionNamesBy(listOf(10L)) } returns
            mapOf(10L to "mcp.facility.read.own")

        When("verifyToken을 호출하면") {
            val result = domainService.verifyToken("mcp_42_random")

            Then("주체 식별자와 스코프 목록이 담긴 유효한 결과가 반환된다") {
                result.valid shouldBe true
                result.tokenId shouldBe 42L
                result.userId shouldBe 7L
                result.scopes.map { it.asString() }.shouldContainExactly(listOf("read:facility"))
            }

            Then("검증 결과에 토큰 원문·해시가 포함되지 않는다") {
                result.toString() shouldNotContain "mcp_42_random"
                result.toString() shouldNotContain token.tokenHash
            }
        }
    }

    Given("존재하지 않는 tokenId 형식의 토큰으로 verifyToken을 호출하면") {
        When("verifyToken을 호출하면") {
            val result = domainService.verifyToken("not-an-mcp-token")

            Then("유효하지 않은 결과가 반환된다") {
                result.valid shouldBe false
            }
        }
    }

    Given("존재하지 않는 토큰 id로 verifyToken을 호출하면") {
        every { mcpTokenRepository.findById(999L) } returns null

        When("verifyToken을 호출하면") {
            val result = domainService.verifyToken("mcp_999_random")

            Then("유효하지 않은 결과가 반환된다") {
                result.valid shouldBe false
            }
        }
    }

    Given("해시가 일치하지 않는 토큰으로 verifyToken을 호출하면") {
        val token = makeToken(userId = 7L, id = 42L)
        every { mcpTokenRepository.findById(42L) } returns token
        every { passwordEncoder.matches("mcp_42_wrong", token.tokenHash) } returns false

        When("verifyToken을 호출하면") {
            val result = domainService.verifyToken("mcp_42_wrong")

            Then("유효하지 않은 결과가 반환된다") {
                result.valid shouldBe false
            }
        }
    }

    Given("만료된 토큰으로 verifyToken을 호출하면") {
        val token = makeToken(userId = 7L, id = 43L)
        val expiredField = McpToken::class.java.getDeclaredField("expiresAt")
        expiredField.isAccessible = true
        expiredField.set(token, ZonedDateTime.now().minusDays(1))
        every { mcpTokenRepository.findById(43L) } returns token
        every { passwordEncoder.matches("mcp_43_random", token.tokenHash) } returns true

        When("verifyToken을 호출하면") {
            val result = domainService.verifyToken("mcp_43_random")

            Then("예외 없이 유효하지 않은 결과가 반환된다 (실패 경로)") {
                result.valid shouldBe false
            }
        }
    }

    Given("비활성(SUSPENDED)된 토큰으로 verifyToken을 호출하면") {
        val token = makeToken(userId = 7L, id = 44L)
        token.suspend()
        every { mcpTokenRepository.findById(44L) } returns token
        every { passwordEncoder.matches("mcp_44_random", token.tokenHash) } returns true

        When("verifyToken을 호출하면") {
            val result = domainService.verifyToken("mcp_44_random")

            Then("유효하지 않은 결과가 반환된다") {
                result.valid shouldBe false
            }
        }
    }

    Given("알 수 없는 권한명이 섞인 스코프를 가진 유효한 토큰으로 verifyToken을 호출하면") {
        val token = makeToken(userId = 7L, id = 45L)
        every { mcpTokenRepository.findById(45L) } returns token
        every { passwordEncoder.matches("mcp_45_random", token.tokenHash) } returns true
        every { mcpTokenScopeRepository.findByTokenId(45L) } returns
            listOf(
                McpTokenScope.create(tokenId = 45L, permissionId = 10L),
                McpTokenScope.create(tokenId = 45L, permissionId = 999L),
            )
        every { mcpPermissionGateway.findPermissionNamesBy(listOf(10L, 999L)) } returns
            mapOf(10L to "mcp.facility.read.own")

        When("verifyToken을 호출하면") {
            val result = domainService.verifyToken("mcp_45_random")

            Then("알 수 없는 권한명(999L) 항목만 건너뛰고 나머지 스코프만 반환된다 (엣지 — mapNotNull 계약)") {
                result.valid shouldBe true
                result.scopes.map { it.asString() }.shouldContainExactly(listOf("read:facility"))
            }
        }
    }
})
