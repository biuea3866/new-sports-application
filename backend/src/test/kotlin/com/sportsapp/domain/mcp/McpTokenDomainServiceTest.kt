package com.sportsapp.domain.mcp

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.common.Permission
import com.sportsapp.domain.common.PermissionRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
    val permissionRepository = mockk<PermissionRepository>()
    val passwordEncoder = mockk<PasswordEncoder>()

    val domainService = McpTokenDomainService(
        mcpTokenRepository = mcpTokenRepository,
        mcpTokenScopeRepository = mcpTokenScopeRepository,
        mcpTokenCustomRepository = mcpTokenCustomRepository,
        permissionRepository = permissionRepository,
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

    fun makePermission(name: String, id: Long = 10L): Permission {
        val permission = Permission(name = name)
        val idField = Permission::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(permission, id)
        val superclass = permission.javaClass.superclass
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            val field = superclass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(permission, ZonedDateTime.now())
        }
        return permission
    }

    Given("[U-01] 유효한 scope 목록으로 토큰 발급을 요청하면") {
        val command = IssueMcpTokenCommand(
            userId = 1L,
            name = "my-token",
            scopes = listOf("read:facility"),
            expiresAt = null,
        )
        val permission = makePermission("mcp.facility.read.own", 10L)
        val savedToken = makeToken(userId = 1L, id = 1L)

        every { permissionRepository.findByName("mcp.facility.read.own") } returns permission
        every { passwordEncoder.encode(any()) } returns "bcrypt-hashed"
        every { mcpTokenRepository.save(any()) } returns savedToken
        every { mcpTokenScopeRepository.save(any()) } answers { firstArg() }

        When("issueToken을 호출하면") {
            val result = domainService.issueToken(command)

            Then("[U-01] 평문 토큰이 포함된 결과가 반환되고 저장은 해시로 이루어진다") {
                result.plainToken.shouldHaveMinLength(10)
                result.token shouldBe savedToken
                verify(exactly = 1) { passwordEncoder.encode(any()) }
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

        every { permissionRepository.findByName("mcp.nonexistent.read.own") } returns null

        When("issueToken을 호출하면") {
            Then("[U-02] McpScopeNotFoundException이 발생한다") {
                shouldThrow<McpScopeNotFoundException> {
                    domainService.issueToken(command)
                }
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
})
