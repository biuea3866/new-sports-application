package com.sportsapp.domain.message.service

import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.repository.RoomRepository
import com.sportsapp.domain.message.vo.RoomContextType
import com.sportsapp.domain.message.vo.RoomType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.every
import io.mockk.mockk

/**
 * BE-08 커뮤니티 응답(`CommunityResponse.roomId`)이 컨텍스트 방 존재 여부를 조회하기 위해
 * 필요한 읽기 전용 조회. [MessageDomainService]에 추가하는 대신 별도 클래스로 분리해
 * TooManyFunctions 임계값(11)을 넘기지 않는다. [RoomRepository.findByContext](BE-03a)를 노출한다.
 */
class RoomContextQueryServiceTest : BehaviorSpec({

    val roomRepository = mockk<RoomRepository>()
    val service = RoomContextQueryService(roomRepository)

    Given("컨텍스트에 연결된 방이 존재하는 경우") {
        val room = Room.createForContext(
            type = RoomType.GROUP,
            contextType = RoomContextType.COMMUNITY,
            contextId = 10L,
            name = "커뮤니티 방",
        )
        every { roomRepository.findByContext(RoomContextType.COMMUNITY, 10L) } returns room

        When("findRoomByContext 를 호출하면") {
            val found = service.findRoomByContext(RoomContextType.COMMUNITY, 10L)

            Then("연결된 방이 반환된다") {
                found.shouldNotBeNull()
            }
        }
    }

    Given("컨텍스트에 연결된 방이 없는 경우") {
        every { roomRepository.findByContext(RoomContextType.COMMUNITY, 20L) } returns null

        When("findRoomByContext 를 호출하면") {
            val found = service.findRoomByContext(RoomContextType.COMMUNITY, 20L)

            Then("null 이 반환된다") {
                found.shouldBeNull()
            }
        }
    }
})
