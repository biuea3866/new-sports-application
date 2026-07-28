package com.sportsapp.domain.user.service

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.entity.UserRole
import com.sportsapp.domain.user.repository.RoleRepository
import com.sportsapp.domain.user.repository.UserRepository
import com.sportsapp.domain.user.repository.UserRoleRepository
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * B2B 파트너 연동 전용 대리 계정(연동 User) 프로비저닝 도메인 서비스.
 *
 * [PH0-08] T1 SAGA — `CreatePartnerUseCase` 의 tx1(provision)·tx3(activate) 로컬 트랜잭션과
 * tx2 실패 시의 보상(abandon)을 담당한다. 연동 User는 실제 로그인 계정이 아니라
 * goods/ticketing 코어 UseCase 가 요구하는 ownerUserId 를 만족시키기 위한 대리 계정이며,
 * INACTIVE 로 생성되어 `AuthDomainService` 의 로그인 semantic lock 이 걸린다.
 */
@Service
class IntegrationAccountDomainService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val userRoleRepository: UserRoleRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    private val secureRandom = SecureRandom()

    /**
     * tx1 — INACTIVE 연동 User 를 생성하고 [roleNames] 를 부여한다. 한 트랜잭션.
     */
    @Transactional
    fun provision(adminId: Long, roleNames: List<String>): User {
        val user = User.createInactive(generateSyntheticEmail(), passwordEncoder.encode(generateRandomPassword()))
        val savedUser = userRepository.save(user)
        roleNames.forEach { roleName -> assignRole(adminId, savedUser.id, roleName) }
        return savedUser
    }

    /**
     * tx3 — INACTIVE -> ACTIVE. 이미 ACTIVE 면 멱등, SUSPENDED 면 전이 거부(User.activate 위임).
     */
    @Transactional
    fun activate(userId: Long) {
        val user = userRepository.findById(userId) ?: throw ResourceNotFoundException("User", userId)
        user.activate()
        userRepository.save(user)
    }

    /**
     * tx2 실패 보상 — INACTIVE 유지 확정. User는 tx1에서 이미 INACTIVE로 커밋돼 있으므로
     * 추가 쓰기가 필요 없다. 존재하지 않아도(멱등) 예외를 던지지 않는다.
     */
    @Transactional
    fun abandon(userId: Long) {
        userRepository.findById(userId) ?: return
    }

    private fun assignRole(adminId: Long, userId: Long, roleName: String) {
        val role = roleRepository.findByName(roleName) ?: throw ResourceNotFoundException("Role", roleName)
        userRoleRepository.save(UserRole(userId = userId, roleId = role.id, grantedBy = adminId))
    }

    private fun generateSyntheticEmail(): String =
        "partner+${UUID.randomUUID()}@integration.local"

    private fun generateRandomPassword(): String {
        val bytes = ByteArray(PASSWORD_BYTE_LENGTH)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        const val PASSWORD_BYTE_LENGTH = 32
    }
}
