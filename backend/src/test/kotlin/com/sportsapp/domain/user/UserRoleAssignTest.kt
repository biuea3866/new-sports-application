package com.sportsapp.domain.user

import com.sportsapp.domain.user.exceptions.DuplicateRoleException
import com.sportsapp.domain.user.exceptions.SelfRevocationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class UserRoleAssignTest : BehaviorSpec({

    Given("이미 FACILITY_OWNER Role(id=3)이 부여된 User") {
        val user = User(
            email = "user@example.com",
            passwordHash = "hash",
            status = UserStatus.ACTIVE,
        )
        val existingRoleIds = setOf(1L, 3L)

        When("동일한 FACILITY_OWNER(id=3)을 또 부여하려 하면") {
            Then("[U-01] DuplicateRoleException을 던진다") {
                shouldThrow<DuplicateRoleException> {
                    user.validateNoDuplicateRole(roleId = 3L, existingRoleIds = existingRoleIds)
                }
            }
        }

        When("아직 없는 ADMIN(id=2)을 부여하려 하면") {
            Then("[U-01-happy] 예외 없이 통과한다") {
                user.validateNoDuplicateRole(roleId = 2L, existingRoleIds = existingRoleIds)
                // 예외 미발생 == 성공
            }
        }
    }

    Given("id=7인 ADMIN User가 자기 자신(id=7)에게 ADMIN 회수를 시도하면") {
        val adminUser = User(
            email = "admin@example.com",
            passwordHash = "hash",
            status = UserStatus.ACTIVE,
        )

        When("validateCanRevokeAdminRole 호출 시") {
            Then("[U-02] SelfRevocationException을 던진다") {
                shouldThrow<SelfRevocationException> {
                    adminUser.validateCanRevokeAdminRole(
                        adminRoleName = "ADMIN",
                        targetRoleName = "ADMIN",
                        requesterId = 0L, // User.id 기본값 0 = 자기 자신
                    )
                }
            }
        }
    }

    Given("타인(id=99)이 User(id=0)의 ADMIN을 회수하려 하면") {
        val user = User(
            email = "target@example.com",
            passwordHash = "hash",
            status = UserStatus.ACTIVE,
        )

        When("validateCanRevokeAdminRole 호출 시") {
            Then("[U-02-happy] 예외 없이 통과한다") {
                user.validateCanRevokeAdminRole(
                    adminRoleName = "ADMIN",
                    targetRoleName = "ADMIN",
                    requesterId = 99L,
                )
            }
        }
    }

    Given("User가 Role 1개만 보유한 상태에서 회수를 시도하면") {
        val user = User(
            email = "single@example.com",
            passwordHash = "hash",
            status = UserStatus.ACTIVE,
        )

        When("validateHasMinimumOneRole(1) 호출 시") {
            Then("[U-03] IllegalArgumentException을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    user.validateHasMinimumOneRole(1)
                }
            }
        }

        When("validateHasMinimumOneRole(2) 호출 시 — Role 2개면") {
            Then("[U-03-happy] 예외 없이 통과한다") {
                user.validateHasMinimumOneRole(2)
                // 예외 미발생 == 성공
            }
        }
    }

    Given("회수 후 Role이 0개가 되는 경우") {
        val user = User(
            email = "last@example.com",
            passwordHash = "hash",
            status = UserStatus.ACTIVE,
        )

        When("validateHasMinimumOneRole(1) 호출 시 — Role 1개만 남은 상태") {
            Then("[U-03] 예외가 발생해 USER Role이 최소 1개 유지됨을 보장한다") {
                val exception = shouldThrow<IllegalArgumentException> {
                    user.validateHasMinimumOneRole(1)
                }
                exception.message shouldBe "User must retain at least one role"
            }
        }
    }
})
