package com.sportsapp.contract

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * 와이어 계약의 한 필드 (W1-10).
 *
 * [type] 은 실제 직렬화 결과의 JSON 노드 종류다 — 코틀린 타입이 아니라 **상대가 실제로 받는 것**을
 * 고정해야 계약이다. [nullable] 은 코틀린 프로퍼티의 nullability 로, "optional → 필수화" 같은
 * 파괴적 변경(§9-2)을 판정하는 기준이다.
 */
data class ContractField(val type: String, val nullable: Boolean)

/** 한 payload 변이의 계약 — 필드 이름 → 계약. */
typealias ContractFields = Map<String, ContractField>

/**
 * 실제 인스턴스를 프로덕션 ObjectMapper 로 직렬화해 와이어 계약을 추출한다 (W1-10).
 *
 * 리플렉션만으로 스키마를 만들지 않는 이유: 이 레포에는 **payload 역직렬화 정합이 깨져 결제
 * 완료 → 주문 확정이 장기간 유실된 사고 이력**이 있다. 그 사고의 실패면은 "코틀린 타입"이 아니라
 * "직렬화된 JSON"이었다. 그래서 계약의 근거를 실제 직렬화 결과로 잡는다.
 */
object ContractShape {

    fun of(instance: Any, objectMapper: ObjectMapper): ContractFields {
        val serialized = objectMapper.readTree(objectMapper.writeValueAsString(instance))
        val nullabilityByProperty = nullabilityOf(instance)
        return serialized.fieldNames().asSequence().associateWith { fieldName ->
            ContractField(
                type = jsonTypeOf(serialized.get(fieldName)),
                nullable = nullabilityByProperty[fieldName] ?: serialized.get(fieldName).isNull,
            )
        }
    }

    private fun nullabilityOf(instance: Any): Map<String, Boolean> =
        instance::class.memberProperties.associate { property ->
            property.isAccessible = true
            property.name to property.returnType.isMarkedNullable
        }

    private fun jsonTypeOf(node: JsonNode): String = when {
        node.isTextual -> "STRING"
        node.isIntegralNumber -> "INTEGER"
        node.isNumber -> "NUMBER"
        node.isBoolean -> "BOOLEAN"
        node.isArray -> "ARRAY"
        node.isObject -> "OBJECT"
        node.isNull -> "NULL"
        else -> "UNKNOWN"
    }
}
