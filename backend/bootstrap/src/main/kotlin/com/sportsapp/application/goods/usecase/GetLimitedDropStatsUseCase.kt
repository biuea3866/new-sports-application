package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.dto.LimitedDropStatsResult
import com.sportsapp.domain.goods.service.LimitedDropDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 한정판 회차 집계 조회(API 계약 `GET /limited-drops/{dropId}/stats`, FR-9).
 */
@Service
class GetLimitedDropStatsUseCase(
    private val limitedDropDomainService: LimitedDropDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(dropId: Long): LimitedDropStatsResult {
        val stats = limitedDropDomainService.getStats(dropId)
        return LimitedDropStatsResult.of(stats)
    }
}
