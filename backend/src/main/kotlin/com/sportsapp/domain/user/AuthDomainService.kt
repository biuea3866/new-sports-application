package com.sportsapp.domain.user

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.user.exceptions.InvalidCredentialsException
import com.sportsapp.domain.user.exceptions.InvalidRefreshTokenException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthDomainService(
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val roleRepository: RoleRepository,
    private val jwtIssuer: JwtIssuer,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    fun authenticate(email: String, rawPassword: String): TokenPair {
        val user = userRepository.findByEmail(email) ?: throw InvalidCredentialsException()
        if (!passwordEncoder.matches(rawPassword, user.passwordHash)) throw InvalidCredentialsException()
        val roles = resolveRoleNames(user.id)
        return issueTokenPair(user.id, user.email, roles)
    }

    fun refresh(userId: Long, incomingRefreshToken: String): TokenPair {
        validateRefreshToken(userId, incomingRefreshToken)
        val user = userRepository.findById(userId) ?: throw ResourceNotFoundException("User", userId)
        val roles = resolveRoleNames(userId)
        refreshTokenRepository.remove(userId)
        return issueTokenPair(user.id, user.email, roles)
    }

    private fun validateRefreshToken(userId: Long, incomingRefreshToken: String) {
        val storedToken = refreshTokenRepository.find(userId) ?: throw InvalidRefreshTokenException()
        if (storedToken != incomingRefreshToken) throw InvalidRefreshTokenException()
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

    private fun resolveRoleNames(userId: Long): List<String> {
        val userRoles = userRoleRepository.findActiveByUserId(userId)
        return userRoles.mapNotNull { userRole -> roleRepository.findById(userRole.roleId)?.name }
    }
}
