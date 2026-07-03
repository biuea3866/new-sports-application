package com.sportsapp.infrastructure.partner

import com.sportsapp.domain.partner.audit.PartnerAuditLogCustomRepository
import com.sportsapp.domain.partner.audit.PartnerAuditLogRepository
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/**
 * partner_audit_log 영속화(BE-03 범위 밖)가 아직 infrastructure 구현되지 않아
 * PartnerAuditLogDomainService(컴포넌트 스캔 대상) 빈 생성이 실패한다.
 * 이 티켓(Partner/PartnerApiKey persistence)과 무관하므로 컨텍스트 부팅용 mock만 제공한다.
 */
@TestConfiguration
class PartnerAuditStubConfig {

    @Bean
    fun partnerAuditLogRepository(): PartnerAuditLogRepository = mockk(relaxed = true)

    @Bean
    fun partnerAuditLogCustomRepository(): PartnerAuditLogCustomRepository = mockk(relaxed = true)
}
