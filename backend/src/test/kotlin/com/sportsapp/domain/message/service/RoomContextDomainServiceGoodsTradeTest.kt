package com.sportsapp.domain.message.service

import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.exception.SelfTradeChatException
import com.sportsapp.domain.message.gateway.GoodsProductGateway
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import com.sportsapp.domain.message.repository.RoomRepository
import com.sportsapp.domain.message.vo.RoomContextType
import com.sportsapp.domain.message.vo.RoomType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

/**
 * goods 거래 채팅 연동 — `RoomContextDomainService.createOrFindGoodsTradeRoom` (BE-11, TDD FR-18).
 *
 * `GoodsProductGateway`로 상품 소유자(판매자) id를 조회해 GOODS_PRODUCT 컨텍스트 방을
 * buyer-seller 조합 단위로 provision·재사용한다. Kotest `BehaviorSpec`은 트리 전체를 단일 패스로
 * 순차 실행하므로(BE-09 RoomContextDomainServiceTest와 동일 이유) `Given` 블록마다 mock을 새로 만든다.
 */
class RoomContextDomainServiceGoodsTradeTest : BehaviorSpec({

    Given("구매자와 상품 소유자(판매자)가 다르고 아직 거래 방이 없는 경우") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val goodsProductGateway = mockk<GoodsProductGateway>()
        val service = RoomContextDomainService(roomRepository, roomParticipantRepository, goodsProductGateway)
        every { goodsProductGateway.findOwnerId(7L) } returns 100L
        every { roomRepository.findByContextAndParticipant(RoomContextType.GOODS_PRODUCT, 7L, 1L) } returns null
        val roomSlot = slot<Room>()
        every { roomRepository.save(capture(roomSlot)) } answers { roomSlot.captured }
        every { roomParticipantRepository.save(any()) } answers { firstArg() }

        When("구매자(1)가 상품(7)에 대한 거래 채팅을 요청하면") {
            val result = service.createOrFindGoodsTradeRoom(productId = 7L, buyerId = 1L)

            Then("판매자(100)와의 GOODS_PRODUCT 컨텍스트 방이 새로 생성된다") {
                result.type shouldBe RoomType.GROUP
                result.contextType shouldBe RoomContextType.GOODS_PRODUCT
                result.contextId shouldBe 7L
                verify(exactly = 1) { roomRepository.save(any()) }
                verify(exactly = 2) { roomParticipantRepository.save(any()) }
            }

            Then("방장(host_user_id, BE-13)은 판매자(100)로 지정된다") {
                result.currentHostUserId shouldBe 100L
            }
        }
    }

    Given("동일 구매자-상품 조합의 거래 방이 이미 있는 경우") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val goodsProductGateway = mockk<GoodsProductGateway>()
        val service = RoomContextDomainService(roomRepository, roomParticipantRepository, goodsProductGateway)
        val existingRoom = Room.createForContext(RoomType.GROUP, RoomContextType.GOODS_PRODUCT, 8L, null)
        every { goodsProductGateway.findOwnerId(8L) } returns 200L
        every { roomRepository.findByContextAndParticipant(RoomContextType.GOODS_PRODUCT, 8L, 2L) } returns existingRoom

        When("동일 구매자(2)가 상품(8)에 대한 거래 채팅을 재요청하면") {
            val result = service.createOrFindGoodsTradeRoom(productId = 8L, buyerId = 2L)

            Then("새 방을 만들지 않고 기존 방으로 이동한다 (멱등)") {
                result shouldBe existingRoom
                verify(exactly = 0) { roomRepository.save(any()) }
                verify(exactly = 0) { roomParticipantRepository.save(any()) }
            }
        }
    }

    Given("구매자가 상품 소유자 본인인 경우") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val goodsProductGateway = mockk<GoodsProductGateway>()
        val service = RoomContextDomainService(roomRepository, roomParticipantRepository, goodsProductGateway)
        every { goodsProductGateway.findOwnerId(9L) } returns 3L

        When("본인(3)이 자신의 상품(9)에 대한 거래 채팅을 요청하면") {
            Then("SelfTradeChatException 이 발생하고 방이 생성되지 않는다") {
                shouldThrow<SelfTradeChatException> {
                    service.createOrFindGoodsTradeRoom(productId = 9L, buyerId = 3L)
                }
                verify(exactly = 0) { roomRepository.findByContextAndParticipant(any(), any(), any()) }
                verify(exactly = 0) { roomRepository.save(any()) }
                verify(exactly = 0) { roomParticipantRepository.save(any()) }
            }
        }
    }

    Given("존재하지 않는 productId 로 거래 채팅을 요청하는 경우") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val goodsProductGateway = mockk<GoodsProductGateway>()
        val service = RoomContextDomainService(roomRepository, roomParticipantRepository, goodsProductGateway)
        every { goodsProductGateway.findOwnerId(9999L) } throws
            com.sportsapp.domain.common.exceptions.ResourceNotFoundException("Product", 9999L)

        When("구매자(1)가 존재하지 않는 상품(9999)에 대한 거래 채팅을 요청하면") {
            Then("ResourceNotFoundException 이 전파되고 방이 생성되지 않는다") {
                shouldThrow<com.sportsapp.domain.common.exceptions.ResourceNotFoundException> {
                    service.createOrFindGoodsTradeRoom(productId = 9999L, buyerId = 1L)
                }
                verify(exactly = 0) { roomRepository.save(any()) }
                verify(exactly = 0) { roomParticipantRepository.save(any()) }
            }
        }
    }
})
