package com.sportsapp.application.recruitment.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * W1-11d recruitment PENDING 신청 만료 스위퍼 튜닝 값.
 *
 * `application.yml`에 키를 추가하지 않는다 — 아래 기본값이 코드 상 SSOT이며,
 * env(`RECRUITMENT_EXPIRY_*`, relaxed binding)로만 재정의한다.
 *
 * on/off 킬 스위치는 이 properties가 아니라 `RecruitmentFeatureFlagKeys.EXPIRY_ENABLED`
 * (`FeatureFlagEvaluator` 런타임 조회, `IsRecruitmentExpiryEnabledUseCase`)로 관리한다 —
 * `@ConfigurationProperties`는 부팅 시 고정 바인딩이라 값을 바꿔도 재기동 전에는 반영되지
 * 않으므로(no-conditional-on-property 취지), "플래그 OFF로 즉시 비활성화"가 성립하지 않는다.
 * 여기 남은 값(ttlMinutes/readyTtlMinutes/chunkSize/maxChunksPerRun/cycleMs)은 순수 튜닝
 * 값이라 재기동 전제가 무방하다.
 *
 * **두 TTL** — payment 상태만으로 "결제가 시작이라도 됐는가"(live)를 판정하고, live 여부에
 * 따라 서로 다른 TTL을 적용한다([com.sportsapp.domain.payment.service.PaymentLivenessClassifier]
 * 참고).
 * - [ttlMinutes](빠른 TTL): 결제 미시작/실패/취소/환불 등 "결제가 시작 못 했거나 종결된"
 *   신청을 취소시키는 기준. F-A(고립 신청)를 정확히 타격한다.
 * - [readyTtlMinutes](느린 TTL): live(READY 이상) 결제가 있는 신청을 취소시키는 기준. 결제창에
 *   머무는 사용자를 오취소하지 않도록 충분히 길게 잡는다.
 *
 * **불변조건**: `readyTtlMinutes`는 반드시 `ttlMinutes`보다 커야 한다 — 같거나 짧으면 live
 * 신청도 빠른 TTL에 걸려 오취소된다. env 재정의로도 이 조건이 깨지면 부팅을 실패시켜
 * (`require`) 가드 무력화를 재기동 즉시 드러낸다(운영 설정 실수의 조용한 재발 방지).
 */
@ConfigurationProperties(prefix = "recruitment.expiry")
data class RecruitmentApplicationExpiryProperties(
    val ttlMinutes: Long = 30,
    val readyTtlMinutes: Long = 90,
    val chunkSize: Int = 100,
    val maxChunksPerRun: Int = 20,
    val cycleMs: Long = DEFAULT_CYCLE_MS,
) {
    init {
        require(readyTtlMinutes > ttlMinutes) {
            "readyTtlMinutes($readyTtlMinutes)는 ttlMinutes($ttlMinutes)보다 커야 한다 — " +
                "그렇지 않으면 결제 진행 중(live)인 신청이 빠른 TTL에 의해 오취소된다"
        }
    }

    companion object {
        /**
         * 스케줄 주기(ms) 기본값 SSOT — [com.sportsapp.presentation.recruitment.scheduler.ApplicationExpiryScheduler]의
         * `@Scheduled(fixedDelayString)` 애노테이션이 컴파일 타임 상수로 이 값을 문자열 보간해 참조한다.
         */
        const val DEFAULT_CYCLE_MS = 300_000L
    }
}
