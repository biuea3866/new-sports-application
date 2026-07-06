package com.sportsapp.domain.message.vo

import com.sportsapp.domain.message.entity.RoomInvitation

/**
 * [GuestInvitationDomainService.invite][com.sportsapp.domain.message.service.GuestInvitationDomainService.invite]의
 * 신규/재사용 멱등 구분 결과 (BE-14).
 *
 * 기존에는 `RoomInvitation` 단일 타입만 반환해 호출자가 "새로 만든 초대인지, 기존 PENDING을
 * 재사용한 것인지" 구분할 수 없었다 — FE가 `createdAt` 3초 휴리스틱으로 추정해야 했다.
 * [reused]가 그 신호를 명시적으로 전달한다.
 */
data class InvitationResult(
    val invitation: RoomInvitation,
    val reused: Boolean,
)
