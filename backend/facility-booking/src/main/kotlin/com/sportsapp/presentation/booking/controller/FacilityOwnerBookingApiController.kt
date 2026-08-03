package com.sportsapp.presentation.booking.controller

import com.sportsapp.application.booking.dto.ListFacilityOwnerBookingsCommand
import com.sportsapp.application.booking.usecase.ListFacilityOwnerBookingsUseCase
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.common.security.UserPrincipal
import com.sportsapp.presentation.booking.dto.response.ListBookingsResponse
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 파트너(시설 소유자) 예약 관리 API.
 *
 * 예약자용 `/bookings/me`와 스코프가 정반대다 — 이쪽은 **내 시설에 들어온 남의 예약**을
 * 반환한다. 조회 범위는 인증 주체(`principal.id`)를 시설 소유자로 삼아 서버가 결정하며,
 * 클라이언트가 ownerUserId를 넘길 수 없다(넘길 수 있으면 남의 예약을 조회할 수 있다).
 */
@RestController
@RequestMapping("/api/facility-owner/bookings")
@PreAuthorize("hasRole('FACILITY_OWNER')")
class FacilityOwnerBookingApiController(
    private val listFacilityOwnerBookingsUseCase: ListFacilityOwnerBookingsUseCase,
) {
    @GetMapping
    fun listFacilityOwnerBookings(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) status: BookingStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ListBookingsResponse> {
        val command = ListFacilityOwnerBookingsCommand(
            ownerUserId = principal.id,
            status = status,
            pageable = PageRequest.of(page, size),
        )
        return ResponseEntity.ok(ListBookingsResponse.of(listFacilityOwnerBookingsUseCase.execute(command)))
    }
}
