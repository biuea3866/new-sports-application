package com.sportsapp.presentation.booking.controller

import com.sportsapp.application.booking.dto.InternalBookingOrderHistoryItemResponse
import com.sportsapp.application.booking.usecase.FindBookingOrderHistoryUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private const val INTERNAL_AUTH_SUBJECT_HEADER = "X-Internal-Auth-Subject"

/**
 * edge 통합 주문내역(BE-08)의 `OrderHistoryGateway.findBookingOrders` 원격 구현(2단계)이 호출할
 * 공급자 엔드포인트 (S2-04). 사용자 식별은 W1-06b 내부 신원 전파 계약의 [INTERNAL_AUTH_SUBJECT_HEADER]
 * 헤더로 받는다 — 개인 데이터라 헤더가 없으면 400으로 거부한다. internal 경로 전체(`/internal/` 이하)
 * 호출자 인가·인증은 S2-07(wave 2)이 일괄 처리하므로 이 컨트롤러는 조회 로직만 다룬다.
 */
@RestController
@RequestMapping("/internal/order-history/bookings")
class InternalBookingOrderApiController(
    private val findBookingOrderHistoryUseCase: FindBookingOrderHistoryUseCase,
) {

    @GetMapping
    fun findBookingOrders(
        @RequestHeader(INTERNAL_AUTH_SUBJECT_HEADER) subject: String,
    ): ResponseEntity<List<InternalBookingOrderHistoryItemResponse>> {
        val userId = subject.toLongOrNull()
            // GlobalExceptionHandler 가 message 를 응답 detail 로 그대로 내보내므로, 입력값·헤더 이름을
            // 담지 않는다 (내부 전용 경로라도 받은 값을 되돌려줄 이유가 없다).
            ?: throw IllegalArgumentException("Invalid internal identity header")
        return ResponseEntity.ok(findBookingOrderHistoryUseCase.execute(userId))
    }
}
