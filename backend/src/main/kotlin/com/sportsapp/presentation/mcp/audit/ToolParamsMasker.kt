package com.sportsapp.presentation.mcp.audit

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.presentation.mcp.pii.PiiMasker

/**
 * MCP tool 파라미터 마스킹 유틸리티.
 *
 * 이름이 부여된 파라미터 맵(key=파라미터명, value=값)을 JSON 문자열로 직렬화하고,
 * PII 패턴 key(name/phone/email 등)에 해당하는 값을 PiiMasker 로 마스킹합니다.
 *
 * 위치: presentation layer (presentation/mcp/audit)
 */
object ToolParamsMasker {

    private val piiKeyPatterns = listOf(
        Regex("(?i).*(name).*"),
        Regex("(?i).*(phone|mobile|tel).*"),
        Regex("(?i).*(email|mail).*"),
        Regex("(?i).*(address|addr).*"),
        Regex("(?i).*(birth|dob).*"),
        Regex("(?i).*(card.*number|cardno).*"),
        Regex("(?i).*(account.*number|accountno).*"),
    )

    fun mask(namedParams: Map<String, Any?>, objectMapper: ObjectMapper): String {
        if (namedParams.isEmpty()) return "{}"
        val masked = namedParams.entries.associate { (key, value) ->
            key to maskValue(key, value)
        }
        return try {
            objectMapper.writeValueAsString(masked)
        } catch (exception: Exception) {
            "{}"
        }
    }

    private fun maskValue(key: String, value: Any?): Any? {
        val stringValue = value as? String
        return when {
            value == null -> null
            stringValue == null -> value
            piiKeyPatterns.any { it.matches(key) } -> applyPiiMask(key, stringValue)
            else -> value
        }
    }

    private fun applyPiiMask(key: String, value: String): String? =
        when {
            key.contains("phone", ignoreCase = true) || key.contains("mobile", ignoreCase = true) ->
                PiiMasker.mobilePhone(value) ?: PiiMasker.landlinePhone(value)
            key.contains("email", ignoreCase = true) || key.contains("mail", ignoreCase = true) ->
                PiiMasker.email(value)
            key.contains("name", ignoreCase = true) ->
                PiiMasker.name(value)
            key.contains("address", ignoreCase = true) || key.contains("addr", ignoreCase = true) ->
                PiiMasker.address(value)
            key.contains("card", ignoreCase = true) ->
                PiiMasker.cardNumber(value)
            key.contains("account", ignoreCase = true) ->
                PiiMasker.accountNumber(value)
            else -> PiiMasker.REDACTED
        }
}
