package com.sportsapp.presentation.message.worker

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.application.message.usecase.MarkReadUseCase
import com.sportsapp.domain.message.gateway.MessageBroadcastGateway
import com.sportsapp.domain.message.gateway.ReadEvent
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
class RoomReadEventWorkerTestConfig {
    /**
     * 실 SimpMessagingTemplate/브로커 없이 AFTER_COMMIT 브로드캐스트 트리거 여부만 검증하기 위해
     * MessageBroadcastGateway 를 mockk 로 대체한다 (allow-bean-definition-overriding=true).
     */
    @Bean
    @Primary
    fun messageBroadcastGateway(): MessageBroadcastGateway = mockk(relaxed = true)
}

/**
 * RoomReadEvent -> RoomReadEventWorker(AFTER_COMMIT) 브로드캐스트 검증 (BE-05, 코드 리뷰 정정).
 * markRead 트랜잭션이 롤백되면 커밋 전 발행된 이벤트 자체가 리스너에 도달하지 않아
 * "유령 브로드캐스트"가 발생하지 않아야 한다.
 */
@Import(RoomReadEventWorkerTestConfig::class)
class RoomReadEventWorkerTest(
    @Autowired private val messageDomainService: MessageDomainService,
    @Autowired private val markReadUseCase: MarkReadUseCase,
    @Autowired private val messageBroadcastGateway: MessageBroadcastGateway,
    @Autowired private val transactionTemplate: TransactionTemplate,
) : BaseJpaIntegrationTest() {

    init {
        afterEach {
            clearMocks(messageBroadcastGateway)
        }

        Given("읽음 처리 트랜잭션이 정상 커밋되면") {
            val room = messageDomainService.createGroupRoom("읽음 커밋 테스트 방", emptyList())
            messageDomainService.joinRoom(room.id, userId = 30L)
            messageDomainService.joinRoom(room.id, userId = 31L)
            val message = messageDomainService.sendMessage(room.id, 31L, "상대 메시지")

            When("markRead 를 커밋까지 완료하면") {
                markReadUseCase.execute(roomId = room.id, userId = 30L, lastReadMessageId = message.id)

                Then("AFTER_COMMIT 이후 MessageBroadcastGateway.broadcastRead 가 1회 호출된다") {
                    val captured = slot<ReadEvent>()
                    verify(exactly = 1) { messageBroadcastGateway.broadcastRead(room.id, capture(captured)) }
                    captured.captured.userId shouldBe 30L
                    captured.captured.lastReadMessageId shouldBe message.id
                }
            }
        }

        Given("읽음 처리 이후 트랜잭션이 롤백되면") {
            val room = messageDomainService.createGroupRoom("읽음 롤백 테스트 방", emptyList())
            messageDomainService.joinRoom(room.id, userId = 40L)
            messageDomainService.joinRoom(room.id, userId = 41L)
            val message = messageDomainService.sendMessage(room.id, 41L, "상대 메시지")

            When("동일 트랜잭션 내에서 markRead 호출 후 예외로 롤백되면") {
                runCatching {
                    transactionTemplate.execute<Unit> {
                        markReadUseCase.execute(roomId = room.id, userId = 40L, lastReadMessageId = message.id)
                        throw IllegalStateException("강제 롤백")
                    }
                }

                Then("브로드캐스트가 발생하지 않는다 (유령 브로드캐스트 방지)") {
                    verify(exactly = 0) { messageBroadcastGateway.broadcastRead(any(), any()) }
                }
            }
        }
    }
}
