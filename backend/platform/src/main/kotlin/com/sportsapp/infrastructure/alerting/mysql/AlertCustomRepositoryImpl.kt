package com.sportsapp.infrastructure.alerting.mysql

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.alerting.entity.QAlert.alert
import com.sportsapp.domain.alerting.repository.AlertCustomRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.time.ZonedDateTime
import org.springframework.stereotype.Repository

/**
 * [AlertCustomRepository] QueryDSL 구현체 — `StockCustomRepositoryImpl` 선례와 동일 패턴.
 * 대상 id를 QueryDSL로 조회한 뒤 실제 삭제는 [AlertJpaRepository.deleteAllByIdInBatch]
 * (Spring Data JPA가 자체 트랜잭션 경계를 갖는 표준 메서드)에 위임한다 — 트랜잭션 선언을 이 클래스에
 * 직접 두지 않으면서도(private-be-code-convention "UseCase에서만 선언"), 호출부가 별도 트랜잭션 없이
 * 이 메서드만 단독 호출해도 안전하게 삭제가 반영된다.
 */
@Repository
class AlertCustomRepositoryImpl(
    private val alertJpaRepository: AlertJpaRepository,
) : AlertCustomRepository {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private val queryFactory: JPAQueryFactory
        get() = JPAQueryFactory(entityManager)

    override fun deleteRaisedBefore(cutoff: ZonedDateTime): Long {
        val expiredIds = queryFactory.select(alert.id)
            .from(alert)
            .where(alert.raisedAt.lt(cutoff))
            .fetch()
        if (expiredIds.isEmpty()) return 0L

        alertJpaRepository.deleteAllByIdInBatch(expiredIds)
        return expiredIds.size.toLong()
    }
}
