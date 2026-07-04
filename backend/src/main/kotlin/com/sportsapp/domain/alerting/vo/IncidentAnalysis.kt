package com.sportsapp.domain.alerting.vo

/**
 * LLM 원인분석 산출물 (TDD.md §Terminology "IncidentAnalysis").
 * [included]가 false면 [fallback] — LLM 실패로 원인분석 없이 원지표만 담아 발송한다(FR-8).
 */
data class IncidentAnalysis(
    val errorType: String,
    val causeEstimation: String,
    val remediation: String,
    val included: Boolean,
) {
    companion object {
        private const val FALLBACK_ERROR_TYPE = "UNKNOWN"
        private const val FALLBACK_CAUSE_ESTIMATION = "LLM 원인분석에 실패해 원인을 추정하지 못했습니다."
        private const val FALLBACK_REMEDIATION = "원지표를 참고해 수동으로 원인을 확인하세요."

        /** LLM 실패 시 대체 분석 — 원인분석 없이 원지표만 담아 발송한다(FR-8). */
        fun fallback(): IncidentAnalysis = IncidentAnalysis(
            errorType = FALLBACK_ERROR_TYPE,
            causeEstimation = FALLBACK_CAUSE_ESTIMATION,
            remediation = FALLBACK_REMEDIATION,
            included = false,
        )
    }
}
