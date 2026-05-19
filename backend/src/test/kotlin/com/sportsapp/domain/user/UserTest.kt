package com.sportsapp.domain.user

import com.sportsapp.domain.user.exceptions.InvalidEmailException
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
                    User.create("not-an-email", "hash")
                }
                shouldThrow<InvalidEmailException> {
                    User.create("missing-at.com", "hash")
                }
                shouldThrow<InvalidEmailException> {
                    User.create("", "hash")
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

        val user = User.create("pw@example.com", "oldHash")

        When("changePassword 를 호출하면") {
            val newHash = encoder("newPassword")
            user.changePassword(newHash)
            Then("[U-04] passwordHash 가 60자 BCrypt 포맷으로 변경된다") {
                user.passwordHash shouldHaveLength 60
            }
        }
    }
})
