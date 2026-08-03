package com.sportsapp.domain.user.service

import com.sportsapp.domain.common.UserRoleName
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.user.entity.Role
import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.entity.UserRole
import com.sportsapp.domain.user.entity.UserStatus
import com.sportsapp.domain.user.repository.RoleRepository
import com.sportsapp.domain.user.repository.UserCustomRepository
import com.sportsapp.domain.user.repository.UserRepository
import com.sportsapp.domain.user.repository.UserRoleRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

/**
 * 닉네임 가입·수정과 표시 이름 일괄 조회. 표시 이름 일괄 조회는 소셜/모집 컨텍스트가
 * 목록 화면에서 N+1 없이 작성자·방장·초대자·신청자 이름을 얻는 유일한 진입점이다.
 */
class UserDomainServiceNicknameTest : BehaviorSpec({

    val userRepository = mockk<UserRepository>()
    val userCustomRepository = mockk<UserCustomRepository>()
    val roleRepository = mockk<RoleRepository>()
    val userRoleRepository = mockk<UserRoleRepository>()

    val userDomainService = UserDomainService(
        userRepository = userRepository,
        userCustomRepository = userCustomRepository,
        roleRepository = roleRepository,
        userRoleRepository = userRoleRepository,
        passwordEncoder = BCryptPasswordEncoder(),
    )

    Given("닉네임을 포함한 가입 요청") {
        val savedUserSlot = slot<User>()
        every { userRepository.findByEmail("new@example.com") } returns null
        every { userRepository.save(capture(savedUserSlot)) } answers { savedUserSlot.captured }
        every { roleRepository.findByName(UserRoleName.USER) } returns Role(name = "USER")
        every { userRoleRepository.existsByUserIdAndRoleId(any(), any()) } returns false
        every { userRoleRepository.save(any()) } returns UserRole(userId = 0L, roleId = 0L, grantedBy = null)

        When("register 를 호출하면") {
            val user = userDomainService.register("new@example.com", "password1234", "김철수")

            Then("닉네임이 저장된다") {
                user.nickname shouldBe "김철수"
                savedUserSlot.captured.nickname shouldBe "김철수"
            }
        }
    }

    Given("이미 같은 닉네임을 쓰는 사용자가 있는 상황") {
        val savedUserSlot = slot<User>()
        every { userRepository.findByEmail("another@example.com") } returns null
        every { userRepository.save(capture(savedUserSlot)) } answers { savedUserSlot.captured }
        every { roleRepository.findByName(UserRoleName.USER) } returns Role(name = "USER")
        every { userRoleRepository.existsByUserIdAndRoleId(any(), any()) } returns false
        every { userRoleRepository.save(any()) } returns UserRole(userId = 0L, roleId = 0L, grantedBy = null)

        When("동일한 닉네임으로 가입하면") {
            val user = userDomainService.register("another@example.com", "password1234", "김철수")

            Then("닉네임 중복은 허용된다 (이메일만 중복 검사한다)") {
                user.nickname shouldBe "김철수"
                verify(exactly = 1) { userRepository.findByEmail("another@example.com") }
            }
        }
    }

    Given("가입된 사용자") {
        val user = User.create("edit@example.com", "hash", "이전닉네임")
        every { userRepository.findById(7L) } returns user
        every { userRepository.save(user) } returns user

        When("changeNickname 을 호출하면") {
            val changed = userDomainService.changeNickname(7L, "새로운닉네임")

            Then("닉네임이 교체되고 저장된다") {
                changed.nickname shouldBe "새로운닉네임"
                verify(exactly = 1) { userRepository.save(user) }
            }
        }
    }

    Given("존재하지 않는 사용자") {
        every { userRepository.findById(404L) } returns null

        When("changeNickname 을 호출하면") {
            Then("ResourceNotFoundException 을 던진다") {
                shouldThrow<ResourceNotFoundException> {
                    userDomainService.changeNickname(404L, "새로운닉네임")
                }
            }
        }
    }

    Given("여러 사용자 id 목록") {
        val nicknamedUser = mockk<User>()
        every { nicknamedUser.id } returns 1L
        every { nicknamedUser.displayName } returns "김철수"
        val unsetNicknameUser = mockk<User>()
        every { unsetNicknameUser.id } returns 2L
        every { unsetNicknameUser.displayName } returns User.UNSET_NICKNAME_DISPLAY_NAME
        every { userRepository.findAllBy(listOf(1L, 2L, 3L)) } returns listOf(nicknamedUser, unsetNicknameUser)

        When("findDisplayNamesBy 를 호출하면") {
            val displayNames = userDomainService.findDisplayNamesBy(listOf(1L, 2L, 3L))

            Then("Repository 를 한 번만 호출한다 (N+1 없음)") {
                verify(exactly = 1) { userRepository.findAllBy(listOf(1L, 2L, 3L)) }
            }

            Then("닉네임이 있는 사용자는 닉네임을 반환한다") {
                displayNames.of(1L) shouldBe "김철수"
            }

            Then("닉네임이 없는 사용자와 조회되지 않은 id 는 기본 표시 이름을 반환한다") {
                displayNames.of(2L) shouldBe User.UNSET_NICKNAME_DISPLAY_NAME
                displayNames.of(999L) shouldBe User.UNSET_NICKNAME_DISPLAY_NAME
            }
        }
    }

    Given("빈 id 목록") {
        When("findDisplayNamesBy 를 호출하면") {
            val displayNames = userDomainService.findDisplayNamesBy(emptyList())

            Then("Repository 를 호출하지 않는다") {
                displayNames.of(1L) shouldBe User.UNSET_NICKNAME_DISPLAY_NAME
                verify(exactly = 0) { userRepository.findAllBy(emptyList()) }
            }
        }
    }
})
