package com.sportsapp.domain.user.service

import com.sportsapp.domain.common.UserRoleName
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.user.dto.UserDisplayNames
import com.sportsapp.domain.user.dto.UserWithRoles
import com.sportsapp.domain.user.entity.Role
import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.entity.UserRole
import com.sportsapp.domain.user.exception.DuplicateEmailException
import com.sportsapp.domain.user.repository.RoleRepository
import com.sportsapp.domain.user.repository.UserCustomRepository
import com.sportsapp.domain.user.repository.UserRepository
import com.sportsapp.domain.user.repository.UserRoleRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
// TooManyFunctions 억제 근거(W1-DEBT-01): 이 DomainService 는 타 모듈(bootstrap·edge)과 아키텍처 테스트가
// 타입을 직접 참조한다 — 책임 분리는 크로스 모듈 호출부 갱신을 동반하므로 별도 티켓으로 분리한다
// (같은 정리에서 social 의 무분별한 분리가 bootstrap 테스트 12곳을 깨뜨린 선례가 있다).
@Suppress("TooManyFunctions")
class UserDomainService(
    private val userRepository: UserRepository,
    private val userCustomRepository: UserCustomRepository,
    private val roleRepository: RoleRepository,
    private val userRoleRepository: UserRoleRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    fun register(email: String, rawPassword: String, nickname: String): User {
        if (userRepository.findByEmail(email) != null) throw DuplicateEmailException(email)
        val user = User.create(email, passwordEncoder.encode(rawPassword), nickname)
        val savedUser = userRepository.save(user)
        val defaultRole = roleRepository.findByName(UserRoleName.USER)
            ?: throw ResourceNotFoundException("Role", UserRoleName.USER)
        if (!userRoleRepository.existsByUserIdAndRoleId(savedUser.id, defaultRole.id)) {
            userRoleRepository.save(UserRole(userId = savedUser.id, roleId = defaultRole.id, grantedBy = null))
        }
        return savedUser
    }

    fun findById(userId: Long): User =
        userRepository.findById(userId) ?: throw ResourceNotFoundException("User", userId)

    /**
     * 사용자의 이메일을 조회한다. 사용자가 없으면 예외가 아니라 null 을 반환한다 ([findById] 와의 차이).
     */
    fun findEmailBy(userId: Long): String? =
        userRepository.findById(userId)?.email

    fun findByEmail(email: String): User =
        userRepository.findByEmail(email) ?: throw ResourceNotFoundException("User", email)

    /** 마이페이지 닉네임 수정. 검증은 [User.changeNickname] 이 갖는다. */
    fun changeNickname(userId: Long, nickname: String): User {
        val user = getUser(userId)
        user.changeNickname(nickname)
        return userRepository.save(user)
    }

    /**
     * 표시 이름 일괄 조회 — 다른 컨텍스트(social·recruitment)의 application 레이어가 목록 화면의
     * 작성자·방장·초대자·신청자 이름을 얻는 진입점이다. 사용자별 단건 조회(N+1)를 막기 위해
     * id 목록을 한 번에 받는다.
     */
    fun findDisplayNamesBy(userIds: Collection<Long>): UserDisplayNames =
        if (userIds.isEmpty()) UserDisplayNames.from(emptyList())
        else UserDisplayNames.from(userRepository.findAllBy(userIds.distinct()))

    /** 단건 표시 이름. 사용자가 없으면 null 을 반환한다 ([findById] 와의 차이). */
    fun findDisplayNameBy(userId: Long): String? =
        userRepository.findById(userId)?.displayName

    fun findByIdWithRoles(userId: Long): UserWithRoles =
        userCustomRepository.findByIdWithRoles(userId) ?: throw ResourceNotFoundException("User", userId)

    fun getRolesForUser(userId: Long): List<Role> {
        val userRoles = userRoleRepository.findActiveByUserId(userId)
        return userRoles.mapNotNull { userRole ->
            roleRepository.findById(userRole.roleId)
        }
    }

    fun assignRole(adminId: Long, userId: Long, roleName: String) {
        val user = getUser(userId)
        val role = getRole(roleName)
        val activeRoles = userRoleRepository.findActiveByUserId(userId)
        user.validateNoDuplicateRole(role.id, activeRoles.map { it.roleId }.toSet())
        userRoleRepository.save(UserRole(userId = userId, roleId = role.id, grantedBy = adminId))
    }

    fun revokeRole(adminId: Long, userId: Long, roleName: String) {
        val user = getUser(userId)
        val role = getRole(roleName)
        user.validateCanRevokeAdminRole(
            targetRole = UserRoleName.fromNameOrNull(roleName),
            requesterId = adminId,
        )
        val activeRoles = userRoleRepository.findActiveByUserId(userId)
        user.validateHasMinimumOneRole(activeRoles.size)
        userRoleRepository.findActiveByUserIdAndRoleId(userId, role.id)
            ?: throw ResourceNotFoundException("UserRole", "$userId/$roleName")
        userRoleRepository.softDeleteByUserIdAndRoleId(userId, role.id, adminId)
    }

    fun listUsers(
        emailKeyword: String?,
        roleName: String?,
        pageable: Pageable,
    ): Page<UserWithRoles> =
        userCustomRepository.findAllWithRoles(
            emailKeyword = emailKeyword,
            roleName = roleName,
            pageable = pageable,
        )

    private fun getUser(userId: Long): User =
        userRepository.findById(userId) ?: throw ResourceNotFoundException("User", userId)

    private fun getRole(roleName: String): Role =
        roleRepository.findByName(roleName) ?: throw ResourceNotFoundException("Role", roleName)
}
