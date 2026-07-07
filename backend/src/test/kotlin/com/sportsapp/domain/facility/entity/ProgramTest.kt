package com.sportsapp.domain.facility.entity

import com.sportsapp.domain.facility.exception.InvalidProgramException
import com.sportsapp.domain.facility.exception.UnauthorizedProgramAccessException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class ProgramTest : BehaviorSpec({

    Given("소유자가 PT 상품(정원1·60분)을 등록하는 경우") {
        When("Program.create 를 호출하면") {
            val program = Program.create(
                facilityId = "FAC-01",
                ownerUserId = 1L,
                name = "1:1 PT",
                description = "개인 트레이닝",
                price = BigDecimal("50000"),
                capacity = 1,
                durationMinutes = 60,
            )

            Then("필드가 채워진다") {
                program.facilityId shouldBe "FAC-01"
                program.ownerUserId shouldBe 1L
                program.name shouldBe "1:1 PT"
                program.description shouldBe "개인 트레이닝"
                program.price shouldBe BigDecimal("50000")
                program.capacity shouldBe 1
                program.durationMinutes shouldBe 60
            }
        }
    }

    Given("price 가 음수인 경우") {
        When("Program.create 를 호출하면") {
            Then("InvalidProgramException 이 발생한다") {
                shouldThrow<InvalidProgramException> {
                    Program.create(
                        facilityId = "FAC-01",
                        ownerUserId = 1L,
                        name = "1:1 PT",
                        description = null,
                        price = BigDecimal("-1"),
                        capacity = 1,
                        durationMinutes = 60,
                    )
                }
            }
        }
    }

    Given("capacity 가 0인 경우") {
        When("Program.create 를 호출하면") {
            Then("InvalidProgramException 이 발생한다") {
                shouldThrow<InvalidProgramException> {
                    Program.create(
                        facilityId = "FAC-01",
                        ownerUserId = 1L,
                        name = "1:1 PT",
                        description = null,
                        price = BigDecimal.ZERO,
                        capacity = 0,
                        durationMinutes = 60,
                    )
                }
            }
        }
    }

    Given("durationMinutes 가 0인 경우") {
        When("Program.create 를 호출하면") {
            Then("InvalidProgramException 이 발생한다") {
                shouldThrow<InvalidProgramException> {
                    Program.create(
                        facilityId = "FAC-01",
                        ownerUserId = 1L,
                        name = "1:1 PT",
                        description = null,
                        price = BigDecimal.ZERO,
                        capacity = 1,
                        durationMinutes = 0,
                    )
                }
            }
        }
    }

    Given("name 이 공백인 경우") {
        When("Program.create 를 호출하면") {
            Then("InvalidProgramException 이 발생한다") {
                shouldThrow<InvalidProgramException> {
                    Program.create(
                        facilityId = "FAC-01",
                        ownerUserId = 1L,
                        name = "   ",
                        description = null,
                        price = BigDecimal.ZERO,
                        capacity = 1,
                        durationMinutes = 60,
                    )
                }
            }
        }
    }

    Given("소유자가 requireOwnedBy 를 호출하는 경우") {
        val program = Program.create(
            facilityId = "FAC-01",
            ownerUserId = 1L,
            name = "1:1 PT",
            description = null,
            price = BigDecimal.ZERO,
            capacity = 1,
            durationMinutes = 60,
        )

        When("본인 userId 로 호출하면") {
            Then("예외 없이 통과한다") {
                program.requireOwnedBy(1L)
            }
        }

        When("타인 userId 로 호출하면") {
            Then("UnauthorizedProgramAccessException 이 발생한다") {
                shouldThrow<UnauthorizedProgramAccessException> {
                    program.requireOwnedBy(99L)
                }
            }
        }
    }
})
