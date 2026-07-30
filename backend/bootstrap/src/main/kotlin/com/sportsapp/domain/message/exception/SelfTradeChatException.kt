package com.sportsapp.domain.message.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

/**
 * 구매자가 상품 소유자(판매자) 본인일 때 거래 채팅 생성을 거부한다 (BE-11, TDD FR-18).
 */
class SelfTradeChatException(
    productId: Long,
    userId: Long,
) : BusinessException(
    errorCode = "SELF_TRADE_CHAT_NOT_ALLOWED",
    message = "User $userId cannot start a trade chat with themselves for product $productId",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
