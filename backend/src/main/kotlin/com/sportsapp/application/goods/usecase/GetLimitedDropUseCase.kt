package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.dto.LimitedDropView
import com.sportsapp.domain.goods.service.LimitedDropDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 한정판 회차 상세 조회(API 계약 `GET /limited-drops/{dropId}`).
 * Redis remaining이 시드되지 않았으면(null) 아직 소진분이 없는 것으로 간주해 limitedQuantity로 채운다.
 */
@Service
class GetLimitedDropUseCase(
    private val limitedDropDomainService: LimitedDropDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(dropId: Long): LimitedDropView {
        val (drop, remaining) = limitedDropDomainService.getView(dropId)
        return LimitedDropView.of(drop, remaining ?: drop.limitedQuantity)
    }
}
