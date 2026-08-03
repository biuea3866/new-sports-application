package com.sportsapp.domain.user.entity

import com.sportsapp.domain.common.JpaAuditingBase
import com.sportsapp.domain.common.UserRoleName
import com.sportsapp.domain.user.exception.DuplicateRoleException
import com.sportsapp.domain.user.exception.InvalidCredentialsException
import com.sportsapp.domain.user.exception.InvalidEmailException
import com.sportsapp.domain.user.exception.InvalidNicknameException
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

    /**
     * 사람이 읽는 표시 이름. 닉네임 도입(V64) 이전에 가입한 계정과 연동 대리 계정(createInactive)은
     * null 이며, 표시에는 [displayName] 을 쓴다 — 이메일·내부 id 는 소셜 화면에 노출하지 않는다.
     * 변경은 [changeNickname] 으로만 가능하다(검증 우회 방지).
     */
    @Column(name = "nickname", nullable = true, length = MAX_NICKNAME_LENGTH)
    var nickname: String? = null
        private set

    /**
     * 게시글 작성자·방장·초대자·신청자 등 타인에게 노출되는 이름. 닉네임 미설정 계정은
     * 개인정보(이메일)나 내부 식별자 대신 중립 기본값을 쓴다.
     */
    val displayName: String get() = nickname ?: UNSET_NICKNAME_DISPLAY_NAME

    companion object {
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        /** 한글·영문·숫자·밑줄만 허용한다. 공백·특수문자는 표시 이름 혼동(사칭)을 유발해 제외한다. */
        private val NICKNAME_REGEX = Regex("^[가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z0-9_]+$")
        const val MIN_NICKNAME_LENGTH = 2
        const val MAX_NICKNAME_LENGTH = 20

        /** 닉네임 미설정 계정의 표시 이름. */
        const val UNSET_NICKNAME_DISPLAY_NAME = "닉네임 미설정"

        fun create(email: String, passwordHash: String, nickname: String): User {
            if (!EMAIL_REGEX.matches(email)) throw InvalidEmailException(email)
            return User(
                email = email,
                passwordHash = passwordHash,
                status = UserStatus.ACTIVE,
            ).also { it.changeNickname(nickname) }
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

    /**
     * 닉네임 등록·수정. 앞뒤 공백을 제거한 뒤 길이·허용 문자를 검증한다.
     * 중복은 허용한다 — 닉네임은 식별자가 아니라 표시 이름이며, 유일성 제약은 가입 실패·경합을
     * 만들 뿐 사칭을 막지 못한다(식별은 id 로 한다).
     */
    fun changeNickname(newNickname: String) {
        val trimmedNickname = newNickname.trim()
        if (trimmedNickname.length !in MIN_NICKNAME_LENGTH..MAX_NICKNAME_LENGTH) {
            throw InvalidNicknameException(newNickname)
        }
        if (!NICKNAME_REGEX.matches(trimmedNickname)) throw InvalidNicknameException(newNickname)
        nickname = trimmedNickname
    }

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
