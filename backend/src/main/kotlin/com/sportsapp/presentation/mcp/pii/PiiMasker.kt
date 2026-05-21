package com.sportsapp.presentation.mcp.pii

import java.time.Period
import java.time.ZonedDateTime

/**
 * B2B MCP 응답에 포함되는 B2C 사용자 PII 마스킹 룰.
 *
 * Gate #C 정책 §2 마스킹 룰 적용. MVP Phase 1은 모든 PII를 항상 마스킹합니다
 * (pii:unmask scope 발급 보류 — TDD ADR-010). Phase 2에서 자격 검증 통과
 * 운영자에 한해 평문 노출 가능.
 *
 * 호출 위치: presentation/mcp/toolregistry/ 하위 Tools 파일에서 tool 응답 매핑 시 명시적 호출.
 * 도메인 Entity 또는 UseCase Response 에는 마스킹 적용하지 않음 (DB 원본 보존).
 */
object PiiMasker {

    /**
     * 본명 마스킹 — 첫 글자만 노출.
     * 예: "김민수" -> "김**", "John Smith" -> "J*** S****"
     * 공백이 있으면 각 토큰 첫 글자만 유지.
     */
    fun name(value: String?): String? {
        if (value.isNullOrBlank()) return value
        return value.split(" ").joinToString(" ") { token ->
            if (token.isEmpty()) token
            else token.first() + "*".repeat((token.length - 1).coerceAtLeast(1))
        }
    }

    /**
     * 휴대폰 번호 마스킹 — 010-****-{뒤4}.
     * 예: "010-1234-5678" -> "010-****-5678"
     * "01012345678" -> "010-****-5678"
     * 형식 인식 실패 시 전체 마스킹.
     */
    fun mobilePhone(value: String?): String? {
        if (value.isNullOrBlank()) return value
        val digits = value.filter { it.isDigit() }
        return if (digits.length == 11 && digits.startsWith("010")) {
            "010-****-${digits.takeLast(4)}"
        } else {
            REDACTED
        }
    }

    /**
     * 일반 전화 마스킹 — 0**-***-{뒤4}.
     * 예: "02-123-4567" -> "0**-***-4567"
     */
    fun landlinePhone(value: String?): String? {
        if (value.isNullOrBlank()) return value
        val digits = value.filter { it.isDigit() }
        return if (digits.length in 9..10) {
            "0**-***-${digits.takeLast(4)}"
        } else {
            REDACTED
        }
    }

    /**
     * 이메일 마스킹 — 로컬 첫 글자 + ***@***.{TLD}.
     * 예: "john@gmail.com" -> "j***@***.com"
     */
    fun email(value: String?): String? {
        if (value.isNullOrBlank()) return value
        val atIdx = value.indexOf('@')
        val masked = if (atIdx <= 0 || atIdx == value.length - 1) {
            REDACTED
        } else {
            val local = value.substring(0, atIdx)
            val tld = value.substring(atIdx + 1).substringAfterLast('.', missingDelimiterValue = "")
            "${local.first()}***@***${if (tld.isNotEmpty()) ".$tld" else ""}"
        }
        return masked
    }

    /**
     * 주소 마스킹 — 시·군·구까지만 보존.
     * 예: "서울 강남구 역삼동 123-45" -> "서울 강남구"
     * 두 번째 공백 이전까지만 유지 (구/시 단위).
     */
    fun address(value: String?): String? {
        if (value.isNullOrBlank()) return value
        val tokens = value.split(" ").filter { it.isNotBlank() }
        return tokens.take(2).joinToString(" ")
    }

    /**
     * 생년월일 마스킹 — 만 나이 범위로 변환.
     * 예: 1990-03-15 (ZonedDateTime) -> "30대 후반" (계산 기준 시점은 호출 시점 ZonedDateTime.now())
     * 호출자는 LocalDate를 동일 ZoneId 의 atStartOfDay 로 변환해서 전달합니다.
     */
    fun birthdate(value: ZonedDateTime?, now: ZonedDateTime = ZonedDateTime.now()): String? {
        if (value == null) return null
        val age = Period.between(value.toLocalDate(), now.toLocalDate()).years
        val decade = (age / 10) * 10
        val band = when (age % 10) {
            in 0..3 -> "초반"
            in 4..6 -> "중반"
            else -> "후반"
        }
        return "${decade}대 $band"
    }

    /**
     * 결제 카드 마스킹 — **** **** **** {뒤4}.
     * 입력 자릿수가 다른 경우(15자리 AMEX 등) 그룹 수 동일하게 유지.
     */
    fun cardNumber(value: String?): String? {
        if (value.isNullOrBlank()) return value
        val digits = value.filter { it.isDigit() }
        return if (digits.length >= 12) {
            "**** **** **** ${digits.takeLast(4)}"
        } else {
            REDACTED
        }
    }

    /**
     * 계좌 번호 마스킹 — ***-***-{뒤4}.
     */
    fun accountNumber(value: String?): String? {
        if (value.isNullOrBlank()) return value
        val digits = value.filter { it.isDigit() }
        return if (digits.length >= 6) {
            "***-***-${digits.takeLast(4)}"
        } else {
            REDACTED
        }
    }

    const val REDACTED = "[REDACTED]"
}
