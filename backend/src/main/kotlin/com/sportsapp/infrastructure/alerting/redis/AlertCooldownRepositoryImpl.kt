package com.sportsapp.infrastructure.alerting.redis

import com.sportsapp.domain.alerting.repository.AlertCooldownRepository
import com.sportsapp.domain.alerting.vo.AlertSignal
import java.time.Duration
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * 신호 단위 쿨다운(ADR-003, INFRA-01 계약) Redis `SET NX PX` 구현체.
 *
 * `RedisDistributedLock.tryLock`과 동일한 원자성 패턴(`opsForValue().setIfAbsent`)을 재사용하되,
 * 락과 달리 해제(`DEL`)는 하지 않는다 — TTL 자연 만료 자체가 "15분 경과"라는 비즈니스 의미이므로
 * 조기 해제는 설계 위반이다(INFRA-01 계약 문서 §무효화·상태 흐름).
 *
 * `env`는 더 이상 이 구현체가 `app.env`로 직접 주입받지 않는다 — 호출부([tryAcquire])가 전달한 값을
 * 그대로 키 조립에 사용한다(env 출처 통일, `AlertDomainService.raise`가 `RaiseAlertCommand.env`를 전달).
 */
@Component
class AlertCooldownRepositoryImpl(
    private val stringRedisTemplate: StringRedisTemplate,
) : AlertCooldownRepository {

    override fun tryAcquire(signal: AlertSignal, env: String, cooldown: Duration): Boolean =
        stringRedisTemplate.opsForValue().setIfAbsent(signal.cooldownKey(env), COOLDOWN_MARKER, cooldown) == true

    companion object {
        private const val COOLDOWN_MARKER = "1"
    }
}
