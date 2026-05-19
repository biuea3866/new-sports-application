package com.sportsapp.infrastructure.audit

import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.domain.AuditorAware
import org.springframework.stereotype.Component
import java.time.ZonedDateTime
import java.time.temporal.TemporalAccessor
import java.util.Optional

/**
 * BaseEntity / BaseDocument 의 `@CreatedBy` · `@LastModifiedBy` 가 호출하는 빈.
 *
 * 현재는 SecurityContext 통합 전 placeholder — 항상 Optional.empty() 반환 (createdBy=null).
 * AUTH-03 (JWT 인증) 완료 후 SecurityContextHolder.getContext().authentication.principal
 * 에서 user id 를 꺼내는 구현으로 교체한다.
 *
 * `AUTH-03` 까지는 모든 Entity 의 created_by / updated_by 가 NULL 로 저장된다 — 도메인 동작에 영향 없음.
 */
@Component
class SecurityAuditorAware : AuditorAware<Long> {

    override fun getCurrentAuditor(): Optional<Long> {
        // TODO(AUTH-03): SecurityContextHolder 에서 user id 추출
        return Optional.empty()
    }
}

/**
 * `@EnableJpaAuditing(dateTimeProviderRef = "zonedDateTimeProvider")` 에서 참조.
 * `@CreatedDate` / `@LastModifiedDate` 필드가 ZonedDateTime 일 때 Auditing 이
 * ZonedDateTime.now() 를 주입하도록 한다.
 */
@Component("zonedDateTimeProvider")
class ZonedDateTimeProvider : DateTimeProvider {
    override fun getNow(): Optional<TemporalAccessor> = Optional.of(ZonedDateTime.now())
}
