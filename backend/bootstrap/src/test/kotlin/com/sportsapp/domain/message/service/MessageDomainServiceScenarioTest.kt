package com.sportsapp.domain.message.service

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.common.exceptions.BusinessRuleViolationException
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.message.repository.MessageRepository
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import com.sportsapp.domain.message.repository.RoomRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate

class MessageDomainServiceScenarioTest(
    @Autowired private val messageDomainService: MessageDomainService,
    @Autowired private val roomRepository: RoomRepository,
    @Autowired private val roomParticipantRepository: RoomParticipantRepository,
    @Autowired private val messageRepository: MessageRepository,
    @Autowired private val transactionTemplate: TransactionTemplate,
) : BaseIntegrationTest() {

    init {
        Given("createRoom + joinRoom + sendMessage 전체 플로우") {
            When("그룹 룸 생성 → 2명 참여 → 메시지 전송") {
                val room = messageDomainService.createGroupRoom("축구 모임", emptyList())
                messageDomainService.joinRoom(room.id, userId = 10L)
                messageDomainService.joinRoom(room.id, userId = 20L)
                val message = messageDomainService.sendMessage(
                    roomId = room.id,
                    userId = 10L,
                    content = "안녕하세요!",
                )

                Then("room, participant, message 가 모두 저장된다") {
                    roomRepository.findById(room.id).shouldNotBeNull()
                    roomParticipantRepository.findActiveByRoomId(room.id) shouldHaveSize 2
                    message.content shouldBe "안녕하세요!"
                    message.room.id shouldBe room.id
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
                Then("ResourceNotFoundException 을 던진다") {
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
                Then("BusinessRuleViolationException 을 던진다") {
                    shouldThrow<BusinessRuleViolationException> {
                        messageDomainService.joinRoom(room.id, userId = 5L)
                    }
                }
            }
        }

        Given("존재하지 않는 Room 에 joinRoom 요청") {
            When("존재하지 않는 roomId 로 joinRoom 을 호출하면") {
                Then("ResourceNotFoundException 을 던진다") {
                    shouldThrow<ResourceNotFoundException> {
                        messageDomainService.joinRoom(roomId = 999999L, userId = 1L)
                    }
                }
            }
        }

        Given("마지막 참가자가 탈퇴하면 Room soft-delete 시 Message 도 soft-delete 된다") {
            val room = messageDomainService.createDirectRoom()
            messageDomainService.joinRoom(room.id, userId = 100L)
            messageDomainService.sendMessage(roomId = room.id, userId = 100L, content = "안녕")
            messageDomainService.sendMessage(roomId = room.id, userId = 100L, content = "잘가")

            When("마지막 참가자가 leaveRoom 을 호출하면") {
                transactionTemplate.execute {
                    messageDomainService.leaveRoom(roomId = room.id, userId = 100L)
                }

                Then("Room 이 soft-delete 되고 해당 Room 의 Message 조회가 0건이다") {
                    roomRepository.findById(room.id) shouldBe null
                    messageRepository.findByRoomId(room.id) shouldHaveSize 0
                }
            }
        }

        Given("sendMessage 호출 후 createdAt 초기화 및 room.lastMessageAt 원자적 갱신 검증") {
            val room = messageDomainService.createDirectRoom()
            messageDomainService.joinRoom(room.id, userId = 200L)

            When("sendMessage 를 호출하면") {
                val message = messageDomainService.sendMessage(
                    roomId = room.id,
                    userId = 200L,
                    content = "원자성 확인",
                )

                Then("message.createdAt 이 초기화되어 있고 room.lastMessageAt 도 함께 갱신된다") {
                    message.createdAt.shouldNotBeNull()
                    val updatedRoom = roomRepository.findById(room.id)
                    updatedRoom.shouldNotBeNull()
                    updatedRoom.lastMessageAt.shouldNotBeNull()
                    updatedRoom.lastMessageAt shouldBe message.createdAt
                }
            }
        }
    }
}
