package com.sportsapp.presentation.alerting.controller

import com.sportsapp.application.alerting.usecase.ReceiveGrafanaAlertUseCase
import com.sportsapp.application.alerting.usecase.RaiseAlertUseCase
import com.sportsapp.domain.common.exceptions.UnauthorizedException
import com.sportsapp.presentation.alerting.dto.request.GrafanaWebhookRequest
import com.sportsapp.presentation.alerting.dto.request.RaiseAlertRequest
import com.sportsapp.presentation.alerting.dto.response.AlertWebhookAckResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 규칙 엔진(Grafana Alerting) webhook·내부 raise(③오버셀·⑧배포실패) 수신 진입점
 * (TDD.md §Detail Design 시스템 역할 경계, BE-08). 시크릿 검증·Request→Command 변환·UseCase 위임만 하고
 * 비즈니스 로직은 갖지 않는다.
 *
 * grafana 경로는 `Authorization: Bearer <secret>`(Grafana는 커스텀 헤더를 발신할 수 없음, INFRA-02 실측),
 * 내부 raise 경로는 `X-Alert-Token: <secret>`으로 공유 시크릿을 검증한다. 불일치 시 401.
 *
 * `alerting.enabled=false`(기본값)면 202만 반환하고 UseCase를 호출하지 않는다
 * (Release Scenario 피처 플래그 게이팅).
 */
@RestController
@RequestMapping("/internal/alerts")
class AlertWebhookApiController(
    private val receiveGrafanaAlertUseCase: ReceiveGrafanaAlertUseCase,
    private val raiseAlertUseCase: RaiseAlertUseCase,
    @Value("\${alerting.webhook-token:}") private val webhookToken: String,
    @Value("\${alerting.enabled:false}") private val alertingEnabled: Boolean,
) {

    @PostMapping("/grafana")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun receiveGrafanaWebhook(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: GrafanaWebhookRequest,
    ): AlertWebhookAckResponse {
        validateBearerToken(authorization)
        if (alertingEnabled) request.toCommands().forEach { receiveGrafanaAlertUseCase.execute(it) }
        return AlertWebhookAckResponse.accepted()
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun receiveInternalAlert(
        @RequestHeader("X-Alert-Token", required = false) alertToken: String?,
        @RequestBody request: RaiseAlertRequest,
    ): AlertWebhookAckResponse {
        validateAlertToken(alertToken)
        if (alertingEnabled) raiseAlertUseCase.execute(request.toCommand())
        return AlertWebhookAckResponse.accepted()
    }

    private fun validateBearerToken(authorization: String?) {
        if (webhookToken.isBlank() || authorization != "Bearer $webhookToken") {
            throw UnauthorizedException("Invalid or missing webhook bearer token")
        }
    }

    private fun validateAlertToken(alertToken: String?) {
        if (webhookToken.isBlank() || alertToken != webhookToken) {
            throw UnauthorizedException("Invalid or missing alert token")
        }
    }
}
