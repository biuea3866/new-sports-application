package com.sportsapp.contract

/**
 * 계약 하위 호환 판정 (W1-10 / 실행설계 §9-2).
 *
 * §3-2 금지 ⑥은 **"두 서비스를 동시에 배포해야 동작하는 변경"** 을 금지한다. 그 변경이 정확히
 * 아래 위반 3종이다 — 공급자만 먼저 배포하면 구 소비자가 깨지고, 소비자만 먼저 배포하면 신 필드를
 * 못 받는다. 필드 **추가(optional)** 만 한쪽 배포로 안전하다.
 *
 * 파괴적 변경이 정말 필요하면 새 버전 경로(`/v2/...`)·새 토픽(`.v{N+1}`)으로 유도한다 — 같은
 * 계약을 제자리에서 깨는 것이 금지 대상이다.
 */
object ContractCompatibility {

    /** 어떤 계약이 어떻게 깨졌는지 — 실패 메시지에 그대로 실린다. */
    data class Violation(val field: String, val reason: String)

    /**
     * [baseline](커밋된 스냅샷) 대비 [current](현재 코드) 의 파괴적 변경을 모두 찾는다.
     * 빈 목록이면 하위 호환이다.
     */
    fun violationsOf(baseline: ContractFields, current: ContractFields): List<Violation> {
        val removed = baseline.keys.filterNot { it in current.keys }
            .map { Violation(it, "필드 제거 — 구 소비자가 읽던 필드가 사라진다") }

        val changed = baseline.entries.mapNotNull { (fieldName, baselineField) ->
            val currentField = current[fieldName] ?: return@mapNotNull null
            when {
                currentField.type != baselineField.type ->
                    Violation(fieldName, "타입 변경 ${baselineField.type} → ${currentField.type}")
                baselineField.nullable && !currentField.nullable ->
                    Violation(fieldName, "optional → 필수화 — 구 소비자가 보내던 null 이 거부된다")
                else -> null
            }
        }

        val addedRequired = current.entries
            .filterNot { it.key in baseline.keys }
            .filterNot { it.value.nullable }
            .map {
                Violation(
                    it.key,
                    "필수 필드 추가 — 추가는 optional 만 허용된다(§9-2). " +
                        "값이 반드시 필요하면 새 버전 경로/토픽으로 유도한다",
                )
            }

        return removed + changed + addedRequired
    }
}
