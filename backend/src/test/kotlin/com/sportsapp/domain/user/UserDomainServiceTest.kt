package com.sportsapp.domain.user

import com.sportsapp.domain.common.UserRoleName
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.user.exceptions.DuplicateEmailException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class UserDomainServiceTest : BehaviorSpec({

    val userRepository = mockk<UserRepository>()
    val userCustomRepository = mockk<UserCustomRepository>()
    val roleRepository = mockk<RoleRepository>()
    val userRoleRepository = mockk<UserRoleRepository>()
    val passwordEncoder = BCryptPasswordEncoder()

    val userDomainService = UserDomainService(
        userRepository = userRepository,
        userCustomRepository = userCustomRepository,
        roleRepository = roleRepository,
        userRoleRepository = userRoleRepository,
        passwordEncoder = passwordEncoder,
    )

    val defaultRole = Role(name = "USER")
    val testUser = User(
        email = "test@example.com",
        passwordHash = "\$2a\$10\$hashed",
        status = UserStatus.ACTIVE,
    )

    Given("중복되지 않은 이메일로 가입 시도 시") {
        every { userRepository.findByEmail("new@example.com") } returns null
        every { userRepository.save(any()) } returns testUser
        every { roleRepository.findByName(UserRoleName.USER) } returns defaultRole
        every { userRoleRepository.existsByUserIdAndRoleId(any(), any()) } returns false
        every { userRoleRepository.save(any()) } returns UserRole(userId = 0L, roleId = 0L, grantedBy = null)

        When("register 를 호출하면") {
            val user = userDomainService.register("new@example.com", "password1234")

            Then("[U-02] 이메일 중복 검사 → bcrypt → save 순서로 호출된다") {
                verify(exactly = 1) { userRepository.findByEmail("new@example.com") }
                verify(exactly = 1) { userRepository.save(any()) }
            }

            Then("[U-03] 기본 USER Role 이 자동 부여된다") {
                verify(exactly = 1) { roleRepository.findByName(UserRoleName.USER) }
                verify(exactly = 1) { userRoleRepository.save(any()) }
            }
        }
    }

    Given("이미 등록된 이메일로 가입 시도 시") {
        every { userRepository.findByEmail("dup@example.com") } returns testUser

        When("register 를 호출하면") {
            Then("[U-02] DuplicateEmailException 이 발생한다") {
                shouldThrow<DuplicateEmailException> {
                    userDomainService.register("dup@example.com", "password1234")
                }
            }
        }
    }

    Given("USER Role 이 존재하지 않는 상태") {
        every { userRepository.findByEmail("noRole@example.com") } returns null
        every { userRepository.save(any()) } returns testUser
        every { roleRepository.findByName(UserRoleName.USER) } returns null

        When("register 를 호출하면") {
            Then("[U-03] ResourceNotFoundException 이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    userDomainService.register("noRole@example.com", "password1234")
                }
            }
        }
    }
})
