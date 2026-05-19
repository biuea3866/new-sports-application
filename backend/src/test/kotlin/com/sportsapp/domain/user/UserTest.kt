package com.sportsapp.domain.user

import com.sportsapp.domain.user.exceptions.DuplicateRoleException
import com.sportsapp.domain.user.exceptions.InvalidEmailException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

class UserTest : BehaviorSpec({

    val fixedTime = ZonedDateTime.parse("2026-01-01T00:00:00Z")

    Given("잘못된 이메일 형식") {
        When("User.create 를 호출하면") {
            Then("[U-01] InvalidEmailException 을 던진다") {
                shouldThrow<InvalidEmailException> {
                    User.create("not-an-email", "hash", fixedTime)
                }
                shouldThrow<InvalidEmailException> {
                    User.create("missing-at.com", "hash", fixedTime)
                }
                shouldThrow<InvalidEmailException> {
                    User.create("", "hash", fixedTime)
                }
            }
        }
    }

    Given("올바른 이메일을 가진 User") {
        val user = User.create("user@example.com", "hash", fixedTime)
        val role = Role(id = 1L, name = "USER", permissions = emptyList())

        When("동일한 Role 을 두 번 부여하면") {
            user.assignRole(role)
            Then("[U-02] DuplicateRoleException 을 던진다") {
                shouldThrow<DuplicateRoleException> {
                    user.assignRole(role)
                }
            }
        }
    }

    Given("특정 id 를 가진 User") {
        val user = User(
            id = 42L,
            email = "owner@example.com",
            passwordHash = "hash",
            status = UserStatus.ACTIVE,
            createdAt = fixedTime,
        )

        When("canAccess 를 본인 id 로 호출하면") {
            Then("[U-03] true 를 반환한다") {
                user.canAccess(42L) shouldBe true
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

        val user = User.create("pw@example.com", "oldHash", fixedTime)

        When("changePassword 를 호출하면") {
            val newHash = encoder("newPassword")
            user.changePassword(newHash)
            Then("[U-04] passwordHash 가 60자 BCrypt 포맷으로 변경된다") {
                user.passwordHash shouldHaveLength 60
            }
        }
    }
})
