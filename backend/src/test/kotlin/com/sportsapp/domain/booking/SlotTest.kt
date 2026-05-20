package com.sportsapp.domain.booking

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime

class SlotTest : BehaviorSpec({

    Given("capacity=0 입력") {
        Then("[U-01] create 시 InvalidSlotException을 던진다") {
            shouldThrow<InvalidSlotException> {
                Slot.create(
                    facilityId = "FAC-01",
                    date = ZonedDateTime.now(),
                    timeRange = "09:00-10:00",
                    capacity = 0,
                    ownerId = 1L,
                )
            }
        }
    }

    Given("capacity=-1 입력") {
        Then("[U-01] create 시 InvalidSlotException을 던진다") {
            shouldThrow<InvalidSlotException> {
                Slot.create(
                    facilityId = "FAC-01",
                    date = ZonedDateTime.now(),
                    timeRange = "09:00-10:00",
                    capacity = -1,
                    ownerId = 1L,
                )
            }
        }
    }

    Given("올바른 파라미터로 Slot 생성") {
        val slot = Slot.create(
            facilityId = "FAC-01",
            date = ZonedDateTime.now(),
            timeRange = "09:00-10:00",
            capacity = 5,
            ownerId = 10L,
        )

        Then("[U-01] Happy path — Slot이 정상 생성된다") {
            slot.facilityId shouldBe "FAC-01"
            slot.timeRange shouldBe "09:00-10:00"
            slot.capacity shouldBe 5
            slot.ownerId shouldBe 10L
        }

        When("소유자(ownerId=10)가 requireOwnedBy를 호출하면") {
            Then("[U-02] 예외 없이 통과한다") {
                slot.requireOwnedBy(10L)
            }
        }

        When("타인(userId=99)이 requireOwnedBy를 호출하면") {
            Then("[U-02] UnauthorizedSlotAccessException을 던진다") {
                shouldThrow<UnauthorizedSlotAccessException> {
                    slot.requireOwnedBy(99L)
                }
            }
        }

        When("applyUpdate로 capacity=10으로 변경하면") {
            slot.applyUpdate(newTimeRange = null, newCapacity = 10)
            Then("[U-03] capacity가 10으로 갱신된다") {
                slot.capacity shouldBe 10
            }
        }

        When("applyUpdate에 잘못된 timeRange를 전달하면") {
            Then("[U-03] InvalidSlotException을 던진다") {
                shouldThrow<InvalidSlotException> {
                    slot.applyUpdate(newTimeRange = "9:00-10:00", newCapacity = null)
                }
            }
        }

        When("applyUpdate에 capacity=0을 전달하면") {
            Then("[U-03] InvalidSlotException을 던진다") {
                shouldThrow<InvalidSlotException> {
                    slot.applyUpdate(newTimeRange = null, newCapacity = 0)
                }
            }
        }
    }
})
