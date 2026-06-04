package com.sportsapp.scenario.message

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.message.dto.CreateRoomCommand
import com.sportsapp.application.message.usecase.CreateRoomUseCase
import com.sportsapp.application.message.usecase.DeleteRoomUseCase
import com.sportsapp.application.message.usecase.GetRoomUseCase
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.message.exception.NotRoomParticipantException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired

class RoomApiScenarioTest(
    @Autowired private val createRoomUseCase: CreateRoomUseCase,
    @Autowired private val getRoomUseCase: GetRoomUseCase,
    @Autowired private val deleteRoomUseCase: DeleteRoomUseCase,
) : BaseIntegrationTest() {

    init {
        Given("1:1 룸 생성 요청") {
            When("[S-01] 동일 두 사용자가 1:1 룸을 두 번 생성 요청하면") {
                val commandA = CreateRoomCommand(requestUserId = 1L, participantIds = listOf(1L, 2L), name = null)
                val firstResponse = createRoomUseCase.execute(commandA)
                val secondResponse = createRoomUseCase.execute(commandA)

                Then("항상 같은 roomId 를 반환한다") {
                    firstResponse.id shouldBe secondResponse.id
                }
            }
        }

        Given("사용자 A, B 가 참여한 룸") {
            val commandAB = CreateRoomCommand(requestUserId = 10L, participantIds = listOf(10L, 11L), name = null)
            val room = createRoomUseCase.execute(commandAB)

            When("[S-02] 사용자 A(userId=10) 가 탈퇴하면") {
                deleteRoomUseCase.execute(roomId = room.id, userId = 10L)

                Then("사용자 B(userId=11) 에게는 룸이 여전히 보인다") {
                    val stillExists = getRoomUseCase.execute(room.id, 11L)
                    stillExists.id shouldBe room.id
                }
            }
        }

        Given("마지막 참가자가 한 명 남은 룸") {
            val commandSingle = CreateRoomCommand(requestUserId = 20L, participantIds = listOf(20L, 21L), name = null)
            val room = createRoomUseCase.execute(commandSingle)
            deleteRoomUseCase.execute(roomId = room.id, userId = 20L)

            When("[S-03] 마지막 참가자(userId=21) 도 탈퇴하면") {
                deleteRoomUseCase.execute(roomId = room.id, userId = 21L)

                Then("GET /rooms/{id} 는 404 를 던진다") {
                    shouldThrow<ResourceNotFoundException> {
                        getRoomUseCase.execute(room.id, 21L)
                    }
                }
            }
        }

        Given("1:1 룸에 참여하지 않은 사용자") {
            val commandCD = CreateRoomCommand(requestUserId = 30L, participantIds = listOf(30L, 31L), name = null)
            val room = createRoomUseCase.execute(commandCD)

            When("[S-04] 비참여자(userId=99) 가 GET /rooms/{id} 를 호출하면") {
                Then("NotRoomParticipantException 이 발생한다") {
                    shouldThrow<NotRoomParticipantException> {
                        getRoomUseCase.execute(room.id, 99L)
                    }
                }
            }
        }
    }
}
