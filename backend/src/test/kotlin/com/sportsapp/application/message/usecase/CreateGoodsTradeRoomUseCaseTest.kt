package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.CreateGoodsTradeRoomCommand
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.service.RoomContextDomainService
import com.sportsapp.domain.message.vo.RoomContextType
import com.sportsapp.domain.message.vo.RoomType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class CreateGoodsTradeRoomUseCaseTest : BehaviorSpec({

    val roomContextDomainService = mockk<RoomContextDomainService>()
    val useCase = CreateGoodsTradeRoomUseCase(roomContextDomainService)

    Given("구매자-상품 거래 채팅 생성 커맨드") {
        val room = Room.createForContext(RoomType.GROUP, RoomContextType.GOODS_PRODUCT, 7L, null)
        every { roomContextDomainService.createOrFindGoodsTradeRoom(7L, 1L) } returns room

        When("execute 를 호출하면") {
            val command = CreateGoodsTradeRoomCommand(productId = 7L, buyerId = 1L)
            val result = useCase.execute(command)

            Then("RoomContextDomainService.createOrFindGoodsTradeRoom 이 위임 호출되고 결과 방을 그대로 반환한다") {
                result shouldBe room
                verify(exactly = 1) { roomContextDomainService.createOrFindGoodsTradeRoom(7L, 1L) }
            }
        }
    }
})
