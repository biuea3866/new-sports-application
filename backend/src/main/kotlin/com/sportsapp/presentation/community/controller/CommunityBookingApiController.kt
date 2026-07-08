package com.sportsapp.presentation.community.controller

import com.sportsapp.application.community.dto.CommunityBookingListItemResponse
import com.sportsapp.application.community.dto.CommunityBookingResponse
import com.sportsapp.application.community.usecase.LinkCommunityBookingUseCase
import com.sportsapp.application.community.usecase.ListCommunityBookingsUseCase
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.community.dto.request.LinkCommunityBookingRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private const val COMMUNITY_BOOKING_ENABLED_PROPERTY = "community.booking.enabled"

/**
 * 소모임 예약 연동 REST 계약 (TDD B3, FR-13~15). `community.booking.enabled=false`면
 * 빈 자체가 등록되지 않아 `/communities/{id}/bookings` 전 경로가 404(즉시 롤백 지점, Release Scenario).
 */
@RestController
@RequestMapping("/communities/{communityId}/bookings")
@ConditionalOnProperty(name = [COMMUNITY_BOOKING_ENABLED_PROPERTY], havingValue = "true", matchIfMissing = false)
class CommunityBookingApiController(
    private val linkCommunityBookingUseCase: LinkCommunityBookingUseCase,
    private val listCommunityBookingsUseCase: ListCommunityBookingsUseCase,
) {

    @PostMapping
    fun link(
        @PathVariable communityId: Long,
        @RequestBody request: LinkCommunityBookingRequest,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<CommunityBookingResponse> {
        val response = linkCommunityBookingUseCase.execute(request.toCommand(communityId, principal.id))
        return ResponseEntity.ok(response)
    }

    @GetMapping
    fun list(
        @PathVariable communityId: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<List<CommunityBookingListItemResponse>> =
        ResponseEntity.ok(listCommunityBookingsUseCase.execute(communityId, principal.id))
}
