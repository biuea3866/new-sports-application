package com.sportsapp.application.ticketing.usecase

import com.sportsapp.domain.ticketing.service.TicketingDomainService
import org.springframework.stereotype.Service

/**
 * ticketing.expiry.enabled 운영 킬 스위치 판정 — `TicketOrderExpiryScheduler`가 매 주기
 * 호출해 스위퍼 실행 여부를 결정한다.
 *
 * 부팅 시 고정되는 `@ConfigurationProperties`가 아니라 `TicketingDomainService.isExpiryEnabled`를
 * 거쳐 `FeatureFlagEvaluator`로 매 주기 런타임 조회한다(no-conditional-on-property) — 관리
 * 플래그를 OFF로 바꾸면 재기동 없이 다음 주기부터 즉시 반영된다.
 */
@Service
class IsTicketOrderExpiryEnabledUseCase(
    private val ticketingDomainService: TicketingDomainService,
) {
    fun execute(): Boolean = ticketingDomainService.isExpiryEnabled()
}
