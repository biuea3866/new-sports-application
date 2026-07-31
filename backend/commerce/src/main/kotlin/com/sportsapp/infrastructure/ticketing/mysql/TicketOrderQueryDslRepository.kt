package com.sportsapp.infrastructure.ticketing.mysql

import com.sportsapp.domain.ticketing.dto.TicketOrderExpiryCandidate
import java.time.ZonedDateTime

/**
 * ticketing 모듈 내 Event(EventQueryDslRepository/EventJpaRepositoryImpl)와 동일한 QueryDSL
 * 커스텀 프래그먼트 패턴 — W1-11b 만료 스위퍼가 소비하는 청크 조회·CAS 쓰기 3종을 담는다.
 */
interface TicketOrderQueryDslRepository {
    fun findPendingCreatedBefore(before: ZonedDateTime, afterId: Long, limit: Int): List<TicketOrderExpiryCandidate>
    fun tryExpire(orderId: Long): Boolean
    fun tryConfirm(orderId: Long, paymentId: Long): Boolean
}
