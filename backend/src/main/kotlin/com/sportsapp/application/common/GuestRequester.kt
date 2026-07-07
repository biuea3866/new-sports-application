package com.sportsapp.application.common

/**
 * 비로그인(게스트) 요청자 sentinel — [com.sportsapp.domain.community.service.CommunityDomainService.getCommunity]로
 * 가시성을 재판정할 때 requesterId 가 없는 경우 이 값으로 조회해 PRIVATE 모임을 항상 비멤버로
 * 거부되게 한다. `CommunityDomainService` 시그니처는 무수정으로 유지하기 위한 application 레이어
 * 공통 상수다 (post/recruitment UseCase 공유, 리뷰 p3-a — 3중복 통합).
 */
object GuestRequester {
    const val ID = 0L
}
