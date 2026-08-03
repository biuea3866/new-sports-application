package com.sportsapp.application.message.dto

import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomInvitation
import com.sportsapp.domain.message.vo.InvitationStatus
import com.sportsapp.domain.message.vo.RoomType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

/**
 * 초대 수신함(S7)은 "어느 방에, 누구에게 초대받았는가"를 보여줘야 하는데, 응답에 방 이름과
 * 초대자 표시 이름이 없어 방 PK(`방 #53`)·사용자 PK(`초대자 #71`)를 그대로 노출하고 있었다.
 * 둘 다 응답에 실리는지 검증한다.
 */
class InvitationResponseTest : BehaviorSpec({

    fun invitationWith(roomName: String?): RoomInvitation {
        val room = mockk<Room>(relaxed = true)
        every { room.id } returns 53L
        every { room.name } returns roomName
        every { room.type } returns RoomType.GROUP

        val invitation = mockk<RoomInvitation>(relaxed = true)
        every { invitation.id } returns 7L
        every { invitation.room } returns room
        every { invitation.inviterUserId } returns 71L
        every { invitation.inviteeUserId } returns 68L
        every { invitation.currentStatus } returns InvitationStatus.PENDING
        every { invitation.canSpeak } returns true
        every { invitation.expiresAt } returns ZonedDateTime.now().plusDays(6)
        every { invitation.createdAt } returns ZonedDateTime.now()
        return invitation
    }

    Given("이름이 있는 방의 초대") {
        val invitation = invitationWith("강남 새벽 러닝크루")

        When("응답으로 변환하면") {
            val response = InvitationResponse.of(invitation, "김철수")

            Then("방 이름이 포함된다") {
                response.roomName shouldBe "강남 새벽 러닝크루"
            }

            Then("초대자 표시 이름이 포함된다") {
                response.inviterDisplayName shouldBe "김철수"
            }

            Then("기존 필드도 그대로 유지된다") {
                response.roomId shouldBe 53L
                response.inviterUserId shouldBe 71L
                response.canSpeak shouldBe true
            }
        }
    }

    Given("이름이 없는 방(1:1 등)의 초대") {
        val invitation = invitationWith(null)

        When("응답으로 변환하면") {
            val response = InvitationResponse.of(invitation, "김철수")

            Then("방 이름은 null로 내려가고 클라이언트가 기본 이름을 결정한다") {
                response.roomName shouldBe null
            }
        }
    }
})
