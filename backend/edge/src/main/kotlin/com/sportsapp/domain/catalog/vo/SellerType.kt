package com.sportsapp.domain.catalog.vo

/**
 * catalog 파사드가 노출·필터링하는 판매자 유형 (S2-01).
 *
 * `commerce` 소유 `com.sportsapp.domain.goods.vo.SellerType`과 값(B2C/B2B)은 동일하지만 별개
 * 타입이다 — edge가 그 타입을 계약(쿼리 파라미터·[com.sportsapp.domain.catalog.dto.CatalogItem])에
 * 그대로 노출하면 edge가 commerce를 컴파일 의존하게 된다(§9 Branch By Abstraction). 변환은
 * 조립자(`bootstrap`)의 로컬 어댑터가 경계에서 수행한다 — enum 이름이 같으므로 쿼리 파라미터·
 * JSON 응답 문자열(B2C/B2B)은 이동 전과 동일하다(동작 변화 0).
 */
enum class SellerType {
    B2C,
    B2B,
}
