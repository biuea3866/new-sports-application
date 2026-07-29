package com.sportsapp.domain.user.service

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.user.exception.InvalidCredentialsException
import com.sportsapp.domain.user.exception.InvalidRefreshTokenException
import com.sportsapp.domain.user.gateway.JwtBlacklistStore
import com.sportsapp.domain.user.gateway.JwtIssuer
import com.sportsapp.domain.user.repository.RefreshTokenRepository
import com.sportsapp.domain.user.repository.UserRepository
import com.sportsapp.domain.user.vo.TokenPair
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class AuthDomainService(
    private val userRepository: UserRepository,
    private val userDomainService: UserDomainService,
    private val jwtIssuer: JwtIssuer,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtBlacklistStore: JwtBlacklistStore,
) {
    fun authenticate(email: String, rawPassword: String): TokenPair {
        val user = userRepository.findByEmail(email) ?: throw InvalidCredentialsException()
        if (!passwordEncoder.matches(rawPassword, user.passwordHash)) throw InvalidCredentialsException()
        user.validateActiveForLogin()
        val roles = userDomainService.getRolesForUser(user.id).map { it.name }
        return issueTokenPair(user.id, user.email, roles)
    }

    fun refresh(incomingRefreshToken: String): TokenPair {
        val userId = refreshTokenRepository.findUserIdByToken(incomingRefreshToken)
            ?: throw InvalidRefreshTokenException()
        val user = userRepository.findById(userId) ?: throw ResourceNotFoundException("User", userId)
        // 로그인 경로와 동일한 semantic lock. 이게 없으면 SUSPENDED 로 전환된 사용자가 refresh token
        // 만료까지 access token 을 계속 발급받는다. 현재 데이터는 전원 ACTIVE 라(비-ACTIVE 사용자 0명)
        // 기존 사용자 동작 변화는 없다.
        user.validateActiveForLogin()
        val roles = userDomainService.getRolesForUser(userId).map { it.name }
        refreshTokenRepository.invalidate(incomingRefreshToken)
        return issueTokenPair(user.id, user.email, roles)
    }

    fun logout(accessToken: String, userId: Long) {
        val jti = jwtIssuer.extractJti(accessToken)
        val expiration = jwtIssuer.extractExpiration(accessToken)
        val ttl = Duration.between(Instant.now(), expiration).coerceAtLeast(Duration.ZERO)
        jwtBlacklistStore.add(jti, ttl)
        refreshTokenRepository.invalidateByUserId(userId)
    }

    private fun issueTokenPair(userId: Long, email: String, roles: List<String>): TokenPair {
        val accessToken = jwtIssuer.generateAccessToken(userId, email, roles)
        val refreshToken = jwtIssuer.generateRefreshToken()
        refreshTokenRepository.save(userId, refreshToken)
        return TokenPair(
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiresIn = jwtIssuer.accessTokenExpiresInSeconds(),
        )
    }
}
