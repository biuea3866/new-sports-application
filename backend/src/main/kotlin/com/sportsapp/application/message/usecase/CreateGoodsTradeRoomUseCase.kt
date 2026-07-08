package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.CreateGoodsTradeRoomCommand
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.service.RoomContextDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateGoodsTradeRoomUseCase(
    private val roomContextDomainService: RoomContextDomainService,
) {
    @Transactional
    fun execute(command: CreateGoodsTradeRoomCommand): Room =
        roomContextDomainService.createOrFindGoodsTradeRoom(
            productId = command.productId,
            buyerId = command.buyerId,
        )
}
