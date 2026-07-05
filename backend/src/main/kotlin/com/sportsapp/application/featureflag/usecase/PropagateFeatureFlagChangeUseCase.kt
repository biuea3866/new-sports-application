package com.sportsapp.application.featureflag.usecase

import com.sportsapp.domain.featureflag.service.FeatureFlagDomainService
import org.springframework.stereotype.Service

/**
 * `FeatureFlagChangedEvent`(AFTER_COMMIT) 수신 시 전파(캐시 갱신 + broadcast)를 트리거한다 (BE-07).
 *
 * 읽기(getByKey) + 외부 발행(cacheStore.put/broadcast)만 수행하므로 `@Transactional`이 불필요하다.
 */
@Service
class PropagateFeatureFlagChangeUseCase(
    private val featureFlagDomainService: FeatureFlagDomainService,
) {
    fun execute(key: String) {
        featureFlagDomainService.propagate(key)
    }
}
