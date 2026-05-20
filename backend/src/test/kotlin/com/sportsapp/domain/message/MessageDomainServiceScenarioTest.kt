package com.sportsapp.domain.message

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.common.exceptions.BusinessRuleViolationException
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired

class MessageDomainServiceScenarioTest(
    @Autowired private val messageDomainService: MessageDomainService,
    @Autowired private val roomRepository: RoomRepository,
    @Autowired private val roomParticipantRepository: RoomParticipantRepository,
) : BaseIntegrationTest() {

    init {
        Given("createRoom + joinRoom + sendMessage 전체 플로우") {
            When("그룹 룸 생성 → 2명 참여 → 메시지 전송") {
                val room = messageDomainService.createGroupRoom("축구 모임")
                messageDomainService.joinRoom(room.id, userId = 10L)
                messageDomainService.joinRoom(room.id, userId = 20L)
                val message = messageDomainService.sendMessage(
                    roomId = room.id,
                    userId = 10L,
                    content = "안녕하세요!",
                )

                Then("[S-01] room, participant, message 가 모두 저장된다") {
                    roomRepository.findById(room.id).shouldNotBeNull()
                    roomParticipantRepository.findActiveByRoomId(room.id) shouldHaveSize 2
                    message.content shouldBe "안녕하세요!"
                    message.roomId shouldBe room.id
                }
            }
        }

        Given("삭제된 Room 에 sendMessage 요청") {
            val room = messageDomainService.createDirectRoom()
            val savedRoom = roomRepository.findById(room.id)
                ?: error("Room not found: ${room.id}")
            savedRoom.softDelete(null)
            roomRepository.save(savedRoom)

            When("삭제된 Room 에 메시지를 보내면") {
                Then("[S-02] ResourceNotFoundException 을 던진다") {
                    shouldThrow<ResourceNotFoundException> {
                        messageDomainService.sendMessage(
                            roomId = room.id,
                            userId = 1L,
                            content = "메시지",
                        )
                    }
                }
            }
        }

        Given("이미 참여한 사용자가 다시 joinRoom 요청") {
            val room = messageDomainService.createDirectRoom()
            messageDomainService.joinRoom(room.id, userId = 5L)

            When("동일 userId 로 다시 joinRoom 을 호출하면") {
                Then("[S-03] BusinessRuleViolationException 을 던진다") {
                    shouldThrow<BusinessRuleViolationException> {
                        messageDomainService.joinRoom(room.id, userId = 5L)
                    }
                }
            }
        }

        Given("존재하지 않는 Room 에 joinRoom 요청") {
            When("존재하지 않는 roomId 로 joinRoom 을 호출하면") {
                Then("[S-02] ResourceNotFoundException 을 던진다") {
                    shouldThrow<ResourceNotFoundException> {
                        messageDomainService.joinRoom(roomId = 999999L, userId = 1L)
                    }
                }
            }
        }
    }
}
