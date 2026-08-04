package com.sportsapp.domain.recruitment.dto

import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * 사용자별 Application 조회 결과 — 표시명(모집 제목) 조인 프로젝션.
 * 참조 Recruitment가 없거나 삭제된 경우 [recruitmentTitle]은 빈 문자열로 방어된다.
 *
 * [paymentId]·[createdAt]은 order 통합조회(BE-08)의 결제 연계 노출·`createdAt desc` 병합에
 * 쓰인다 — 둘 다 Application 자기 컬럼이라 추가 조인 없이 노출한다.
 *
 * [feeAmount]는 참조 Recruitment(같은 recruitment 컨텍스트)의 참가비를 그대로 노출한다 —
 * title과 동일한 조인으로 얻는 자기 컨텍스트 데이터다(결제 도메인 역참조 없음). 참조
 * Recruitment가 없거나 삭제된 경우 금액을 확정할 수 없어 `null`로 방어한다(recruitmentTitle과
 * 동일 정책). `0`(무료 모집)은 금액이 확정된 정상값이므로 null과 구분한다 — 0으로 방어하면
 * "무료"와 "미확정"을 화면이 구분하지 못한다.
 */
data class ApplicationWithRecruitmentTitle(
    val applicationId: Long,
    val status: ApplicationStatus,
    val recruitmentTitle: String,
    val paymentId: Long?,
    val createdAt: ZonedDateTime,
    val feeAmount: BigDecimal? = null,
)
