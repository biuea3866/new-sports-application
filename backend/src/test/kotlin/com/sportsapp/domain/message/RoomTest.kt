package com.sportsapp.domain.message

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class RoomTest : BehaviorSpec({

    Given("Room.createDirect() 호출") {
        When("DIRECT 타입 Room 을 생성하면") {
            val room = Room.createDirect()
            Then("[U-01] type 이 DIRECT 이고 name 이 null 이다") {
                room.type shouldBe RoomType.DIRECT
                room.name shouldBe null
            }
        }
    }

    Given("Room.createGroup() 호출") {
        When("빈 문자열 name 으로 생성하면") {
            Then("[U-02] IllegalArgumentException 을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    Room.createGroup("   ")
                }
            }
        }

        When("정상적인 name 으로 생성하면") {
            val room = Room.createGroup("스포츠 모임")
            Then("[U-01] type 이 GROUP 이고 name 이 설정된다") {
                room.type shouldBe RoomType.GROUP
                room.name shouldBe "스포츠 모임"
            }
        }
    }

    Given("소프트 삭제된 Room") {
        val room = Room.createDirect()
        room.softDelete(null)

        When("validateNotDeleted 를 호출하면") {
            Then("[U-03] IllegalStateException 을 던진다") {
                shouldThrow<IllegalStateException> {
                    room.validateNotDeleted()
                }
            }
        }
    }

    Given("활성 Room") {
        val room = Room.createDirect()

        When("softDelete 를 두 번 호출하면") {
            room.softDelete(1L)
            Then("[U-03] 두 번째 호출에서 IllegalStateException 을 던진다") {
                shouldThrow<IllegalStateException> {
                    room.softDelete(1L)
                }
            }
        }
    }
})
