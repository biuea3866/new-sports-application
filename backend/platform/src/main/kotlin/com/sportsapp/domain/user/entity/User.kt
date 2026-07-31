package com.sportsapp.domain.user.entity

import com.sportsapp.domain.common.JpaAuditingBase
import com.sportsapp.domain.common.UserRoleName
import com.sportsapp.domain.user.exception.DuplicateRoleException
import com.sportsapp.domain.user.exception.InvalidCredentialsException
import com.sportsapp.domain.user.exception.InvalidEmailException
import com.sportsapp.domain.user.exception.InvalidUserStatusTransitionException
import com.sportsapp.domain.user.exception.SelfRevocationException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class User(
    @Column(name = "email", nullable = false, unique = true, length = 320)
    val email: String,
    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String,
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: UserStatus,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    companion object {
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun create(email: String, passwordHash: String): User {
            if (!EMAIL_REGEX.matches(email)) throw InvalidEmailException(email)
            return User(
                email = email,
                passwordHash = passwordHash,
                status = UserStatus.ACTIVE,
            )
        }

        /**
         * 연동(파트너 대리 계정) 전용 비활성 생성 팩토리.
         * `IntegrationAccountDomainService.provision` 의 tx1에서 사용한다 — 뒤이은 tx2(Partner 생성)가
         * 실패해도 이 User는 로그인이 거부되는 INACTIVE 상태로 남아 무해하다 (semantic lock).
         */
        fun createInactive(email: String, passwordHash: String): User {
            if (!EMAIL_REGEX.matches(email)) throw InvalidEmailException(email)
            return User(
                email = email,
                passwordHash = passwordHash,
                status = UserStatus.INACTIVE,
            )
        }
    }

    fun canAccess(resourceOwnerId: Long): Boolean = id == resourceOwnerId

    fun changePassword(newPasswordHash: String) {
        passwordHash = newPasswordHash
    }

    /**
     * INACTIVE -> ACTIVE 전이. 이미 ACTIVE면 멱등하게 no-op. SUSPENDED면 전이를 거부한다.
     * `IntegrationAccountDomainService.activate` (tx3) 가 호출한다.
     */
    fun activate() {
        when (status) {
            UserStatus.ACTIVE -> return
            UserStatus.INACTIVE -> status = UserStatus.ACTIVE
            UserStatus.SUSPENDED -> throw InvalidUserStatusTransitionException(id, status, UserStatus.ACTIVE)
        }
    }

    /**
     * 로그인 semantic lock — ACTIVE 가 아닌 계정(INACTIVE/SUSPENDED)의 로그인을 거부한다.
     * 계정 상태를 노출하지 않기 위해 자격 증명 오류와 동일한 예외를 사용한다.
     */
    fun validateActiveForLogin() {
        if (status != UserStatus.ACTIVE) throw InvalidCredentialsException()
    }

    fun validateNoDuplicateRole(roleId: Long, existingRoleIds: Set<Long>) {
        if (roleId in existingRoleIds) throw DuplicateRoleException(roleId)
    }

    fun validateCanRevokeAdminRole(targetRole: UserRoleName?, requesterId: Long) {
        if (targetRole == UserRoleName.ADMIN && id == requesterId) throw SelfRevocationException()
    }

    fun validateHasMinimumOneRole(activeRoleCount: Int) {
        require(activeRoleCount > 1) { "User must retain at least one role" }
    }
}
