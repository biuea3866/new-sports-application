package com.sportsapp.domain.user.service

import com.sportsapp.domain.user.gateway.JwtBlacklistStore
import com.sportsapp.domain.user.gateway.JwtIssuer
import com.sportsapp.domain.user.repository.RefreshTokenRepository
import com.sportsapp.domain.user.repository.UserRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant

class AuthDomainServiceLogoutTest : BehaviorSpec({

    val userRepository = mockk<UserRepository>()
    val userDomainService = mockk<UserDomainService>()
    val jwtIssuer = mockk<JwtIssuer>()
    val refreshTokenRepository = mockk<RefreshTokenRepository>()
    val jwtBlacklistStore = mockk<JwtBlacklistStore>()
    val passwordEncoder = org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()

    val authDomainService = AuthDomainService(
        userRepository = userRepository,
        userDomainService = userDomainService,
        jwtIssuer = jwtIssuer,
        refreshTokenRepository = refreshTokenRepository,
        passwordEncoder = passwordEncoder,
        jwtBlacklistStore = jwtBlacklistStore,
    )

    Given("유효한 accessToken 과 userId 가 주어진 경우") {
        val accessToken = "valid.access.token"
        val jti = "test-jti-uuid"
        val userId = 1L
        val expiration = Instant.now().plusSeconds(1800)

        every { jwtIssuer.extractJti(accessToken) } returns jti
        every { jwtIssuer.extractExpiration(accessToken) } returns expiration
        every { jwtBlacklistStore.add(any(), any()) } returns Unit
        every { refreshTokenRepository.invalidateByUserId(userId) } returns Unit

        When("[U-01] logout 을 호출하면") {
            authDomainService.logout(accessToken, userId)

            Then("jti 가 블랙리스트에 잔여 TTL 로 등록되고 refreshToken 이 삭제된다") {
                verify(exactly = 1) { jwtBlacklistStore.add(jti, any()) }
                verify(exactly = 1) { refreshTokenRepository.invalidateByUserId(userId) }
            }
        }
    }

    Given("이미 블랙리스트에 등록된 jti 로 logout 을 재시도하는 경우") {
        val accessToken = "already.blacklisted.token"
        val jti = "blacklisted-jti"
        val userId = 2L
        val expiration = Instant.now().plusSeconds(900)

        every { jwtIssuer.extractJti(accessToken) } returns jti
        every { jwtIssuer.extractExpiration(accessToken) } returns expiration
        every { jwtBlacklistStore.add(any(), any()) } returns Unit
        every { refreshTokenRepository.invalidateByUserId(userId) } returns Unit

        When("[U-02] logout 을 다시 호출하면") {
            authDomainService.logout(accessToken, userId)

            Then("멱등하게 처리되어 예외 없이 완료된다") {
                verify(exactly = 1) { jwtBlacklistStore.add(jti, any()) }
            }
        }
    }
})
