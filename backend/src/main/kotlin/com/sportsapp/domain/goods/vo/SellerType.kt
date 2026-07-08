package com.sportsapp.domain.goods.vo

/**
 * 상품 판매자 유형. B2C(개인/중고) vs B2B(파트너/브랜드).
 *
 * 등록 요청이 [com.sportsapp.domain.common.security.AuthChannelResolver.isPartnerAuthenticated]로
 * 판별한 인증 채널(파트너 API Key 경유 여부)에 따라 등록 시점에 1회 자동 결정되고, 이후 불변이다
 * (근거 TDD "방안 3 — sellerType 판별 신호", 상태 전이표).
 */
enum class SellerType {
    B2C,
    B2B,
    ;

    companion object {
        fun fromPartnerAuthenticated(partnerAuthenticated: Boolean): SellerType =
            if (partnerAuthenticated) B2B else B2C
    }
}
