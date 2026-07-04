package com.sportsapp.infrastructure.alerting.redis

import com.sportsapp.domain.alerting.repository.AlertCooldownRepository
import com.sportsapp.domain.alerting.vo.AlertSignal
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * 신호 단위 쿨다운(ADR-003, INFRA-01 계약) Redis `SET NX PX` 구현체.
 *
 * `RedisDistributedLock.tryLock`과 동일한 원자성 패턴(`opsForValue().setIfAbsent`)을 재사용하되,
 * 락과 달리 해제(`DEL`)는 하지 않는다 — TTL 자연 만료 자체가 "15분 경과"라는 비즈니스 의미이므로
 * 조기 해제는 설계 위반이다(INFRA-01 계약 문서 §무효화·상태 흐름).
 */
@Component
class AlertCooldownRepositoryImpl(
    private val stringRedisTemplate: StringRedisTemplate,
    @Value("\${app.env:local}") private val env: String,
) : AlertCooldownRepository {

    override fun tryAcquire(signal: AlertSignal, cooldown: Duration): Boolean =
        stringRedisTemplate.opsForValue().setIfAbsent(signal.cooldownKey(env), COOLDOWN_MARKER, cooldown) == true

    companion object {
        private const val COOLDOWN_MARKER = "1"
    }
}
