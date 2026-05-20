package com.sportsapp.application.message

import com.sportsapp.domain.message.MessageDomainService
import com.sportsapp.domain.message.Room
import com.sportsapp.domain.message.RoomType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class CreateRoomUseCaseTest : BehaviorSpec({

    val messageDomainService = mockk<MessageDomainService>()
    val createRoomUseCase = CreateRoomUseCase(messageDomainService)

    Given("1:1 룸 생성 요청 — 기존 룸이 없는 경우") {
        val newRoom = Room(type = RoomType.DIRECT, name = null)
        every { messageDomainService.createOrFindOneToOne(1L, 2L) } returns newRoom

        When("participantIds=[1,2], name=null 로 execute 를 호출하면") {
            val command = CreateRoomCommand(participantIds = listOf(1L, 2L), name = null)
            val result = createRoomUseCase.execute(command)

            Then("[U-01] 새 룸이 반환된다") {
                result.type shouldBe RoomType.DIRECT
                verify { messageDomainService.createOrFindOneToOne(1L, 2L) }
            }
        }
    }

    Given("1:1 룸 생성 요청 — 기존 룸이 있는 경우") {
        val existingRoom = Room(type = RoomType.DIRECT, name = null)
        every { messageDomainService.createOrFindOneToOne(3L, 4L) } returns existingRoom

        When("동일 participantIds 로 두 번 호출하면") {
            val command = CreateRoomCommand(participantIds = listOf(3L, 4L), name = null)
            val firstResult = createRoomUseCase.execute(command)
            val secondResult = createRoomUseCase.execute(command)

            Then("[U-01] 동일 룸이 반환되고 createOrFindOneToOne 이 2회 호출된다") {
                firstResult.type shouldBe RoomType.DIRECT
                secondResult.type shouldBe RoomType.DIRECT
                verify(exactly = 2) { messageDomainService.createOrFindOneToOne(3L, 4L) }
            }
        }
    }

    Given("그룹 룸 생성 요청") {
        val groupRoom = Room(type = RoomType.GROUP, name = "축구 모임")
        every { messageDomainService.createGroupRoom("축구 모임") } returns groupRoom

        When("name='축구 모임' 으로 execute 를 호출하면") {
            val command = CreateRoomCommand(participantIds = listOf(1L, 2L, 3L), name = "축구 모임")
            val result = createRoomUseCase.execute(command)

            Then("그룹 룸이 반환된다") {
                result.type shouldBe RoomType.GROUP
                result.name shouldBe "축구 모임"
                verify { messageDomainService.createGroupRoom("축구 모임") }
            }
        }
    }
})
