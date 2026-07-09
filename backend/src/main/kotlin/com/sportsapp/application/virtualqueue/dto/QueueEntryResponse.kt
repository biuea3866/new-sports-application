package com.sportsapp.application.virtualqueue.dto

import java.time.ZonedDateTime

/**
 * `POST /virtual-queues/{type}/{targetId}/entries` · `GET .../entries/me` 공용 응답 골격.
 *
 * 필드 의미(TDD "FE/외부 계약 — API 명세" §1/§2 SSOT):
 * - [status]: `WAITING` | `ADMITTED` | `DIRECT_ADMITTED`
 * - [position]: 순번(1-based, WAITING만 값 보유)
 * - [aheadCount]: 앞선 대기 인원(WAITING만, `QueuePosition.aheadCount`)
 * - [etaSeconds]: 예상 대기 초(WAITING만, `QueuePosition.etaSeconds`)
 * - [entryToken]/[tokenExpiresAt]: ADMITTED/DIRECT_ADMITTED만 값 보유
 *
 * 도메인(`QueueStatus`) → 응답 변환 로직은 이 티켓 범위가 아니다(후행 `GetQueueStatusUseCase`
 * 등에서 확정) — 여기서는 FE 계약과 1:1 대응하는 응답 형태(shape)만 고정한다.
 */
data class QueueEntryResponse(
    val status: String,
    val position: Long?,
    val aheadCount: Long?,
    val etaSeconds: Long?,
    val entryToken: String?,
    val tokenExpiresAt: ZonedDateTime?,
)
