package com.sportsapp.application.virtualqueue.dto

import com.sportsapp.domain.virtualqueue.vo.QueueStatus
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
 */
data class QueueEntryResponse(
    val status: String,
    val position: Long?,
    val aheadCount: Long?,
    val etaSeconds: Long?,
    val entryToken: String?,
    val tokenExpiresAt: ZonedDateTime?,
) {

    companion object {
        /**
         * `QueueStatus` → `QueueEntryResponse` 변환 (BE-06, EnterQueueUseCase/GetQueueStatusUseCase 공용).
         *
         * [position](1-based 순번)은 도메인에 별도로 보관되지 않는다 — `QueuePosition.aheadCount`
         * (ZRANK, 표시용 동적 순위)에 +1한 값으로 계산한다. admission 판정에 쓰이는 고정 시퀀스
         * (`seq`)는 내부 판정 전용이라 API로 노출하지 않는다(§0-1 — seq는 admitted 판정에만 관여).
         */
        fun of(status: QueueStatus): QueueEntryResponse {
            val position = status.position
            val entryToken = status.entryToken
            return QueueEntryResponse(
                status = status.state.name,
                position = position?.let { it.aheadCount + 1 },
                aheadCount = position?.aheadCount,
                etaSeconds = position?.etaSeconds,
                entryToken = entryToken?.raw,
                tokenExpiresAt = entryToken?.expiresAt,
            )
        }
    }
}
