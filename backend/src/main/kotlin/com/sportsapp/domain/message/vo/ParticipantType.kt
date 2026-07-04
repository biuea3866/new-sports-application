package com.sportsapp.domain.message.vo

/**
 * 방 참여자의 참여 스코프 속성 (TDD "Guest").
 * MEMBER는 정상 참여자, GUEST는 한시적으로 참여하는 참여자다.
 */
enum class ParticipantType {
    MEMBER,
    GUEST,
}
