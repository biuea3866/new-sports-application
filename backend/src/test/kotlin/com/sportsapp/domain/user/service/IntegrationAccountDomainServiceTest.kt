package com.sportsapp.domain.user.service

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.user.entity.Role
import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.entity.UserRole
import com.sportsapp.domain.user.entity.UserStatus
import com.sportsapp.domain.user.exception.InvalidUserStatusTransitionException
import com.sportsapp.domain.user.repository.RoleRepository
import com.sportsapp.domain.user.repository.UserRepository
import com.sportsapp.domain.user.repository.UserRoleRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * PH0-08 T1 SAGA — 연동 전용 대리 계정 프로비저닝 도메인 서비스.
 * tx1(provision) / tx3(activate) / 보상(abandon) 각각을 독립 트랜잭션 경계 메서드로 검증한다.
 *
 * Given 블록마다 Mock·서비스 인스턴스를 새로 생성한다 — BehaviorSpec(SingleInstance)이 스펙
 * 인스턴스를 하나만 만들어 모든 Given 블록이 순차 실행되므로, mock을 루트에서 공유하면
 * 이전 Given의 호출 이력이 뒤 Given의 verify(exactly=0) 단언을 오염시킨다.
 */
class IntegrationAccountDomainServiceTest : BehaviorSpec({

    fun newService(
        userRepository: UserRepository = mockk(),
        roleRepository: RoleRepository = mockk(),
        userRoleRepository: UserRoleRepository = mockk(),
        passwordEncoder: PasswordEncoder = mockk(),
    ) = IntegrationAccountDomainService(
        userRepository = userRepository,
        roleRepository = roleRepository,
        userRoleRepository = userRoleRepository,
        passwordEncoder = passwordEncoder,
    )

    val goodsSellerRole = Role(name = "GOODS_SELLER")
    val eventHostRole = Role(name = "EVENT_HOST")

    Given("adminId 와 부여할 role 목록이 주어졌을 때") {
        val userRepository = mockk<UserRepository>()
        val roleRepository = mockk<RoleRepository>()
        val userRoleRepository = mockk<UserRoleRepository>()
        val passwordEncoder = mockk<PasswordEncoder>()
        val service = newService(userRepository, roleRepository, userRoleRepository, passwordEncoder)

        every { passwordEncoder.encode(any()) } returns "hashed-random-password"
        val savedUserSlot = slot<User>()
        every { userRepository.save(capture(savedUserSlot)) } answers { savedUserSlot.captured }
        every { roleRepository.findByName("GOODS_SELLER") } returns goodsSellerRole
        every { roleRepository.findByName("EVENT_HOST") } returns eventHostRole
        every { userRoleRepository.save(any()) } returns UserRole(userId = 0L, roleId = 0L, grantedBy = null)

        When("provision 을 호출하면") {
            val user = service.provision(adminId = 1L, roleNames = listOf("GOODS_SELLER", "EVENT_HOST"))

            Then("INACTIVE 상태의 User 가 생성되어 저장된다") {
                user.status shouldBe UserStatus.INACTIVE
                verify(exactly = 1) { userRepository.save(any()) }
            }

            Then("이메일은 partner+ 로 시작하는 연동 전용 형식이다") {
                savedUserSlot.captured.email.startsWith("partner+") shouldBe true
                savedUserSlot.captured.email.endsWith("@integration.local") shouldBe true
            }

            Then("전달된 두 role 이 모두 조회되어 부여된다") {
                verify(exactly = 1) { roleRepository.findByName("GOODS_SELLER") }
                verify(exactly = 1) { roleRepository.findByName("EVENT_HOST") }
                verify(exactly = 2) { userRoleRepository.save(any()) }
            }
        }
    }

    Given("존재하지 않는 role 이름으로 provision 을 호출하면") {
        val userRepository = mockk<UserRepository>()
        val roleRepository = mockk<RoleRepository>()
        val userRoleRepository = mockk<UserRoleRepository>()
        val passwordEncoder = mockk<PasswordEncoder>()
        val service = newService(userRepository, roleRepository, userRoleRepository, passwordEncoder)

        every { passwordEncoder.encode(any()) } returns "hashed-random-password"
        every { userRepository.save(any()) } answers { firstArg() }
        every { roleRepository.findByName("UNKNOWN_ROLE") } returns null

        When("provision 을 호출하면") {
            Then("ResourceNotFoundException 이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    service.provision(adminId = 1L, roleNames = listOf("UNKNOWN_ROLE"))
                }
            }
        }
    }

    Given("두 번 연속 provision 을 호출했을 때") {
        val userRepository = mockk<UserRepository>()
        val roleRepository = mockk<RoleRepository>()
        val userRoleRepository = mockk<UserRoleRepository>()
        val passwordEncoder = mockk<PasswordEncoder>()
        val service = newService(userRepository, roleRepository, userRoleRepository, passwordEncoder)

        every { passwordEncoder.encode(any()) } returns "hashed-random-password"
        val slots: CapturingSlot<User> = slot()
        val savedEmails = mutableListOf<String>()
        every { userRepository.save(capture(slots)) } answers {
            savedEmails.add(slots.captured.email)
            slots.captured
        }
        every { roleRepository.findByName(any<String>()) } returns goodsSellerRole
        every { userRoleRepository.save(any()) } returns UserRole(userId = 0L, roleId = 0L, grantedBy = null)

        When("provision 을 두 번 호출하면") {
            service.provision(adminId = 1L, roleNames = listOf("GOODS_SELLER"))
            service.provision(adminId = 1L, roleNames = listOf("GOODS_SELLER"))

            Then("생성되는 연동 계정 이메일이 매번 유일하다") {
                savedEmails.size shouldBe 2
                savedEmails[0] shouldNotBe savedEmails[1]
            }
        }
    }

    Given("INACTIVE 상태의 연동 User 가 존재할 때") {
        val userRepository = mockk<UserRepository>()
        val service = newService(userRepository = userRepository)

        val userId = 10L
        val inactiveUser = User.createInactive("partner+activate@integration.local", "hashed-password")
        every { userRepository.findById(userId) } returns inactiveUser
        every { userRepository.save(any()) } answers { firstArg() }

        When("activate 를 호출하면") {
            service.activate(userId)

            Then("User 가 ACTIVE 로 전이되어 저장된다") {
                inactiveUser.status shouldBe UserStatus.ACTIVE
                verify(exactly = 1) { userRepository.save(inactiveUser) }
            }
        }
    }

    Given("존재하지 않는 userId 로 activate 를 호출하면") {
        val userRepository = mockk<UserRepository>()
        val service = newService(userRepository = userRepository)
        every { userRepository.findById(999L) } returns null

        When("activate 를 호출하면") {
            Then("ResourceNotFoundException 이 발생한다") {
                shouldThrow<ResourceNotFoundException> { service.activate(999L) }
            }
        }
    }

    Given("SUSPENDED 상태의 User 에 activate 를 호출하면") {
        val userRepository = mockk<UserRepository>()
        val service = newService(userRepository = userRepository)

        val userId = 11L
        val suspendedUser = User(
            email = "suspended@example.com",
            passwordHash = "hashed-password",
            status = UserStatus.SUSPENDED,
        )
        every { userRepository.findById(userId) } returns suspendedUser

        When("activate 를 호출하면") {
            Then("InvalidUserStatusTransitionException 이 발생하고 저장되지 않는다") {
                shouldThrow<InvalidUserStatusTransitionException> { service.activate(userId) }
                verify(exactly = 0) { userRepository.save(any()) }
            }
        }
    }

    Given("INACTIVE 상태로 잔존한 연동 User 가 있을 때") {
        val userRepository = mockk<UserRepository>()
        val service = newService(userRepository = userRepository)

        val userId = 12L
        val inactiveUser = User.createInactive("partner+abandon@integration.local", "hashed-password")
        every { userRepository.findById(userId) } returns inactiveUser

        When("abandon 을 호출하면") {
            service.abandon(userId)

            Then("추가 쓰기 없이 멱등하게 완료된다") {
                verify(exactly = 0) { userRepository.save(any()) }
            }
        }
    }

    Given("이미 삭제되었거나 존재하지 않는 userId 로 abandon 을 호출하면") {
        val userRepository = mockk<UserRepository>()
        val service = newService(userRepository = userRepository)
        every { userRepository.findById(404L) } returns null

        When("abandon 을 호출하면") {
            Then("예외 없이 멱등하게 완료된다") {
                service.abandon(404L)
            }
        }
    }
})
