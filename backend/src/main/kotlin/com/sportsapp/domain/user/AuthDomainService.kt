package com.sportsapp.domain.user

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.user.exceptions.InvalidCredentialsException
import com.sportsapp.domain.user.exceptions.InvalidRefreshTokenException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthDomainService(
    private val userRepository: UserRepository,
    private val userDomainService: UserDomainService,
    private val jwtIssuer: JwtIssuer,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    fun authenticate(email: String, rawPassword: String): TokenPair {
        val user = userRepository.findByEmail(email) ?: throw InvalidCredentialsException()
        if (!passwordEncoder.matches(rawPassword, user.passwordHash)) throw InvalidCredentialsException()
        val roles = userDomainService.getRolesForUser(user.id).map { it.name }
        return issueTokenPair(user.id, user.email, roles)
    }

    fun refresh(incomingRefreshToken: String): TokenPair {
        val userId = refreshTokenRepository.findUserIdByToken(incomingRefreshToken)
            ?: throw InvalidRefreshTokenException()
        val user = userRepository.findById(userId) ?: throw ResourceNotFoundException("User", userId)
        val roles = userDomainService.getRolesForUser(userId).map { it.name }
        refreshTokenRepository.invalidate(incomingRefreshToken)
        return issueTokenPair(user.id, user.email, roles)
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
