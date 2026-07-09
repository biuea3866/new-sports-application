package com.sportsapp.domain.virtualqueue.vo

/**
 * 가상 대기열이 걸리는 대상의 종류.
 *
 * [slug]는 `QueueTarget`이 Redis 키를 조립할 때 쓰는 소문자-케밥 표현이다 — 다른 도메인은
 * enum 이름이 아니라 이 슬러그를 통해서만 키 공간을 공유한다.
 */
enum class QueueTargetType(val slug: String) {
    LIMITED_DROP("limited-drop"),
    TICKETING_EVENT("ticketing-event"),
}
