package com.sportsapp.presentation.message.worker

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.application.message.dto.SendMessageCommand
import com.sportsapp.application.message.usecase.SendMessageUseCase
import com.sportsapp.domain.message.gateway.BroadcastMessage
import com.sportsapp.domain.message.gateway.MessageBroadcastGateway
import com.sportsapp.domain.message.service.MessageDomainService
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.transaction.support.TransactionTemplate

@TestConfiguration
class MessageBroadcastEventWorkerTestConfig {
    /**
     * 실 SimpMessagingTemplate/브로커 없이 AFTER_COMMIT 브로드캐스트 트리거 여부만 검증하기 위해
     * MessageBroadcastGateway 를 mockk 로 대체한다 (allow-bean-definition-overriding=true).
     */
    @Bean
    @Primary
    fun messageBroadcastGateway(): MessageBroadcastGateway = mockk(relaxed = true)
}

/**
 * MessageSentEvent -> MessageBroadcastEventWorker(AFTER_COMMIT) 브로드캐스트 검증 (BE-04).
 */
@Import(MessageBroadcastEventWorkerTestConfig::class)
class MessageBroadcastEventWorkerTest(
    @Autowired private val messageDomainService: MessageDomainService,
    @Autowired private val sendMessageUseCase: SendMessageUseCase,
    @Autowired private val messageBroadcastGateway: MessageBroadcastGateway,
    @Autowired private val transactionTemplate: TransactionTemplate,
) : BaseJpaIntegrationTest() {

    init {
        afterEach {
            clearMocks(messageBroadcastGateway)
        }

        Given("메시지 전송 트랜잭션이 정상 커밋되면") {
            val room = messageDomainService.createGroupRoom("커밋 테스트 방", emptyList())
            messageDomainService.joinRoom(room.id, userId = 10L)

            When("sendMessage 를 커밋까지 완료하면") {
                sendMessageUseCase.execute(SendMessageCommand(roomId = room.id, senderId = 10L, content = "커밋 메시지"))

                Then("AFTER_COMMIT 이후 MessageBroadcastGateway.broadcast 가 1회 호출된다") {
                    val captured = slot<BroadcastMessage>()
                    verify(exactly = 1) { messageBroadcastGateway.broadcast(room.id, capture(captured)) }
                    captured.captured.content shouldBe "커밋 메시지"
                    captured.captured.userId shouldBe 10L
                }
            }
        }

        Given("메시지 전송 이후 트랜잭션이 롤백되면") {
            val room = messageDomainService.createGroupRoom("롤백 테스트 방", emptyList())
            messageDomainService.joinRoom(room.id, userId = 20L)

            When("동일 트랜잭션 내에서 sendMessage 호출 후 예외로 롤백되면") {
                runCatching {
                    transactionTemplate.execute<Unit> {
                        sendMessageUseCase.execute(
                            SendMessageCommand(roomId = room.id, senderId = 20L, content = "롤백 메시지"),
                        )
                        throw IllegalStateException("강제 롤백")
                    }
                }

                Then("브로드캐스트가 발생하지 않는다") {
                    verify(exactly = 0) { messageBroadcastGateway.broadcast(any(), any()) }
                }
            }
        }
    }
}
