package com.sportsapp.domain.message.entity

import com.sportsapp.domain.message.exception.NotRoomHostException
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

    Given("host_user_id 가 지정되지 않은 방 (Room.createGroup(name))") {
        val room = Room.createGroup("호스트 없는 모임")

        Then("currentHostUserId 는 null 이다") {
            room.currentHostUserId.shouldBeNull()
        }
        Then("isHostedBy 는 어떤 userId 에도 false 를 반환한다") {
            room.isHostedBy(1L) shouldBe false
        }
        Then("requireHostedBy 는 누가 호출해도 NotRoomHostException 을 던진다") {
            shouldThrow<NotRoomHostException> {
                room.requireHostedBy(1L)
            }
        }
    }

    Given("host_user_id 가 1L 로 지정된 방 (Room.createGroup(name, hostUserId = 1L))") {
        val room = Room.createGroup("호스트 있는 모임", hostUserId = 1L)

        Then("currentHostUserId 는 1L 이다") {
            room.currentHostUserId shouldBe 1L
        }
        Then("isHostedBy(1L) 은 true, isHostedBy(2L) 은 false 다") {
            room.isHostedBy(1L) shouldBe true
            room.isHostedBy(2L) shouldBe false
        }
        Then("requireHostedBy(1L) 은 예외 없이 통과한다") {
            room.requireHostedBy(1L)
        }
        Then("requireHostedBy(2L) 은 NotRoomHostException 을 던진다") {
            shouldThrow<NotRoomHostException> {
                room.requireHostedBy(2L)
            }
        }
    }

    Given("host 미지정 방에 assignHost(3L) 을 호출하면") {
        val room = Room.createGroup("호스트 지정 전")

        When("assignHost(3L) 을 호출하면") {
            room.assignHost(3L)

            Then("currentHostUserId 가 3L 로 갱신되고 requireHostedBy(3L) 이 통과한다") {
                room.currentHostUserId shouldBe 3L
                room.requireHostedBy(3L)
            }
        }
    }

    Given("Room.createForContext(..., hostUserId = 7L) 호출") {
        When("컨텍스트 방을 host 지정과 함께 생성하면") {
            val room = Room.createForContext(RoomType.GROUP, RoomContextType.COMMUNITY, 10L, "주말축구", hostUserId = 7L)

            Then("currentHostUserId 는 7L 이다") {
                room.currentHostUserId shouldBe 7L
            }
        }
    }
})
