package com.sportsapp.scenario.message

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.message.dto.CreateRoomCommand
import com.sportsapp.application.message.dto.SendMessageCommand
import com.sportsapp.application.message.usecase.CreateRoomUseCase
import com.sportsapp.application.message.usecase.ListMessagesUseCase
import com.sportsapp.application.message.usecase.SendMessageUseCase
import com.sportsapp.domain.message.exception.NotRoomParticipantException
import com.sportsapp.presentation.message.dto.response.ListMessagesResponse
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired

class MessageApiScenarioTest(
    @Autowired private val createRoomUseCase: CreateRoomUseCase,
    @Autowired private val sendMessageUseCase: SendMessageUseCase,
    @Autowired private val listMessagesUseCase: ListMessagesUseCase,
) : BaseIntegrationTest() {

    init {
        Given("[S-01] 메시지 작성 후 첫 페이지 조회") {
            val room = createRoomUseCase.execute(
                CreateRoomCommand(requestUserId = 100L, participantIds = listOf(100L, 101L), name = null),
            )
            sendMessageUseCase.execute(SendMessageCommand(roomId = room.id, senderId = 100L, content = "첫 번째 메시지"))

            When("GET /rooms/{id}/messages 를 cursor 없이 호출하면") {
                val messages = listMessagesUseCase.execute(room.id, 100L, null)
                val response = ListMessagesResponse.of(messages, 30)

                Then("작성된 메시지가 첫 페이지에 즉시 노출된다") {
                    response.messages shouldHaveSize 1
                    response.messages.first().content shouldBe "첫 번째 메시지"
                    response.nextCursor.shouldBeNull()
                }
            }
        }

        Given("[S-01] 31건 메시지 작성 후 커서 페이지네이션") {
            val room = createRoomUseCase.execute(
                CreateRoomCommand(requestUserId = 200L, participantIds = listOf(200L, 201L), name = null),
            )
            repeat(31) { index ->
                sendMessageUseCase.execute(
                    SendMessageCommand(roomId = room.id, senderId = 200L, content = "메시지${index + 1}"),
                )
            }

            When("첫 페이지를 조회하면") {
                val firstMessages = listMessagesUseCase.execute(room.id, 200L, null)
                val firstPage = ListMessagesResponse.of(firstMessages, 30)

                Then("[S-01] 30건 + nextCursor 가 반환된다") {
                    firstPage.messages shouldHaveSize 30
                    firstPage.nextCursor.shouldNotBeNull()
                }

                And("nextCursor 로 두 번째 페이지를 조회하면") {
                    val secondMessages = listMessagesUseCase.execute(room.id, 200L, firstPage.nextCursor)
                    val secondPage = ListMessagesResponse.of(secondMessages, 30)

                    Then("나머지 1건이 반환되고 nextCursor 는 null 이다") {
                        secondPage.messages shouldHaveSize 1
                        secondPage.nextCursor.shouldBeNull()
                    }
                }
            }
        }

        Given("[S-03] 참여하지 않은 사용자가 메시지를 작성할 때") {
            val room = createRoomUseCase.execute(
                CreateRoomCommand(requestUserId = 300L, participantIds = listOf(300L, 301L), name = null),
            )

            When("비참여자(userId=999) 가 메시지 작성을 시도하면") {
                Then("NotRoomParticipantException 이 발생한다") {
                    shouldThrow<NotRoomParticipantException> {
                        sendMessageUseCase.execute(
                            SendMessageCommand(roomId = room.id, senderId = 999L, content = "침입"),
                        )
                    }
                }
            }
        }

        Given("[S-02] 메시지 전송 후 Room.lastMessageAt 갱신") {
            val room = createRoomUseCase.execute(
                CreateRoomCommand(requestUserId = 400L, participantIds = listOf(400L, 401L), name = null),
            )
            sendMessageUseCase.execute(
                SendMessageCommand(roomId = room.id, senderId = 400L, content = "테스트 메시지"),
            )

            When("메시지 목록을 조회하면") {
                val messages = listMessagesUseCase.execute(room.id, 400L, null)
                val response = ListMessagesResponse.of(messages, 30)

                Then("메시지가 1건 존재한다") {
                    response.messages shouldHaveSize 1
                }
            }
        }
    }
}
