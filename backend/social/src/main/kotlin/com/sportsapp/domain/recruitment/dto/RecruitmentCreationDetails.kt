package com.sportsapp.domain.recruitment.dto

import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * 모집 생성에 필요한 필드를 묶은 값 객체 (RecruitmentDomainService.create LongParameterList
 * 정리, W1-DEBT-01). application 의 CreateRecruitmentCommand 를 그대로 재사용하지 않는 이유는
 * domain 이 application 을 참조할 수 없기 때문이다 — application 매퍼가 이 값 객체로 변환한다.
 */
data class RecruitmentCreationDetails(
    val title: String,
    val description: String?,
    val capacity: Int,
    val feeAmount: BigDecimal,
    val activityAt: ZonedDateTime,
    val applicationDeadline: ZonedDateTime,
    val communityId: Long?,
    val recruiterUserId: Long,
)
