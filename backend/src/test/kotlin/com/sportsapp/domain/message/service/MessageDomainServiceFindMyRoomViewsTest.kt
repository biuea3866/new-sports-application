package com.sportsapp.domain.message.service

import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.message.gateway.MessageBroadcastGateway
import com.sportsapp.domain.message.repository.MessageRepository
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import com.sportsapp.domain.message.repository.RoomRepository
import com.sportsapp.domain.message.vo.RoomContextType
import com.sportsapp.domain.message.vo.RoomListView
import com.sportsapp.domain.message.vo.RoomType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

/**
 * MessageDomainService 가 방목록 미리보기 조회를 [RoomRepository.findMyRoomViews] 로
 * 위임하는지 검증한다 (BE-12, N+1 회피 projection 전환).
 */
class MessageDomainServiceFindMyRoomViewsTest : BehaviorSpec({

    val roomRepository = mockk<RoomRepository>()
    val messageRepository = mockk<MessageRepository>()
    val roomParticipantRepository = mockk<RoomParticipantRepository>()
    val domainEventPublisher = mockk<DomainEventPublisher>()
    val messageBroadcastGateway = mockk<MessageBroadcastGateway>()
    val messageDomainService = MessageDomainService(
        roomRepository = roomRepository,
        messageRepository = messageRepository,
        roomParticipantRepository = roomParticipantRepository,
        domainEventPublisher = domainEventPublisher,
        messageBroadcastGateway = messageBroadcastGateway,
    )

    Given("userId=1 이 참여한 방 목록 projection 이 존재") {
        val views = listOf(
            RoomListView(
                roomId = 10L,
                type = RoomType.GROUP,
                name = "축구 모임",
                contextType = RoomContextType.COMMUNITY,
                lastMessageContent = "안녕하세요",
                lastMessageAt = ZonedDateTime.now(),
            ),
        )
        every { roomRepository.findMyRoomViews(1L, null) } returns views

        When("findMyRoomViews 를 호출하면") {
            val result = messageDomainService.findMyRoomViews(1L, null)

            Then("RoomRepository.findMyRoomViews 위임 결과가 그대로 반환된다") {
                result shouldHaveSize 1
                result[0].roomId shouldBe 10L
                verify(exactly = 1) { roomRepository.findMyRoomViews(1L, null) }
            }
        }
    }
})
