package com.sportsapp.domain.virtualqueue.vo

/** 대기열 진입자의 상태. */
enum class QueueEntryState {
    /** 대기 중 — 아직 admission되지 않았다. */
    WAITING,

    /** admission되어 대기열을 정상 경유해 입장 토큰을 발급받았다. */
    ADMITTED,

    /** 피처 플래그 OFF — 대기 없이 즉시 통과해 토큰을 발급받았다. */
    DIRECT_ADMITTED,
}
