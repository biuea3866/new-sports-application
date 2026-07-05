package com.sportsapp.domain.message.entity

import com.sportsapp.domain.message.vo.RoomContextType
import com.sportsapp.domain.message.vo.RoomType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime

class RoomTest : BehaviorSpec({

    Given("Room.createDirect() 호출") {
        When("DIRECT 타입 Room 을 생성하면") {
            val room = Room.createDirect()
            Then("[U-01] type 이 DIRECT 이고 name 이 null 이다") {
                room.type shouldBe RoomType.DIRECT
                room.name shouldBe null
            }
            Then("contextType/contextId 는 모두 null 이다 (기존 호환)") {
                room.contextType.shouldBeNull()
                room.contextId.shouldBeNull()
            }
            Then("belongsToContext() 는 false 다") {
                room.belongsToContext() shouldBe false
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
            Then("contextType/contextId 는 모두 null 이다 (기존 호환)") {
                room.contextType.shouldBeNull()
                room.contextId.shouldBeNull()
            }
        }
    }

    Given("Room.createForContext(GROUP, COMMUNITY, 10, \"주말축구\") 호출") {
        When("커뮤니티 컨텍스트로 방을 생성하면") {
            val room = Room.createForContext(RoomType.GROUP, RoomContextType.COMMUNITY, 10L, "주말축구")
            Then("type/name 이 설정된다") {
                room.type shouldBe RoomType.GROUP
                room.name shouldBe "주말축구"
            }
            Then("contextType/contextId 가 채워진 방이 만들어진다") {
                room.contextType shouldBe RoomContextType.COMMUNITY
                room.contextId shouldBe 10L
            }
            Then("belongsToContext() 는 true 다") {
                room.belongsToContext() shouldBe true
            }
        }
    }

    Given("활성 Room 에 메시지가 전송되면") {
        val room = Room.createDirect()
        val sentAt = ZonedDateTime.now()

        When("lastMessageBumpedTo 를 호출하면") {
            room.lastMessageBumpedTo(sentAt)
            Then("[U-02] lastMessageAt 이 sentAt 으로 갱신된다") {
                room.lastMessageAt shouldBe sentAt
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
