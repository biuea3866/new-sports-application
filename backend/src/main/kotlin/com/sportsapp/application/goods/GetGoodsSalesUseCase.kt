package com.sportsapp.application.goods

import com.sportsapp.domain.goods.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetGoodsSalesUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: GetGoodsSalesCommand): GoodsSalesResponse {
        requirePeriodNotInFuture(command)
        val summaries = goodsDomainService.aggregateSales(
            ownerUserId = command.operatorUserId,
            productId = command.productId,
            from = command.from,
            to = command.to,
        )
        return GoodsSalesResponse.of(summaries)
    }

    private fun requirePeriodNotInFuture(command: GetGoodsSalesCommand) {
        val now = java.time.ZonedDateTime.now()
        require(command.from.isBefore(now)) { "from must be in the past" }
    }
}
