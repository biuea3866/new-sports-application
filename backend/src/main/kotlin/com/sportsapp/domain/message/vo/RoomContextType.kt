package com.sportsapp.domain.message.vo

/**
 * 방이 외부 도메인 엔티티에 연결될 때의 컨텍스트 종류 (TDD "Context Room").
 * null이면 기존 DIRECT/GROUP처럼 컨텍스트 없는 일반 방을 의미한다.
 */
enum class RoomContextType {
    COMMUNITY,
    GOODS_PRODUCT,
}
