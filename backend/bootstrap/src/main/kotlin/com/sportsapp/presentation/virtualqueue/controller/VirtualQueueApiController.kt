package com.sportsapp.presentation.virtualqueue.controller

import com.sportsapp.application.virtualqueue.dto.EnterQueueCommand
import com.sportsapp.application.virtualqueue.dto.GetQueueStatsCommand
import com.sportsapp.application.virtualqueue.dto.GetQueueStatusCommand
import com.sportsapp.application.virtualqueue.dto.LeaveQueueCommand
import com.sportsapp.application.virtualqueue.dto.QueueEntryResponse
import com.sportsapp.application.virtualqueue.dto.QueueStatsResponse
import com.sportsapp.application.virtualqueue.usecase.EnterQueueUseCase
import com.sportsapp.application.virtualqueue.usecase.GetQueueStatsUseCase
import com.sportsapp.application.virtualqueue.usecase.GetQueueStatusUseCase
import com.sportsapp.application.virtualqueue.usecase.LeaveQueueUseCase
import com.sportsapp.domain.common.security.UserPrincipal
import com.sportsapp.domain.virtualqueue.vo.QueueTargetType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 가상 대기열 REST 진입점 (BE-08, API 계약: TDD "FE/외부 계약 — API 명세" §1~4).
 *
 * `{type}` 경로 변수는 `QueueTargetType.slug`(`limited-drop` | `ticketing-event`)다. Spring의
 * enum 자동 바인딩(enum name 매칭)을 쓰지 않고 [QueueTargetType.fromSlug]로 직접 파싱한다 —
 * 실패 시 `IllegalArgumentException`이 `GlobalExceptionHandler.handleIllegalArgumentException`으로
 * 잡혀 400을 반환한다.
 *
 * UseCase(BE-06)만 호출한다 — Repository/Gateway/DomainService 직접 참조 없음, 비즈니스 로직 없음.
 */
@RestController
@RequestMapping("/virtual-queues/{type}/{targetId}")
class VirtualQueueApiController(
    private val enterQueueUseCase: EnterQueueUseCase,
    private val getQueueStatusUseCase: GetQueueStatusUseCase,
    private val leaveQueueUseCase: LeaveQueueUseCase,
    private val getQueueStatsUseCase: GetQueueStatsUseCase,
) {

    /** 대기열 진입(FR-2·FR-7). 200 + QueueEntryResponse, 포화 시 QueueFullException → 429. */
    @PostMapping("/entries")
    fun enter(
        @PathVariable type: String,
        @PathVariable targetId: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<QueueEntryResponse> {
        val command = EnterQueueCommand(type = QueueTargetType.fromSlug(type), targetId = targetId, userId = principal.id)
        return ResponseEntity.ok(enterQueueUseCase.execute(command))
    }

    /** 순번·상태 조회(폴링+heartbeat). 200, 큐 부재 시 QueueEntryNotFoundException → 404. */
    @GetMapping("/entries/me")
    fun getStatus(
        @PathVariable type: String,
        @PathVariable targetId: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<QueueEntryResponse> {
        val command = GetQueueStatusCommand(type = QueueTargetType.fromSlug(type), targetId = targetId, userId = principal.id)
        return ResponseEntity.ok(getQueueStatusUseCase.execute(command))
    }

    /** 명시적 이탈(FR-8). 204. */
    @DeleteMapping("/entries/me")
    fun leave(
        @PathVariable type: String,
        @PathVariable targetId: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<Void> {
        val command = LeaveQueueCommand(type = QueueTargetType.fromSlug(type), targetId = targetId, userId = principal.id)
        leaveQueueUseCase.execute(command)
        return ResponseEntity.noContent().build()
    }

    /** 운영자 통계(FR-11). 200 + QueueStatsResponse. */
    @GetMapping("/stats")
    fun getStats(
        @PathVariable type: String,
        @PathVariable targetId: Long,
    ): ResponseEntity<QueueStatsResponse> {
        val command = GetQueueStatsCommand(type = QueueTargetType.fromSlug(type), targetId = targetId)
        return ResponseEntity.ok(getQueueStatsUseCase.execute(command))
    }
}
