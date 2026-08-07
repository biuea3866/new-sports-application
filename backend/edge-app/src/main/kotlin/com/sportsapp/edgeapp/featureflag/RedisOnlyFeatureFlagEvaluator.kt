package com.sportsapp.edgeapp.featureflag

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.common.FeatureFlagEvaluator
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * edge 전용 피처 플래그 평가기 — **Redis 만 읽는다** (S2-12).
 *
 * 공용 계약([FeatureFlagEvaluator])의 유일한 구현은 platform 소유이고, 그 체인이
 * `@PostConstruct` 에서 MySQL 을 전량 조회한다. edge 는 DataSource 자체가 없고 `svc_edge` 는 DB
 * 권한이 0 이라(§3-1) 그 구현을 쓰면 **부팅 단계에서 실패**한다. 그래서 읽기 전용 구현을 따로 둔다.
 *
 * **쓰기·무효화는 하지 않는다.** platform 이 MySQL 에 쓰고 Redis 스냅샷을 갱신하며, edge 는 소비자다.
 *
 * ### 두 실패 상태를 구분한다
 *
 * | 스냅샷 상태 | 판정 | 이유 |
 * |---|---|---|
 * | 키 없음 (미생성·Redis 장애·깨진 JSON) | 호출부 `default` | 플래그 미생성이 **현재 정상 상태**다. 대기열 플래그의 default 가 안전 방향(우회)이라 폴백이 성립한다 |
 * | `status=ARCHIVED` | 호출부 `default` | platform 규칙과 동일 |
 * | `GLOBAL_TOGGLE` | `enabled` 값 | edge 가 해석하는 유일한 전략 |
 * | 그 외 전략 | **OFF (fail-closed)** | 정책 위반이 실제로 발생한 상황이다. 켜진 채 잘못 평가되는 것보다 꺼지는 편이 안전하고, 경고 로그로 즉시 드러난다 |
 *
 * 둘을 같게 처리하면 정책 위반이 "정상 기본값"에 묻힌다.
 *
 * **트레이드오프**: `virtualqueue.admission.enabled` 의 호출부 default 는 `true`(펌프 실행)라, 그
 * 플래그가 비-GlobalToggle 로 생성되면 fail-closed 로 **펌프가 멈춘다.** "virtualqueue 계열은
 * GlobalToggle 로만 생성한다"는 정책이 지켜지는 한 발생하지 않는다(후속 리스크 R-19).
 *
 * 전략 타입 자체를 해석하지 못하는 이유는 그 sealed 타입이 platform domain 에 있고 edge 가
 * platform 을 의존할 수 없기 때문이다(W1-06b 가 끊어 둔 결합).
 */
@Component
class RedisOnlyFeatureFlagEvaluator(
    private val redisTemplate: StringRedisTemplate,
) : FeatureFlagEvaluator {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 인스턴스 로컬 L1 캐시.
     *
     * platform 의 `LocalFeatureFlagStore` 가 30초 주기로 Redis 를 재기록하므로 키는 계속 살아 있고,
     * 3초 TTL 이면 플래그 변경이 사실상 즉시 반영된다. pub/sub(`featureflag:changes`) 구독은
     * 채택하지 않았다 — 3초 수렴으로 충분하고 구독 커넥션 관리 비용이 더 크다.
     */
    private val localCache = ConcurrentHashMap<String, CachedDecision>()

    override fun isEnabled(key: String, context: FeatureContext, default: Boolean): Boolean =
        when (val decision = decisionFor(key)) {
            Decision.UseCallerDefault -> default
            is Decision.Fixed -> decision.enabled
        }

    /**
     * edge 는 변형(variant)을 해석하지 않는다 — `GlobalToggle` 만 지원하기 때문이다.
     * 변형이 필요한 플래그가 생기면 전략 타입을 `common` 으로 승격해야 한다(R-19).
     */
    override fun variant(key: String, context: FeatureContext, default: String): String = default

    private fun decisionFor(key: String): Decision {
        val cached = localCache[key]
        if (cached != null && !cached.isExpired()) return cached.decision

        val decision = readSnapshot(key)
        localCache[key] = CachedDecision(decision, expiresAtMillis = nowMillis() + LOCAL_CACHE_TTL_SECONDS * 1_000)
        return decision
    }

    private fun readSnapshot(key: String): Decision =
        fetchSnapshot(key)?.let { decisionOf(key, it) } ?: Decision.UseCallerDefault

    /** Redis 조회·파싱. 어느 단계가 실패해도 null 이며 호출부가 "호출부 기본값"으로 해석한다. */
    private fun fetchSnapshot(key: String): JsonNode? =
        runCatching { redisTemplate.opsForValue().get(cacheKeyOf(key)) }
            .onFailure { logger.warn("피처 플래그 스냅샷 조회 실패 — 호출부 기본값으로 평가한다 (key={})", key, it) }
            .getOrNull()
            ?.let { raw ->
                runCatching { objectMapper.readTree(raw) }
                    .onFailure { logger.warn("피처 플래그 스냅샷 파싱 실패 — 호출부 기본값으로 평가한다 (key={})", key, it) }
                    .getOrNull()
            }

    /** 스냅샷이 있을 때의 판정 — ARCHIVED·미지원 전략을 여기서 가른다. */
    private fun decisionOf(key: String, snapshot: JsonNode): Decision {
        val strategy = snapshot.path("strategy")
        val strategyType = strategy.path("strategyType").asText()
        return when {
            snapshot.path("status").asText() == ARCHIVED_STATUS -> Decision.UseCallerDefault
            strategyType == GLOBAL_TOGGLE_STRATEGY -> Decision.Fixed(strategy.path("enabled").asBoolean(false))
            else -> {
                logger.warn(
                    "edge 가 해석하지 않는 전략이라 OFF 로 판정한다 (key={}, strategyType={}) — " +
                        "virtualqueue 계열 플래그는 GLOBAL_TOGGLE 로만 생성해야 한다",
                    key,
                    strategyType,
                )
                Decision.Fixed(enabled = false)
            }
        }
    }

    private fun cacheKeyOf(flagKey: String) = "$CACHE_KEY_PREFIX$flagKey"

    private fun nowMillis(): Long = System.currentTimeMillis()

    /** 스냅샷 해석 결과 — "호출부 기본값을 쓴다"와 "값이 정해졌다"를 타입으로 구분한다. */
    private sealed interface Decision {
        data object UseCallerDefault : Decision
        data class Fixed(val enabled: Boolean) : Decision
    }

    private inner class CachedDecision(val decision: Decision, private val expiresAtMillis: Long) {
        fun isExpired(): Boolean = nowMillis() >= expiresAtMillis
    }

    companion object {
        /** L1 캐시 수명(초). platform 의 재기록 주기(30초)보다 훨씬 짧아야 변경이 즉시 반영된다. */
        const val LOCAL_CACHE_TTL_SECONDS = 3L

        /** `FeatureFlagRedisKeys`(platform 소유)와 같은 값 — edge 는 그 모듈을 의존할 수 없어 리터럴로 둔다. */
        private const val CACHE_KEY_PREFIX = "featureflag:flag:"
        private const val GLOBAL_TOGGLE_STRATEGY = "GLOBAL_TOGGLE"
        private const val ARCHIVED_STATUS = "ARCHIVED"

        private val objectMapper: ObjectMapper = ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }
}
