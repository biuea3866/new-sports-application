package com.sportsapp.domain.user

import com.sportsapp.domain.user.exceptions.InvalidCredentialsException
import com.sportsapp.domain.user.exceptions.InvalidRefreshTokenException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class AuthDomainServiceTest : BehaviorSpec({

    val userRepository = mockk<UserRepository>()
    val userDomainService = mockk<UserDomainService>()
    val jwtIssuer = mockk<JwtIssuer>()
    val refreshTokenRepository = mockk<RefreshTokenRepository>()
    val jwtBlacklistStore = mockk<JwtBlacklistStore>()
    val passwordEncoder = BCryptPasswordEncoder()

    val authDomainService = AuthDomainService(
        userRepository = userRepository,
        userDomainService = userDomainService,
        jwtIssuer = jwtIssuer,
        refreshTokenRepository = refreshTokenRepository,
        passwordEncoder = passwordEncoder,
        jwtBlacklistStore = jwtBlacklistStore,
    )

    val rawPassword = "password1234"
    val hashedPassword = passwordEncoder.encode(rawPassword)

    val testUser = User(
        email = "test@example.com",
        passwordHash = hashedPassword,
        status = UserStatus.ACTIVE,
    )

    Given("올바른 이메일과 비밀번호로 로그인 시도 시") {
        every { userRepository.findByEmail("test@example.com") } returns testUser
        every { userDomainService.getRolesForUser(any()) } returns emptyList()
        every { jwtIssuer.generateAccessToken(any(), any(), any()) } returns "access-token"
        every { jwtIssuer.generateRefreshToken() } returns "refresh-token"
        every { jwtIssuer.accessTokenExpiresInSeconds() } returns 1800L
        every { refreshTokenRepository.save(any(), any()) } returns Unit

        When("authenticate 를 호출하면") {
            val tokenPair = authDomainService.authenticate("test@example.com", rawPassword)

            Then("[U-01 happy path] TokenPair 가 반환된다") {
                tokenPair.accessToken shouldBe "access-token"
                tokenPair.refreshToken shouldBe "refresh-token"
                tokenPair.accessTokenExpiresIn shouldBe 1800L
            }
        }
    }

    Given("잘못된 비밀번호로 로그인 시도 시") {
        every { userRepository.findByEmail("test@example.com") } returns testUser

        When("authenticate 를 호출하면") {
            Then("[U-01] InvalidCredentialsException 이 발생한다") {
                shouldThrow<InvalidCredentialsException> {
                    authDomainService.authenticate("test@example.com", "wrong-password")
                }
            }
        }
    }

    Given("존재하지 않는 이메일로 로그인 시도 시") {
        every { userRepository.findByEmail("notfound@example.com") } returns null

        When("authenticate 를 호출하면") {
            Then("[U-01] InvalidCredentialsException 이 발생한다") {
                shouldThrow<InvalidCredentialsException> {
                    authDomainService.authenticate("notfound@example.com", rawPassword)
                }
            }
        }
    }

    Given("유효한 Refresh Token 으로 재발급 시도 시") {
        every { refreshTokenRepository.findUserIdByToken("valid-refresh-token") } returns 1L
        every { userRepository.findById(1L) } returns testUser
        every { userDomainService.getRolesForUser(1L) } returns emptyList()
        every { jwtIssuer.generateAccessToken(any(), any(), any()) } returns "new-access-token"
        every { jwtIssuer.generateRefreshToken() } returns "new-refresh-token"
        every { jwtIssuer.accessTokenExpiresInSeconds() } returns 1800L
        every { refreshTokenRepository.invalidate("valid-refresh-token") } returns Unit
        every { refreshTokenRepository.save(any(), any()) } returns Unit

        When("refresh 를 호출하면") {
            val tokenPair = authDomainService.refresh("valid-refresh-token")

            Then("[U-03] 기존 Refresh Token 이 삭제되고 새 TokenPair 가 반환된다") {
                tokenPair.accessToken shouldBe "new-access-token"
                tokenPair.refreshToken shouldBe "new-refresh-token"
                tokenPair shouldNotBe null
                verify(exactly = 1) { refreshTokenRepository.invalidate("valid-refresh-token") }
                verify(exactly = 1) { refreshTokenRepository.save(any(), "new-refresh-token") }
            }
        }
    }

    Given("Redis 에 존재하지 않는 Refresh Token 으로 재발급 시도 시") {
        every { refreshTokenRepository.findUserIdByToken("nonexistent-token") } returns null

        When("refresh 를 호출하면") {
            Then("[U-03] InvalidRefreshTokenException 이 발생한다") {
                shouldThrow<InvalidRefreshTokenException> {
                    authDomainService.refresh("nonexistent-token")
                }
            }
        }
    }
})
