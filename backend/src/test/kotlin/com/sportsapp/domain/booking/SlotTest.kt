package com.sportsapp.domain.booking

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.entity.SlotStatus
import com.sportsapp.domain.booking.exception.InvalidSlotException
import com.sportsapp.domain.booking.exception.InvalidSlotStatusException
import com.sportsapp.domain.booking.exception.SlotClosedException
import com.sportsapp.domain.booking.exception.UnauthorizedSlotAccessException

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

        When("applyUpdate로 timeRange를 변경하면") {
            slot.applyUpdate(newTimeRange = "11:00-12:00", newCapacity = null)
            Then("[U-04] timeRange가 실제로 변경된다") {
                slot.timeRange shouldBe "11:00-12:00"
            }
        }
    }

    Given("programId 없이 생성한 Slot") {
        val slot = Slot.create(
            facilityId = "FAC-STATUS-01",
            date = ZonedDateTime.now(),
            timeRange = "09:00-10:00",
            capacity = 5,
            ownerId = 1L,
        )

        Then("status는 OPEN이고 programId는 null이다") {
            slot.status shouldBe SlotStatus.OPEN
            slot.programId shouldBe null
        }
    }

    Given("programId를 가진 Slot 생성") {
        val slot = Slot.create(
            facilityId = "FAC-PROGRAM-01",
            date = ZonedDateTime.now(),
            timeRange = "09:00-10:00",
            capacity = 5,
            ownerId = 1L,
            programId = 77L,
        )

        Then("programId가 그대로 보관되고 status는 OPEN이다") {
            slot.programId shouldBe 77L
            slot.status shouldBe SlotStatus.OPEN
        }
    }

    Given("OPEN 상태에서 소유자가 close를 호출하는 경우") {
        val slot = Slot.create(
            facilityId = "FAC-CLOSE-01",
            date = ZonedDateTime.now(),
            timeRange = "09:00-10:00",
            capacity = 5,
            ownerId = 1L,
        )

        Then("status가 CLOSED로 전이한다") {
            slot.close(1L)
            slot.status shouldBe SlotStatus.CLOSED
        }
    }

    Given("OPEN 상태에서 소유자가 아닌 사용자가 close를 호출하는 경우") {
        val slot = Slot.create(
            facilityId = "FAC-CLOSE-02",
            date = ZonedDateTime.now(),
            timeRange = "09:00-10:00",
            capacity = 5,
            ownerId = 1L,
        )

        Then("UnauthorizedSlotAccessException을 던진다") {
            shouldThrow<UnauthorizedSlotAccessException> {
                slot.close(99L)
            }
        }
    }

    Given("OPEN 상태에서 requireBookable을 호출하는 경우") {
        val slot = Slot.create(
            facilityId = "FAC-CLOSE-03",
            date = ZonedDateTime.now(),
            timeRange = "09:00-10:00",
            capacity = 5,
            ownerId = 1L,
        )

        Then("예외 없이 통과한다") {
            slot.requireBookable()
        }
    }

    Given("CLOSED 상태에서 소유자가 open을 호출하는 경우") {
        val slot = Slot.create(
            facilityId = "FAC-OPEN-01",
            date = ZonedDateTime.now(),
            timeRange = "09:00-10:00",
            capacity = 5,
            ownerId = 1L,
        )
        slot.close(1L)

        Then("status가 OPEN으로 전이한다") {
            slot.open(1L)
            slot.status shouldBe SlotStatus.OPEN
        }
    }

    Given("CLOSED 상태에서 소유자가 아닌 사용자가 open을 호출하는 경우") {
        val slot = Slot.create(
            facilityId = "FAC-OPEN-02",
            date = ZonedDateTime.now(),
            timeRange = "09:00-10:00",
            capacity = 5,
            ownerId = 1L,
        )
        slot.close(1L)

        Then("UnauthorizedSlotAccessException을 던진다") {
            shouldThrow<UnauthorizedSlotAccessException> {
                slot.open(99L)
            }
        }
    }

    Given("CLOSED 상태에서 requireBookable을 호출하는 경우") {
        val slot = Slot.create(
            facilityId = "FAC-OPEN-03",
            date = ZonedDateTime.now(),
            timeRange = "09:00-10:00",
            capacity = 5,
            ownerId = 1L,
        )
        slot.close(1L)

        Then("SlotClosedException을 던진다") {
            shouldThrow<SlotClosedException> {
                slot.requireBookable()
            }
        }
    }

    Given("이미 CLOSED인 슬롯을 다시 close하는 경우") {
        val slot = Slot.create(
            facilityId = "FAC-OPEN-04",
            date = ZonedDateTime.now(),
            timeRange = "09:00-10:00",
            capacity = 5,
            ownerId = 1L,
        )
        slot.close(1L)

        Then("InvalidSlotStatusException을 던진다") {
            shouldThrow<InvalidSlotStatusException> {
                slot.close(1L)
            }
        }
    }
})
