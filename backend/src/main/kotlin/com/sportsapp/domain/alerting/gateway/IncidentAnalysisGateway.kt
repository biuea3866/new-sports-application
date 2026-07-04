package com.sportsapp.domain.alerting.gateway

import com.sportsapp.domain.alerting.vo.IncidentAnalysis
import com.sportsapp.domain.alerting.vo.IncidentContext

/**
 * LLM(Claude API)에게 원인분석을 요청하는 domain gateway.
 * 구현체는 infrastructure의 `IncidentAnalysisGatewayImpl`이 담당하며, HTTP client는 그곳에만 DI한다.
 */
interface IncidentAnalysisGateway {

    /**
     * [context]를 바탕으로 원인분석을 요청한다.
     * 타임아웃·오류 시 [com.sportsapp.domain.alerting.exception.IncidentAnalysisException]을 던진다 —
     * 폴백 여부는 이 gateway가 아니라 [com.sportsapp.domain.alerting.service.AlertDomainService]가 결정한다.
     */
    fun analyze(context: IncidentContext): IncidentAnalysis
}
