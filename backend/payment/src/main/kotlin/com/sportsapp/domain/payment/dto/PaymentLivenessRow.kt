package com.sportsapp.domain.payment.dto

import com.sportsapp.domain.payment.entity.PaymentStatus
import java.time.ZonedDateTime

/**
 * [com.sportsapp.domain.payment.service.PaymentLivenessClassifier.classify]의 입력 —
 * payment 조회(QueryDSL Projections)가 `Payment` 엔티티 전체 대신 만료 스위퍼 판정에
 * 필요한 최소 사실(orderId·status·createdAt)만 값 객체로 뽑아 전달한다. 순수 함수인
 * classify를 테스트할 때 JPA auditing이 채우는 lateinit 필드(Payment.createdAt)를
 * 우회할 필요 없이 직접 생성할 수 있게 하는 목적도 겸한다.
 */
data class PaymentLivenessRow(
    val orderId: Long,
    val status: PaymentStatus,
    val createdAt: ZonedDateTime,
)
