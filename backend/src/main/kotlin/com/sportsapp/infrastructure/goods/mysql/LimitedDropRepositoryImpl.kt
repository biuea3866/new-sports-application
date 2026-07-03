package com.sportsapp.infrastructure.goods.mysql

import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import com.sportsapp.domain.goods.repository.LimitedDropRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class LimitedDropRepositoryImpl(
    private val limitedDropJpaRepository: LimitedDropJpaRepository,
) : LimitedDropRepository {

    override fun save(limitedDrop: LimitedDrop): LimitedDrop =
        limitedDropJpaRepository.save(limitedDrop)

    override fun findById(id: Long): LimitedDrop? =
        limitedDropJpaRepository.findByIdOrNull(id)

    override fun findOpenByProductId(productId: Long): LimitedDrop? =
        limitedDropJpaRepository.findFirstByProductIdAndStatusInAndDeletedAtIsNullOrderByOpenAtDesc(
            productId = productId,
            statuses = ACTIVE_STATUSES,
        )

    override fun findAllActive(): List<LimitedDrop> =
        limitedDropJpaRepository.findAllByStatusInAndDeletedAtIsNull(RECONCILABLE_STATUSES)

    companion object {
        private val ACTIVE_STATUSES = listOf(
            LimitedDropStatus.SCHEDULED,
            LimitedDropStatus.OPEN,
            LimitedDropStatus.SOLD_OUT,
        )
        private val RECONCILABLE_STATUSES = listOf(
            LimitedDropStatus.SCHEDULED,
            LimitedDropStatus.OPEN,
        )
    }
}
