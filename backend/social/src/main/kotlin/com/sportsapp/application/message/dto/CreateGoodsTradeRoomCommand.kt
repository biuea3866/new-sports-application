package com.sportsapp.application.message.dto

/**
 * goods 거래 채팅 생성 커맨드 (BE-11, TDD FR-18).
 */
data class CreateGoodsTradeRoomCommand(
    val productId: Long,
    val buyerId: Long,
)
