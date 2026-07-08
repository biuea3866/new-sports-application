package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.dto.CreateLimitedDropCommand
import com.sportsapp.application.goods.dto.LimitedDropView
import com.sportsapp.domain.goods.service.LimitedDropDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 판매자 한정판 회차 개설 — 재고 검증·저장·Redis 시드는 [LimitedDropDomainService]가 소유하며,
 * 이 UseCase는 오케스트레이션만 담당한다.
 */
@Service
class CreateLimitedDropUseCase(
    private val limitedDropDomainService: LimitedDropDomainService,
) {
    @Transactional
    fun execute(command: CreateLimitedDropCommand): LimitedDropView {
        val (drop, price) = limitedDropDomainService.createDrop(
            productId = command.productId,
            openAt = command.openAt,
            closeAt = command.closeAt,
            limitedQuantity = command.limitedQuantity,
            perUserLimit = command.perUserLimit,
            ownerUserId = command.ownerUserId,
        )
        return LimitedDropView.of(drop, command.limitedQuantity, price)
    }
}
