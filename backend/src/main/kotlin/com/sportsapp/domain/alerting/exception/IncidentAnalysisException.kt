package com.sportsapp.domain.alerting.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

/**
 * [IncidentAnalysisGateway][com.sportsapp.domain.alerting.gateway.IncidentAnalysisGateway]가
 * LLM 호출 타임아웃·오류 시 던진다. [AlertDomainService][com.sportsapp.domain.alerting.service
 * .AlertDomainService.process]가 이를 잡아 [com.sportsapp.domain.alerting.vo.IncidentAnalysis
 * .fallback]으로 대체한다(FR-8) — 외부로 전파되지 않는다.
 */
class IncidentAnalysisException(
    message: String,
    cause: Throwable? = null,
) : BusinessException(
    errorCode = "INCIDENT_ANALYSIS_FAILURE",
    message = message,
    cause = cause,
) {
    override val status: ErrorStatus = ErrorStatus.INTERNAL
}
