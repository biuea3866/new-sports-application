package com.sportsapp.domain.alerting.repository

import java.time.ZonedDateTime

/**
 * `@Query` 없이 복잡 쿼리를 다루는 QueryDSL 커스텀 계약(private-be-code-convention "QueryDSL") —
 * 구현체는 infrastructure의 `AlertCustomRepositoryImpl`이 담당한다. [StockCustomRepository][com.sportsapp.domain.goods.repository.StockCustomRepository] 선례와 동일 패턴.
 */
interface AlertCustomRepository {

    /** [cutoff] 이전 raised_at 행을 벌크 삭제하고 삭제된 행 수를 반환한다. */
    fun deleteRaisedBefore(cutoff: ZonedDateTime): Long
}
