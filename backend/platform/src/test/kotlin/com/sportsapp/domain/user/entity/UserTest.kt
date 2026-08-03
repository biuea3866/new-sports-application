package com.sportsapp.domain.user.entity

import com.sportsapp.domain.user.exception.InvalidCredentialsException
import com.sportsapp.domain.user.exception.InvalidEmailException
import com.sportsapp.domain.user.exception.InvalidUserStatusTransitionException
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import io.mockk.every
import io.mockk.mockk

class UserTest : BehaviorSpec({

    Given("잘못된 이메일 형식") {
        When("User.create 를 호출하면") {
            Then("[U-01] InvalidEmailException 을 던진다") {
                shouldThrow<InvalidEmailException> {
                    User.create("not-an-email", "hash", "닉네임")
                }
                shouldThrow<InvalidEmailException> {
                    User.create("missing-at.com", "hash", "닉네임")
                }
                shouldThrow<InvalidEmailException> {
                    User.create("", "hash", "닉네임")
                }
            }
        }
    }

    Given("특정 id 를 가진 User") {
        val user = User(
            email = "owner@example.com",
            passwordHash = "hash",
            status = UserStatus.ACTIVE,
        )

        When("canAccess 를 본인 id 로 호출하면") {
            Then("[U-03] true 를 반환한다") {
                // id=0 (default) 으로 동일 id 검증
                user.canAccess(0L) shouldBe true
            }
        }

        When("canAccess 를 타인 id 로 호출하면") {
            Then("[U-03] false 를 반환한다") {
                user.canAccess(99L) shouldBe false
            }
        }
    }

    Given("Mock PasswordEncoder 를 통해 생성된 해시") {
        val encoder = mockk<(String) -> String>()
        every { encoder("newPassword") } returns "\$2a\$10\$" + "x".repeat(53)

        val user = User.create("pw@example.com", "oldHash", "닉네임")

        When("changePassword 를 호출하면") {
            val newHash = encoder("newPassword")
            user.changePassword(newHash)
            Then("[U-04] passwordHash 가 60자 BCrypt 포맷으로 변경된다") {
                user.passwordHash shouldHaveLength 60
            }
        }
    }

    Given("연동 계정용 이메일과 비밀번호 해시가 주어졌을 때") {
        When("createInactive 를 호출하면") {
            val user = User.createInactive("partner+abc@integration.local", "hashed-password")

            Then("INACTIVE 상태의 User 가 생성된다") {
                user.status shouldBe UserStatus.INACTIVE
            }
        }

        When("잘못된 이메일 형식으로 createInactive 를 호출하면") {
            Then("InvalidEmailException 을 던진다") {
                shouldThrow<InvalidEmailException> {
                    User.createInactive("not-an-email", "hashed-password")
                }
            }
        }
    }

    Given("INACTIVE 상태의 User 가 있을 때") {
        val user = User.createInactive("partner+activate@integration.local", "hashed-password")

        When("activate 를 호출하면") {
            user.activate()

            Then("ACTIVE 상태로 전이된다") {
                user.status shouldBe UserStatus.ACTIVE
            }
        }
    }

    Given("이미 ACTIVE 상태인 User 가 있을 때") {
        val user = User.create("already-active@example.com", "hashed-password", "닉네임")

        When("activate 를 다시 호출하면") {
            Then("예외 없이 ACTIVE 상태가 유지된다 (멱등)") {
                shouldNotThrowAny { user.activate() }
                user.status shouldBe UserStatus.ACTIVE
            }
        }
    }

    Given("SUSPENDED 상태의 User 가 있을 때") {
        val user = User(
            email = "suspended@example.com",
            passwordHash = "hashed-password",
            status = UserStatus.SUSPENDED,
        )

        When("activate 를 호출하면") {
            Then("InvalidUserStatusTransitionException 이 발생하고 상태가 유지된다") {
                shouldThrow<InvalidUserStatusTransitionException> { user.activate() }
                user.status shouldBe UserStatus.SUSPENDED
            }
        }
    }

    Given("ACTIVE 상태의 User 가 있을 때") {
        val user = User.create("login-active@example.com", "hashed-password", "닉네임")

        When("validateActiveForLogin 을 호출하면") {
            Then("예외 없이 통과한다") {
                shouldNotThrowAny { user.validateActiveForLogin() }
            }
        }
    }

    Given("INACTIVE 상태의 연동 User 가 있을 때") {
        val user = User.createInactive("partner+login@integration.local", "hashed-password")

        When("validateActiveForLogin 을 호출하면") {
            Then("InvalidCredentialsException 이 발생한다") {
                shouldThrow<InvalidCredentialsException> { user.validateActiveForLogin() }
            }
        }
    }

    Given("SUSPENDED 상태의 User 로 로그인 가능 여부를 검증할 때") {
        val user = User(
            email = "suspended-login@example.com",
            passwordHash = "hashed-password",
            status = UserStatus.SUSPENDED,
        )

        When("validateActiveForLogin 을 호출하면") {
            Then("InvalidCredentialsException 이 발생한다") {
                shouldThrow<InvalidCredentialsException> { user.validateActiveForLogin() }
            }
        }
    }
})
