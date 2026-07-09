package com.sportsapp.domain.virtualqueue.vo

/**
 * 대기열 상태 응답의 도메인 표현 — WAITING/ADMITTED/DIRECT_ADMITTED 상태와 그에 따른
 * 위치·토큰 조합을 캡슐화한다. 호출부는 `state`를 직접 비교하지 않고 팩토리로 생성된
 * 조합(`position`/`entryToken` 존재 여부)만 사용한다.
 */
data class QueueStatus private constructor(
    val state: QueueEntryState,
    val position: QueuePosition?,
    val entryToken: EntryToken?,
) {

    companion object {

        /** 대기 중 — 아직 admission되지 않았다. */
        fun waiting(position: QueuePosition): QueueStatus =
            QueueStatus(state = QueueEntryState.WAITING, position = position, entryToken = null)

        /** admission되어 입장 토큰을 발급받았다 — 정상 대기열 경유 경로. */
        fun admitted(entryToken: EntryToken): QueueStatus =
            QueueStatus(state = QueueEntryState.ADMITTED, position = null, entryToken = entryToken)

        /** 피처 플래그 OFF — 대기 없이 즉시 통과, 토큰만 발급한다. */
        fun directEntry(entryToken: EntryToken): QueueStatus =
            QueueStatus(state = QueueEntryState.DIRECT_ADMITTED, position = null, entryToken = entryToken)
    }
}
