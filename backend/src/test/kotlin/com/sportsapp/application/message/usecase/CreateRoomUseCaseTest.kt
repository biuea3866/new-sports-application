package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.CreateRoomCommand
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.service.MessageDomainService
import com.sportsapp.domain.message.vo.RoomType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class CreateRoomUseCaseTest : BehaviorSpec({

    val messageDomainService = mockk<MessageDomainService>()
    val createRoomUseCase = CreateRoomUseCase(messageDomainService)

    Given("1:1 룸 생성 요청 — 기존 룸이 없는 경우") {
        val newRoom = Room.createDirect()
        every { messageDomainService.createOrFindOneToOne(1L, 2L) } returns newRoom

        When("participantIds=[1,2], name=null 로 execute 를 호출하면") {
            val command = CreateRoomCommand(requestUserId = 1L, participantIds = listOf(1L, 2L), name = null)
            val result = createRoomUseCase.execute(command)

            Then("[U-01] 새 룸이 반환된다") {
                result.type shouldBe RoomType.DIRECT
                verify { messageDomainService.createOrFindOneToOne(1L, 2L) }
            }
        }
    }

    Given("1:1 룸 생성 요청 — 기존 룸이 있는 경우") {
        val existingRoom = Room.createDirect()
        every { messageDomainService.createOrFindOneToOne(3L, 4L) } returns existingRoom

        When("동일 participantIds 로 두 번 호출하면") {
            val command = CreateRoomCommand(requestUserId = 3L, participantIds = listOf(3L, 4L), name = null)
            val firstResult = createRoomUseCase.execute(command)
            val secondResult = createRoomUseCase.execute(command)

            Then("[U-01] 동일 룸이 반환되고 createOrFindOneToOne 이 2회 호출된다") {
                firstResult.type shouldBe RoomType.DIRECT
                secondResult.type shouldBe RoomType.DIRECT
                verify(exactly = 2) { messageDomainService.createOrFindOneToOne(3L, 4L) }
            }
        }
    }

    Given("그룹 룸 생성 요청 — 호출자가 participantIds에 포함된 경우") {
        val groupRoom = Room.createGroup("축구 모임", hostUserId = 1L)
        every {
            messageDomainService.createGroupRoom("축구 모임", listOf(1L, 2L, 3L), hostUserId = 1L)
        } returns groupRoom

        When("requestUserId=1, participantIds=[1,2,3], name='축구 모임' 으로 execute 를 호출하면") {
            val command = CreateRoomCommand(requestUserId = 1L, participantIds = listOf(1L, 2L, 3L), name = "축구 모임")
            val result = createRoomUseCase.execute(command)

            Then("[U-02] 그룹 룸이 반환되고 참여자 3명이 등록된다") {
                result.type shouldBe RoomType.GROUP
                result.name shouldBe "축구 모임"
                verify { messageDomainService.createGroupRoom("축구 모임", listOf(1L, 2L, 3L), hostUserId = 1L) }
            }

            Then("요청자(1L)가 방장(hostUserId, BE-13)으로 지정된다") {
                result.currentHostUserId shouldBe 1L
            }
        }
    }

    Given("그룹 룸 생성 요청 — 호출자가 participantIds에 미포함된 경우") {
        val groupRoom = Room.createGroup("농구 모임", hostUserId = 1L)
        every {
            messageDomainService.createGroupRoom("농구 모임", listOf(1L, 2L, 3L), hostUserId = 1L)
        } returns groupRoom

        When("requestUserId=1, participantIds=[2,3], name='농구 모임' 으로 execute 를 호출하면") {
            val command = CreateRoomCommand(requestUserId = 1L, participantIds = listOf(2L, 3L), name = "농구 모임")
            val result = createRoomUseCase.execute(command)

            Then("[U-03] 호출자가 자동 추가되어 참여자 3명으로 그룹 룸이 생성된다") {
                result.type shouldBe RoomType.GROUP
                verify { messageDomainService.createGroupRoom("농구 모임", listOf(1L, 2L, 3L), hostUserId = 1L) }
            }
        }
    }

    Given("1:1 룸 생성 요청 — 본인이 참여자에 미포함된 경우") {
        When("requestUserId=99, participantIds=[1,2], name=null 로 execute 를 호출하면") {
            val command = CreateRoomCommand(requestUserId = 99L, participantIds = listOf(1L, 2L), name = null)

            Then("[U-04] 본인이 참여자에 포함되지 않으면 IllegalArgumentException 이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    createRoomUseCase.execute(command)
                }
            }
        }
    }
})
